package dev.infotrans.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reader overlay that shows the translated book page by page, with a toggle
 * back to the original text. Page chunks appear progressively as the free
 * model answers.
 */
public class TranslatedBookScreen extends Screen {
    private static final int PAGE_WIDTH = 114;
    private static final int TEXT_COLOR = 0xFFE8E8E8;
    private static final int ERROR_COLOR = 0xFFFF7070;

    private final Screen parent;
    private final List<String> originalPages;
    private final Map<Integer, String> translatedPages = new ConcurrentHashMap<>();
    private final Map<Integer, String> errors = new ConcurrentHashMap<>();

    private int page;
    private boolean showTranslated = true;

    public TranslatedBookScreen(Screen parent, List<String> originalPages) {
        super(Component.literal("Information Translation"));
        this.parent = parent;
        this.originalPages = originalPages;
    }

    public void setPages(int startIndex, List<String> translated) {
        for (int i = 0; i < translated.size(); i++) {
            translatedPages.put(startIndex + i, translated.get(i));
        }
        errors.keySet().removeIf(k -> k >= startIndex && k < startIndex + translated.size());
    }

    public void setError(int pageIndex, String error) {
        errors.put(pageIndex, error);
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        this.addRenderableWidget(Button.builder(Component.literal("◀"), b -> turnPage(-1))
                .bounds(cx - 100, this.height - 28, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("▶"), b -> turnPage(1))
                .bounds(cx + 80, this.height - 28, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("原文/译文"), b -> {
                    showTranslated = !showTranslated;
                }).bounds(cx - 76, this.height - 28, 70, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
                .bounds(cx + 6, this.height - 28, 70, 20).build());
    }

    private void turnPage(int delta) {
        int next = page + delta;
        if (next >= 0 && next < originalPages.size()) {
            page = next;
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractBackground(graphics, mouseX, mouseY, partialTick);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.centeredText(this.font,
                Component.literal("第 " + (page + 1) + " / " + originalPages.size() + " 页 · " +
                        (showTranslated ? "译文" : "原文")),
                this.width / 2, 10, 0xFF909090);

        String original = originalPages.get(page);
        String content;
        int color = TEXT_COLOR;
        if (!showTranslated) {
            content = original;
            color = 0xFFB0B0B0;
        } else {
            String error = errors.get(page);
            String translated = translatedPages.get(page);
            if (translated != null) {
                content = translated;
            } else if (error != null) {
                content = "翻译失败: " + error;
                color = ERROR_COLOR;
            } else {
                content = "…翻译中,请稍候…";
                color = 0xFF909090;
            }
        }

        int x = this.width / 2 - PAGE_WIDTH / 2;
        int y = 34;
        for (FormattedCharSequence line : this.font.split(FormattedText.of(content), PAGE_WIDTH)) {
            if (y > this.height - 40) {
                break;
            }
            graphics.text(this.font, line, x, y, color);
            y += 9;
        }
    }
}
