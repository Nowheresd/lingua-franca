package com.linguafranca;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.linguafranca.core.Backend;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** DeepSeek / 任意 OpenAI 兼容接口。 */
public final class DeepSeekBackend implements Backend {
    private static final String SYSTEM = String.join("\n",
            "你是 Minecraft 游戏文本翻译器。把用户给出的英文翻译成简体中文。",
            "规则：",
            "1. 只输出译文，不要解释、不要引号、不要加原文；",
            "2. 保留数字、百分号、颜色代码（§a 之类）、占位符（%s、{0}）原样；",
            "3. 游戏术语用大陆玩家通用译名（例如 Enchant 附魔、Crate 补给箱）；",
            "4. 名词短语保持简洁，适合直接显示在物品提示框里；",
            "5. 玩家名、公会名、服务器专有名词保持原文，不要意译。");

    private final LinguaConfig config;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public DeepSeekBackend(LinguaConfig config) {
        this.config = config;
    }

    @Override
    public String id() {
        return "deepseek";
    }

    @Override
    public String translate(String english) throws Exception {
        if (config.apiKey == null || config.apiKey.isBlank()) {
            return null;
        }
        String body = buildBody(english);
        HttpRequest req = HttpRequest.newBuilder(URI.create(trimSlash(config.baseUrl) + "/chat/completions"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey.trim())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) {
            String err = res.body();
            LinguaFrancaTranslate.LOG.warn("[lingua] deepseek HTTP {}: {}", res.statusCode(),
                    err.length() > 200 ? err.substring(0, 200) : err);
            return null;
        }
        JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
        if (!json.has("choices") || json.getAsJsonArray("choices").isEmpty()) {
            return null;
        }
        JsonObject message = json.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
        if (message == null || !message.has("content")) {
            return null;
        }
        String text = message.get("content").getAsString().trim();
        if (text.isEmpty() || text.equalsIgnoreCase(english)) {
            return null;
        }
        return text;
    }

    @Override
    public java.util.Map<String, String> translateBatch(java.util.List<String> texts) throws Exception {
        if (texts == null || texts.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < texts.size(); i += 20) {
            java.util.List<String> chunk = texts.subList(i, Math.min(texts.size(), i + 20));
            java.util.List<String> part = requestBatch(chunk);
            if (part == null || part.size() != chunk.size()) {
                return null;
            }
            out.addAll(part);
        }
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < texts.size(); i++) {
            map.put(texts.get(i), out.get(i));
        }
        return map;
    }

    @Override
    public String translateToEnglish(String chinese) throws Exception {
        if (config.apiKey == null || config.apiKey.isBlank()) {
            return null;
        }
        String system = String.join("\n",
                "把玩家输入的中文翻译成地道、简短的游戏聊天英文。",
                "只输出英文，不要解释、不要引号；保留玩家名、数字和 / 开头的命令。");
        java.util.Map<String, Object> payload = java.util.Map.of(
                "model", config.model,
                "temperature", 0,
                "max_tokens", 200,
                "messages", java.util.List.of(
                        java.util.Map.of("role", "system", "content", system),
                        java.util.Map.of("role", "user", "content", chinese)));
        String body = new com.google.gson.Gson().toJson(payload);
        HttpRequest req = HttpRequest.newBuilder(URI.create(trimSlash(config.baseUrl) + "/chat/completions"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey.trim())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) {
            String err = res.body();
            LinguaFrancaTranslate.LOG.warn("[lingua] outbound HTTP {}: {}", res.statusCode(),
                    err.length() > 160 ? err.substring(0, 160) : err);
            return null;
        }
        JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
        if (!json.has("choices") || json.getAsJsonArray("choices").isEmpty()) {
            return null;
        }
        JsonObject message = json.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
        if (message == null || !message.has("content")) {
            return null;
        }
        String text = message.get("content").getAsString().trim();
        return text.isEmpty() ? null : text;
    }

    private java.util.List<String> requestBatch(java.util.List<String> chunk) throws Exception {
        String user = new com.google.gson.Gson().toJson(chunk);
        String system = String.join("\n",
                "你是一个 Minecraft 游戏文本翻译 API。",
                "输入是一个 JSON 字符串数组，请把每个元素翻译成简体中文。",
                "输出必须是同样长度、同样顺序的 JSON 字符串数组，不要输出任何解释或 Markdown 代码块。",
                "保留数字、颜色代码（§a）、占位符（%s、{0}）。游戏术语用大陆通用译名。");
        java.util.Map<String, Object> payload = java.util.Map.of(
                "model", config.model,
                "temperature", 0,
                "max_tokens", 2400,
                "messages", java.util.List.of(
                        java.util.Map.of("role", "system", "content", system),
                        java.util.Map.of("role", "user", "content", user)));
        String body = new com.google.gson.Gson().toJson(payload);
        HttpRequest req = HttpRequest.newBuilder(URI.create(trimSlash(config.baseUrl) + "/chat/completions"))
                .timeout(Duration.ofSeconds(40))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey.trim())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) {
            String err = res.body();
            LinguaFrancaTranslate.LOG.warn("[lingua] deepseek HTTP {}: {}", res.statusCode(),
                    err.length() > 200 ? err.substring(0, 200) : err);
            return null;
        }
        JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
        if (!json.has("choices") || json.getAsJsonArray("choices").isEmpty()) {
            return null;
        }
        String content = json.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString().trim();
        int start = content.indexOf('[');
        int end = content.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        com.google.gson.JsonArray arr = JsonParser.parseString(content.substring(start, end + 1)).getAsJsonArray();
        java.util.List<String> out = new java.util.ArrayList<>(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            out.add(arr.get(i).getAsString());
        }
        return out;
    }

    private String buildBody(String english) {
        Map<String, Object> payload = Map.of(
                "model", config.model,
                "temperature", 0,
                "max_tokens", 800,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM),
                        Map.of("role", "user", "content", english)));
        return new com.google.gson.Gson().toJson(payload);
    }

    private static String trimSlash(String s) {
        String out = s.trim();
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }
}
