package com.linguafranca;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/yijie.json，首次启动自动生成。 */
public final class LinguaConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile LinguaConfig instance;

    public boolean enabled = true;
    public String apiKey = "";
    public String baseUrl = "https://api.deepseek.com";
    public String model = "deepseek-flash";
    public boolean translateChat = true;
    public boolean translateTooltip = true;
    public boolean translateItemName = true;
    public boolean translateItemLore = true;
    public int maxCache = 200000;
    /** 调试：每 30 秒打印各翻译车道排队/延迟。 */
    public boolean debugMetrics = false;
    public String[] protectedNames = new String[0];
    public boolean translateOutbound = true;
    public boolean useDefaultFontForChinese = true;
    public boolean translateTextDisplay = true;
    public boolean translateAdvancements = true;
    public boolean translateSigns = true;
    /** Tab 列表标题/页脚（部分服务器用它显示任务、事件）。 */
    public boolean translateTabList = true;
    /** Boss 条名称（部分服务器用它显示事件提示）。 */
    public boolean translateBossBar = true;
    /** 上面两项只在匹配这些服务器时生效；留空表示所有服务器。 */
    public String tabBossServers = "wynncraft";
    public boolean compatSafeMode = false;

    public static void save(Path path, LinguaConfig cfg) {
        try {
            if (cfg == null || path == null) {
                return;
            }
            java.nio.file.Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(cfg));
        } catch (Exception ignored) {
        }
    }

    public static LinguaConfig get() {
        return instance;
    }

    public static LinguaConfig load(Path path) {
        LinguaConfig cfg = null;
        try {
            if (Files.exists(path)) {
                // 旧配置文件缺少新字段时，Gson 会给出 false/0；先用默认值补齐再解析
                com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(Files.readString(path));
                if (parsed != null && parsed.isJsonObject()) {
                    com.google.gson.JsonObject obj = parsed.getAsJsonObject();
                    LinguaConfig defaults = new LinguaConfig();
                    java.lang.reflect.Field[] fields = LinguaConfig.class.getDeclaredFields();
                    for (java.lang.reflect.Field f : fields) {
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                            continue;
                        }
                        if (!obj.has(f.getName())) {
                            try {
                                obj.add(f.getName(), GSON.toJsonTree(f.get(defaults)));
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                    cfg = GSON.fromJson(obj, LinguaConfig.class);
                } else {
                    cfg = GSON.fromJson(Files.readString(path), LinguaConfig.class);
                }
            }
            if (cfg == null) {
                cfg = new LinguaConfig();
            }
            String env = System.getenv("DEEPSEEK_API_KEY");
            if ((cfg.apiKey == null || cfg.apiKey.isBlank()) && env != null && !env.isBlank()) {
                cfg.apiKey = env.trim();
            }
            if (cfg.baseUrl == null || cfg.baseUrl.isBlank()) {
                cfg.baseUrl = "https://api.deepseek.com";
            }
            if (cfg.model == null || cfg.model.isBlank()) {
                cfg.model = "deepseek-flash";
            }
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(cfg));
            }
            instance = cfg;
        } catch (Exception e) {
            cfg = new LinguaConfig();
            instance = cfg;
        }
        return cfg;
    }
}
