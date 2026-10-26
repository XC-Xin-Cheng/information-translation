package dev.infotrans.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.infotrans.InfoTranslation;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Client config, persisted to config/information_translation.json.
 */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static volatile ModConfig INSTANCE = new ModConfig();

    public static ModConfig get() {
        return INSTANCE;
    }

    // ---- provider ----
    /** "pollinations" (free, no key), "openai" (custom OpenAI-compatible endpoint), "google" (free fallback) */
    public String provider = "pollinations";
    public String apiBase = "https://text.pollinations.ai/openai";
    public String model = "openai";
    public String apiKey = "";

    // ---- behaviour ----
    public String targetLanguage = "简体中文";
    public boolean enabled = true;
    public boolean translateChat = true;
    public boolean translateBooks = true;
    public boolean translateSigns = true;

    // ---- tuning ----
    /** chat messages are batched: flush every N client ticks */
    public int chatBatchDelayTicks = 30;
    /** max items sent to the model in one request */
    public int maxBatchLines = 8;
    /** minimum delay between two HTTP requests (ms), protects free rate limits */
    public int requestDelayMs = 5000;
    public int requestTimeoutSeconds = 45;

    public static final List<String> LANGUAGE_PRESETS = List.of(
            "简体中文", "繁體中文", "English", "日本語", "한국어",
            "Français", "Deutsch", "Español", "Русский", "Português");

    public static void load() {
        Path path = path();
        if (!Files.exists(path)) {
            save();
            return;
        }
        try {
            ModConfig read = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), ModConfig.class);
            if (read != null) {
                INSTANCE = read;
            }
        } catch (Exception e) {
            InfoTranslation.LOGGER.warn("Failed to read config, using defaults", e);
        }
    }

    public static void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(INSTANCE), StandardCharsets.UTF_8);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            InfoTranslation.LOGGER.warn("Failed to save config", e);
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("information_translation.json");
    }
}
