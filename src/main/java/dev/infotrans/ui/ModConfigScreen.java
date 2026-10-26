package dev.infotrans.ui;

import dev.infotrans.config.ModConfig;
import dev.infotrans.translate.TranslationService;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Settings screen (openable from Mod Menu or the O key).
 */
public class ModConfigScreen extends Screen {
    private static final String[] PROVIDER_IDS = {"pollinations", "openai", "google"};
    private static final String[] PROVIDER_LABELS = {"Pollinations(免费模型,无需密钥)", "自定义 OpenAI 兼容接口", "Google 翻译(备用)"};

    private static final int LABEL_COLOR = 0xFFA0A0A0;
    private static final int W = 150;

    private final Screen parent;

    private EditBox langBox;
    private CycleButton<String> presetButton;
    private CycleButton<String> providerButton;
    private EditBox apiBaseBox;
    private EditBox modelBox;
    private EditBox apiKeyBox;
    private CycleButton<Boolean> enabledButton;
    private CycleButton<Boolean> chatButton;
    private CycleButton<Boolean> booksButton;
    private CycleButton<Boolean> signsButton;

    private volatile String testStatus = "默认按键:O 打开本设置;Mod Menu 中也可点击设置按钮。";

    public ModConfigScreen(Screen parent) {
        super(Component.literal("Information Translation 设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModConfig cfg = ModConfig.get();
        int cx = this.width / 2;
        int left = cx - 155;
        int right = cx + 5;

        langBox = new EditBox(this.font, left, 35, W, 16, Component.literal("目标语言"));
        langBox.setValue(cfg.targetLanguage);
        langBox.setMaxLength(64);
        this.addRenderableWidget(langBox);

        presetButton = CycleButton.builder((String s) -> Component.literal(s), cfg.targetLanguage)
                .withValues(ModConfig.LANGUAGE_PRESETS)
                .create(right, 33, W, 20, Component.literal("预设"),
                        (btn, val) -> langBox.setValue(val));
        this.addRenderableWidget(presetButton);

        String currentProvider = providerIndex(cfg.provider) >= 0 ? cfg.provider : "pollinations";
        providerButton = CycleButton.builder(
                        (String id) -> Component.literal(PROVIDER_LABELS[providerIndex(id)]), currentProvider)
                .withValues(PROVIDER_IDS)
                .create(left, 59, W, 20, Component.literal("翻译源"), (btn, val) -> {
                });
        this.addRenderableWidget(providerButton);

        this.addRenderableWidget(Button.builder(Component.literal("测试翻译"), b -> runTest())
                .bounds(right, 59, W, 20).build());

        apiBaseBox = new EditBox(this.font, left, 97, 310, 16, Component.literal("API 地址"));
        apiBaseBox.setValue(cfg.apiBase);
        apiBaseBox.setMaxLength(256);
        this.addRenderableWidget(apiBaseBox);

        modelBox = new EditBox(this.font, left, 133, 310, 16, Component.literal("模型"));
        modelBox.setValue(cfg.model);
        modelBox.setMaxLength(128);
        this.addRenderableWidget(modelBox);

        apiKeyBox = new EditBox(this.font, left, 169, 310, 16, Component.literal("API 密钥"));
        apiKeyBox.setValue(cfg.apiKey);
        apiKeyBox.setMaxLength(256);
        this.addRenderableWidget(apiKeyBox);

        enabledButton = toggle(cfg.enabled, "总开关", left, 195);
        chatButton = toggle(cfg.translateChat, "翻译聊天", right, 195);
        booksButton = toggle(cfg.translateBooks, "翻译书本", left, 217);
        signsButton = toggle(cfg.translateSigns, "翻译告示牌", right, 217);

        this.addRenderableWidget(Button.builder(Component.literal("完成"), b -> {
                    saveAndClose();
                }).bounds(cx - 100, this.height - 26, 200, 20).build());
    }

    private CycleButton<Boolean> toggle(boolean value, String label, int x, int y) {
        CycleButton<Boolean> btn = CycleButton.builder(
                        (Boolean v) -> Component.literal(v ? "开" : "关"), value)
                .withValues(true, false)
                .create(x, y, W, 20, Component.literal(label), (b, v) -> {
                });
        this.addRenderableWidget(btn);
        return btn;
    }

    private static int providerIndex(String id) {
        for (int i = 0; i < PROVIDER_IDS.length; i++) {
            if (PROVIDER_IDS[i].equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private void runTest() {
        ModConfig.INSTANCE = snapshot();
        ModConfig.save();
        testStatus = "测试中…";
        TranslationService.translate(List.of("Hello world! 你好,世界!"), result -> {
            if (result.error() != null) {
                this.testStatus = "❌ " + result.error();
            } else {
                this.testStatus = "✅ " + result.translated().get(0);
            }
        });
    }

    /** applies current widget values to a config copy without touching the saved instance */
    private ModConfig snapshot() {
        ModConfig cfg = ModConfig.get();
        ModConfig copy = copyOf(cfg);
        copy.enabled = enabledButton.getValue();
        copy.targetLanguage = langBox.getValue().isBlank() ? cfg.targetLanguage : langBox.getValue().trim();
        copy.provider = providerButton.getValue();
        copy.apiBase = apiBaseBox.getValue().trim();
        copy.model = modelBox.getValue().trim();
        copy.apiKey = apiKeyBox.getValue();
        copy.translateChat = chatButton.getValue();
        copy.translateBooks = booksButton.getValue();
        copy.translateSigns = signsButton.getValue();
        return copy;
    }

    private static ModConfig copyOf(ModConfig src) {
        ModConfig c = new ModConfig();
        c.provider = src.provider;
        c.apiBase = src.apiBase;
        c.model = src.model;
        c.apiKey = src.apiKey;
        c.targetLanguage = src.targetLanguage;
        c.enabled = src.enabled;
        c.translateChat = src.translateChat;
        c.translateBooks = src.translateBooks;
        c.translateSigns = src.translateSigns;
        c.chatBatchDelayTicks = src.chatBatchDelayTicks;
        c.maxBatchLines = src.maxBatchLines;
        c.requestDelayMs = src.requestDelayMs;
        c.requestTimeoutSeconds = src.requestTimeoutSeconds;
        return c;
    }

    private void saveAndClose() {
        ModConfig.INSTANCE = snapshot();
        ModConfig.save();
        this.onClose();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractBackground(graphics, mouseX, mouseY, partialTick);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int cx = this.width / 2;
        int left = cx - 155;

        graphics.centeredText(this.font, this.title, cx, 12, 0xFFFFFFFF);

        graphics.text(this.font, "目标语言", left, 27, LABEL_COLOR);
        graphics.text(this.font, "API 地址", left, 89, LABEL_COLOR);
        graphics.text(this.font, "模型", left, 125, LABEL_COLOR);
        graphics.text(this.font, "API 密钥(可留空)", left, 161, LABEL_COLOR);

        int y = 244;
        for (FormattedCharSequence line : this.font.split(FormattedText.of(testStatus), 310)) {
            graphics.text(this.font, line, cx - 155, y, 0xFFC8C8C8);
            y += 9;
            if (y > this.height - 34) {
                break;
            }
        }
    }
}
