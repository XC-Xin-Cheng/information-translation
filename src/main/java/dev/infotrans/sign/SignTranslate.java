package dev.infotrans.sign;

import dev.infotrans.config.ModConfig;
import dev.infotrans.translate.TranslationService;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Content-addressed sign translation. The renderer mixin calls {@link #apply}
 * every frame; until a translation is ready the vanilla text is used, then the
 * cached translated lines are swapped in. All 4 lines of a sign are translated
 * in a single request together with other recently seen signs.
 */
public final class SignTranslate {
    private SignTranslate() {
    }

    private static final int LINES = 4;

    private record Job(String key, List<String> lines, Function<Component, FormattedCharSequence> transformer) {
    }

    private static final Map<String, FormattedCharSequence[]> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, FormattedCharSequence[]> eldest) {
                    return size() > 2048;
                }
            });

    private static final List<Job> QUEUE = new ArrayList<>();
    private static int tickCounter = 0;

    /** called from SignTextMixin every frame; returns the translated render lines or null */
    public static FormattedCharSequence[] apply(Object textObj, boolean filteringEnabled,
                                                Function<Component, FormattedCharSequence> transformer) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.enabled || !cfg.translateSigns) {
            return null;
        }
        if (!(textObj instanceof net.minecraft.world.level.block.entity.SignText text)) {
            return null;
        }

        List<String> lines = new ArrayList<>(LINES);
        boolean any = false;
        for (int i = 0; i < LINES; i++) {
            String s = text.getMessage(i, filteringEnabled).getString();
            if (s != null && !s.isBlank()) {
                any = true;
            }
            lines.add(s == null ? "" : s);
        }
        if (!any) {
            return null;
        }
        String key = String.join("\u0000", lines);

        FormattedCharSequence[] cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        enqueueLocked(new Job(key, lines, transformer));
        return null;
    }

    private static void enqueueLocked(Job job) {
        synchronized (QUEUE) {
            for (Job j : QUEUE) {
                if (j.key().equals(job.key())) {
                    return;
                }
            }
            QUEUE.add(job);
        }
    }

    public static void tick() {
        List<Job> todo;
        synchronized (QUEUE) {
            if (QUEUE.isEmpty()) {
                return;
            }
            if (QUEUE.size() < 2 && ++tickCounter < 20) {
                return;
            }
            tickCounter = 0;
            todo = new ArrayList<>(QUEUE);
            QUEUE.clear();
        }

        List<String> flat = new ArrayList<>();
        List<int[]> index = new ArrayList<>(); // {jobIdx, lineIdx}
        for (int j = 0; j < todo.size(); j++) {
            List<String> lines = todo.get(j).lines();
            for (int l = 0; l < LINES; l++) {
                if (!lines.get(l).isBlank()) {
                    index.add(new int[]{j, l});
                    flat.add(lines.get(l));
                }
            }
        }
        if (flat.isEmpty()) {
            return;
        }

        TranslationService.translate(flat, result -> {
            if (result.error() != null) {
                return; // keep vanilla text, retry when the sign is seen again later
            }
            List<String> translated = result.translated();
            for (int j = 0; j < todo.size(); j++) {
                Job job = todo.get(j);
                List<String> lines = job.lines();
                FormattedCharSequence[] arr = new FormattedCharSequence[LINES];
                for (int l = 0; l < LINES; l++) {
                    if (lines.get(l).isBlank()) {
                        arr[l] = FormattedCharSequence.EMPTY;
                        continue;
                    }
                    String value = null;
                    for (int k = 0; k < index.size() && k < translated.size(); k++) {
                        int[] at = index.get(k);
                        if (at[0] == j && at[1] == l) {
                            value = translated.get(k);
                            break;
                        }
                    }
                    if (value == null || value.isBlank()) {
                        value = lines.get(l); // fall back to the original line
                    }
                    arr[l] = job.transformer().apply(Component.literal(value));
                }
                synchronized (CACHE) {
                    CACHE.put(job.key(), arr);
                }
            }
        });
    }
}
