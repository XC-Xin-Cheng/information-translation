package dev.infotrans.chat;

import dev.infotrans.config.ModConfig;
import dev.infotrans.translate.TranslationService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Collects incoming chat messages and translates them in BATCHES (several
 * messages share one request, so short lines keep their context and the
 * free-model rate limit is respected). The translated copy is posted under
 * the original as a gray italic line.
 */
public final class ChatTranslate {
    private ChatTranslate() {
    }

    /** set while the mod itself is posting a chat line, so we never translate our own output */
    public static volatile boolean INJECTING = false;

    private static final ArrayDeque<String> PENDING = new ArrayDeque<>();
    private static final Map<Long, Long> RECENT = new HashMap<>();
    private static int tickCounter = 0;

    private static final String PREFIX = "↳ ";

    public static void onMessage(Component message) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.enabled || !cfg.translateChat || INJECTING) {
            return;
        }
        String text = message.getString();
        if (text == null || text.isBlank() || text.length() > 2000) {
            return;
        }
        if (text.startsWith(PREFIX)) {
            return;
        }
        long hash = text.hashCode() * 31L + text.length();
        long now = System.currentTimeMillis();
        Long seenAt = RECENT.get(hash);
        if (seenAt != null && now - seenAt < 10_000) {
            return;
        }
        RECENT.put(hash, now);
        if (RECENT.size() > 512) {
            Iterator<Map.Entry<Long, Long>> it = RECENT.entrySet().iterator();
            while (it.hasNext() && RECENT.size() > 256) {
                Map.Entry<Long, Long> e = it.next();
                if (now - e.getValue() > 10_000) {
                    it.remove();
                }
            }
        }

        PENDING.add(text);
        if (PENDING.size() >= Math.max(2, cfg.maxBatchLines)) {
            flush();
        }
    }

    public static void tick() {
        if (PENDING.isEmpty()) {
            return;
        }
        if (++tickCounter >= Math.max(5, ModConfig.get().chatBatchDelayTicks)) {
            flush();
        }
    }

    private static void flush() {
        tickCounter = 0;
        ModConfig cfg = ModConfig.get();
        int batch = Math.max(2, cfg.maxBatchLines);
        while (!PENDING.isEmpty()) {
            List<String> chunk = new ArrayList<>(Math.min(batch, PENDING.size()));
            while (chunk.size() < batch && !PENDING.isEmpty()) {
                chunk.add(PENDING.poll());
            }
            submitChunk(chunk);
        }
    }

    private static void submitChunk(List<String> chunk) {
        TranslationService.translate(chunk, result -> {
            Minecraft mc = Minecraft.getInstance();
            if (result.error() != null) {
                postLine(mc, Component.literal("§c[Information Translation] 翻译失败: " + result.error()));
                return;
            }
            List<String> translated = result.translated();
            for (int i = 0; i < translated.size() && i < chunk.size(); i++) {
                String t = translated.get(i);
                if (t == null || t.isBlank()) {
                    continue;
                }
                postTranslated(mc, chunk.get(i), t);
            }
        });
    }

    private static void postTranslated(Minecraft mc, String original, String translated) {
        if (translated.strip().equals(original.strip())) {
            return; // nothing changed, likely already in the target language
        }
        MutableComponent line = Component.literal(PREFIX + translated)
                .setStyle(Style.EMPTY.withItalic(true).withColor(ChatFormatting.GRAY));
        postLine(mc, line);
    }

    private static void postLine(Minecraft mc, Component line) {
        mc.execute(() -> {
            INJECTING = true;
            try {
                mc.gui.getChat().addClientSystemMessage(line);
            } finally {
                INJECTING = false;
            }
        });
    }
}
