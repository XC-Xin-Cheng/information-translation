package dev.infotrans.translate;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.infotrans.config.ModConfig;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Talks to free chat models (browser-style, no key needed) or Google Translate,
 * and always translates items in BATCHES so whole signs / several chat lines /
 * whole book pages keep their context instead of being translated line by line.
 */
public final class TranslationService {
    private TranslationService() {
    }

    public record Result(List<String> translated, String error) {
    }

    private static final Gson GSON = new Gson();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** one worker thread: requests are naturally serialized and rate-limit friendly */
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "InformationTranslation-Worker");
        t.setDaemon(true);
        return t;
    });

    private static final Pattern INDEX_TAG = Pattern.compile("(?m)^\\s*\\[(\\d{1,3})\\][ \\t]?");

    private static final String SYSTEM_PROMPT = """
            You are a translation engine for Minecraft game content. You output only translations, never explanations.
            Preserve in-game formatting: §-color codes, emojis, player names, item names, placeholders like %s %d { } < >, links and commands.
            If a line is already in the target language, or is not natural language (a name, number, command, link), output it unchanged.""";

    private static final String SYSTEM_PROMPT_SINGLE = SYSTEM_PROMPT + "\nOutput ONLY the translation, nothing else.";

    // ---------------------------------------------------------------- cache

    private static final Map<String, String> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > 4096;
                }
            });

    private static String cacheKey(String text) {
        ModConfig cfg = ModConfig.get();
        return cfg.provider + '\u0000' + cfg.targetLanguage + '\u0000' + cfg.model + '\u0000' + text;
    }

    // ---------------------------------------------------------------- public api

    /**
     * Translates the given items (batched, context preserved between items).
     * Callback always fires on the worker thread - wrap UI/chat side effects in mc.execute().
     */
    public static void translate(List<String> items, Consumer<Result> callback) {
        if (items.isEmpty()) {
            callback.accept(new Result(List.of(), null));
            return;
        }
        EXEC.execute(() -> {
            try {
                List<String> out = new ArrayList<>(Collections.nCopies(items.size(), (String) null));
                List<Integer> missing = new ArrayList<>();
                for (int i = 0; i < items.size(); i++) {
                    String cached = CACHE.get(cacheKey(items.get(i)));
                    if (cached != null) {
                        out.set(i, cached);
                    } else {
                        missing.add(i);
                    }
                }
                ModConfig cfg = ModConfig.get();
                int batch = Math.max(1, cfg.maxBatchLines);
                for (int from = 0; from < missing.size(); from += batch) {
                    int to = Math.min(from + batch, missing.size());
                    List<Integer> slice = missing.subList(from, to);
                    List<String> inputs = new ArrayList<>(slice.size());
                    for (Integer i : slice) {
                        inputs.add(items.get(i));
                    }
                    List<String> translated = translateChunk(inputs);
                    for (int k = 0; k < slice.size(); k++) {
                        String value = translated.get(k);
                        CACHE.put(cacheKey(items.get(slice.get(k))), value);
                        out.set(slice.get(k), value);
                    }
                }
                callback.accept(new Result(out, null));
            } catch (Exception e) {
                callback.accept(new Result(null, readableError(e)));
            }
        });
    }

    private static String readableError(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isBlank()) {
            m = e.getClass().getSimpleName();
        }
        return m;
    }

    // ---------------------------------------------------------------- batch logic

    private static List<String> translateChunk(List<String> items) throws Exception {
        ModConfig cfg = ModConfig.get();
        if ("google".equals(cfg.provider)) {
            List<String> out = new ArrayList<>(items.size());
            for (String item : items) {
                out.add(item.isBlank() ? item : googleTranslate(item, cfg.targetLanguage));
            }
            return out;
        }

        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            throttle();
            String user = buildNumberedPrompt(items, cfg.targetLanguage, attempt > 0);
            String response = callChatModel(SYSTEM_PROMPT, user);
            Map<Integer, String> parsed = parseNumbered(response, items.size());
            if (parsed != null) {
                List<String> out = new ArrayList<>(items.size());
                for (int i = 0; i < items.size(); i++) {
                    String t = parsed.get(i);
                    out.add(t != null ? t : items.get(i));
                }
                return out;
            }
            last = new Exception("模型输出缺少 [i] 编号,无法对应原文(已重试)");
        }

        // single item: accept an un-tagged plain reply as the whole translation
        if (items.size() == 1) {
            try {
                throttle();
                String response = callChatModel(SYSTEM_PROMPT_SINGLE, items.get(0));
                return List.of(cleanSingle(response));
            } catch (Exception e) {
                last = e;
            }
        }
        throw last != null ? last : new Exception("翻译失败");
    }

    private static String buildNumberedPrompt(List<String> items, String language, boolean reminder) {
        StringBuilder sb = new StringBuilder();
        sb.append("Translate each numbered item below into ").append(language).append(".\n");
        sb.append("Rules:\n");
        sb.append("1. Reply with exactly ").append(items.size()).append(" items.\n");
        sb.append("2. Every reply item must start with its index tag [0], [1], ...\n");
        sb.append("3. Do not merge or split items. No explanations.\n");
        sb.append("4. Keep line breaks inside an item.\n");
        if (reminder) {
            sb.append("REMINDER: your previous answer did not follow the [i] numbering. Follow it strictly.\n");
        }
        sb.append('\n');
        for (int i = 0; i < items.size(); i++) {
            sb.append('[').append(i).append("] ").append(items.get(i));
            if (i < items.size() - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /** returns index -> translated text, or null if any index 0..n-1 is missing */
    private static Map<Integer, String> parseNumbered(String response, int n) {
        if (response == null || response.isBlank()) {
            return null;
        }
        Matcher m = INDEX_TAG.matcher(response);
        Map<Integer, String> map = new LinkedHashMap<>();
        int lastIndex = -1;
        int lastEnd = -1;
        while (m.find()) {
            if (lastIndex >= 0) {
                map.putIfAbsent(lastIndex, response.substring(lastEnd, m.start()).trim());
            }
            lastIndex = Integer.parseInt(m.group(1));
            lastEnd = m.end();
        }
        if (lastIndex >= 0) {
            map.putIfAbsent(lastIndex, response.substring(lastEnd).trim());
        }
        if (map.size() < n) {
            return null;
        }
        for (int i = 0; i < n; i++) {
            if (!map.containsKey(i)) {
                return null;
            }
        }
        return map;
    }

    private static String cleanSingle(String response) {
        String s = response == null ? "" : response.trim();
        Matcher m = INDEX_TAG.matcher(s);
        if (m.lookingAt()) {
            s = s.substring(m.end()).trim();
        }
        // strip wrapping quotes the model sometimes adds
        if (s.length() >= 2 && ((s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"')
                || (s.charAt(0) == '“' && s.charAt(s.length() - 1) == '”'))) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }

    // ---------------------------------------------------------------- providers

    private static String callChatModel(String system, String user) throws Exception {
        ModConfig cfg = ModConfig.get();
        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.model == null || cfg.model.isBlank() ? "openai" : cfg.model);
        JsonArray messages = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", system);
        messages.add(sys);
        JsonObject usr = new JsonObject();
        usr.addProperty("role", "user");
        usr.addProperty("content", user);
        messages.add(usr);
        body.add("messages", messages);
        body.addProperty("temperature", 0.2);
        body.addProperty("private", true);
        body.addProperty("referrer", "information-translation-mod");

        // free tiers answer 402/429 when queried too fast - back off and retry
        HttpResponse<String> resp = null;
        Exception rateLimited = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) {
                Thread.sleep(4000L * attempt);
            }
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.apiBase))
                    .timeout(Duration.ofSeconds(Math.max(10, cfg.requestTimeoutSeconds)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8));
            if (cfg.apiKey != null && !cfg.apiKey.isBlank()) {
                rb.header("Authorization", "Bearer " + cfg.apiKey.trim());
            }
            resp = HTTP.send(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = resp.statusCode();
            if (code == 402 || code == 429 || code >= 500) {
                rateLimited = new Exception("HTTP " + code + ": " + snippet(resp.body()));
                continue;
            }
            rateLimited = null;
            break;
        }
        if (rateLimited != null) {
            throw rateLimited;
        }
        String text = resp.body() == null ? "" : resp.body();
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new Exception("HTTP " + resp.statusCode() + ": " + snippet(text));
        }
        // OpenAI-compatible shape: choices[0].message.content
        try {
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            if (root.has("choices")) {
                return root.getAsJsonArray("choices").get(0).getAsJsonObject()
                        .getAsJsonObject("message").get("content").getAsString();
            }
            if (root.has("error")) {
                throw new Exception("API error: " + snippet(root.get("error").toString()));
            }
        } catch (IllegalStateException | java.util.NoSuchElementException ignored) {
            // fall through: treat as plain text
        }
        // Pollinations may also answer with plain text
        return text;
    }

    private static final Map<String, String> GOOGLE_CODES = Map.ofEntries(
            Map.entry("简体中文", "zh-CN"), Map.entry("繁體中文", "zh-TW"),
            Map.entry("English", "en"), Map.entry("日本語", "ja"), Map.entry("한국어", "ko"),
            Map.entry("Français", "fr"), Map.entry("Deutsch", "de"), Map.entry("Español", "es"),
            Map.entry("Русский", "ru"), Map.entry("Português", "pt"), Map.entry("Italiano", "it"),
            Map.entry("العربية", "ar"));

    private static String googleTranslate(String text, String language) throws Exception {
        ModConfig cfg = ModConfig.get();
        String tl = GOOGLE_CODES.getOrDefault(language, language);
        String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl="
                + URLEncoder.encode(tl, StandardCharsets.UTF_8)
                + "&dt=t&q=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(10, cfg.requestTimeoutSeconds)))
                .header("User-Agent", "Mozilla/5.0 (InformationTranslation mod)")
                .GET()
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new Exception("Google HTTP " + resp.statusCode());
        }
        JsonArray root = JsonParser.parseString(resp.body()).getAsJsonArray();
        StringBuilder sb = new StringBuilder();
        if (root.size() > 0 && root.get(0).isJsonArray()) {
            for (var seg : root.get(0).getAsJsonArray()) {
                if (seg.isJsonArray() && seg.getAsJsonArray().size() > 0) {
                    sb.append(seg.getAsJsonArray().get(0).getAsString());
                }
            }
        }
        return sb.toString().trim();
    }

    // ---------------------------------------------------------------- utils

    private static long lastRequestAt = 0;

    private static synchronized void throttle() {
        ModConfig cfg = ModConfig.get();
        long minGap = Math.max(0, cfg.requestDelayMs);
        long now = System.currentTimeMillis();
        long wait = lastRequestAt + minGap - now;
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestAt = System.currentTimeMillis();
    }

    private static String snippet(String s) {
        if (s == null) {
            return "";
        }
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() > 200 ? one.substring(0, 200) + "…" : one;
    }
}
