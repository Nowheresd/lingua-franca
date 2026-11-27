package com.linguafranca;

import com.linguafranca.core.Backend;
import com.linguafranca.core.Glossary;
import com.linguafranca.core.TranslationCache;
import com.linguafranca.core.TranslationEngine;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LinguaFrancaTranslate implements ClientModInitializer {
    static {
        try {
            System.setProperty("java.awt.headless", "false");
        } catch (Throwable ignored) {
        }
    }

    public static final Logger LOG = LoggerFactory.getLogger("yijie");

    private static Path CONFIG_PATH;
    private static TranslationCache CACHE;

    public static Path configPath() {
        return CONFIG_PATH;
    }

    public static TranslationCache cache() {
        return CACHE;
    }

    @Override
    public void onInitializeClient() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configPath = configDir.resolve("lingua-franca.json");
        Path legacyConfig = configDir.resolve("yijie.json");
        if (!java.nio.file.Files.exists(legacyConfig)) {
            legacyConfig = configDir.resolve("mzkf-translate.json");
        }
        if (!java.nio.file.Files.exists(configPath) && java.nio.file.Files.exists(legacyConfig)) {
            try {
                java.nio.file.Files.copy(legacyConfig, configPath);
                LOG.info("[lingua] migrated legacy config");
            } catch (Exception ignored) {
            }
        }
        CONFIG_PATH = configPath;
        LinguaConfig cfg = LinguaConfig.load(configPath);

        Glossary glossary = Glossary.create();
        try {
            glossary.loadResource("/assets/lingua-franca/vanilla-zh.tsv");
            glossary.loadResource("/assets/lingua-franca/server-zh.tsv");
            glossary.loadResource("/assets/lingua-franca/orqol-zh.tsv");
        } catch (Exception e) {
            LOG.warn("[lingua] builtin glossary failed", e);
        }
        Path userGlossary = configDir.resolve("lingua-franca-glossary.tsv");
        try {
            if (!java.nio.file.Files.exists(userGlossary)) {
                Path legacyGlossary = configDir.resolve("yijie-glossary.tsv");
                if (!java.nio.file.Files.exists(legacyGlossary)) {
                    legacyGlossary = configDir.resolve("mzkf-glossary.tsv");
                }
                if (java.nio.file.Files.exists(legacyGlossary)) {
                    userGlossary = legacyGlossary;
                }
            }
            glossary.loadFile(userGlossary);
        } catch (Exception e) {
            LOG.warn("[lingua] user glossary failed", e);
        }

        TranslationCache cache = new TranslationCache(configDir.resolve("cache"), "translations",
                Math.max(500, cfg.maxCache), ServerScope::current);
        Backend backend = cfg.apiKey == null || cfg.apiKey.isBlank() ? null : new DeepSeekBackend(cfg);
        Glossary serverGlossary = Glossary.create();
        TranslationEngine engine = new TranslationEngine(glossary, cache, backend, 0.85);
        engine.setServerGlossary(serverGlossary);
        CACHE = cache;
        TextTranslator.init(cfg, engine);

        // 分服词表：只加载当前服务器的专有词，换服自动清空重建
        Path serverDir = configDir.resolve("lingua-franca").resolve("servers");
        long[] lastScopeStamp = {0L};
        String[] lastScope = {""};
        Thread scopeWatcher = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException e) {
                    return;
                }
                try {
                    String scope = ServerScope.current();
                    if (scope.equals(lastScope[0])) {
                        continue;
                    }
                    lastScope[0] = scope;
                    serverGlossary.clear();
                    serverGlossary.loadResource("/assets/lingua-franca/servers/" + scope + ".tsv");
                    java.nio.file.Files.createDirectories(serverDir);
                    serverGlossary.loadFile(serverDir.resolve(scope + ".tsv"));
                    TextTranslator.invalidateCaches();
                    LOG.info("[lingua] server glossary loaded for {}", scope);
                } catch (Throwable ignored) {
                }
            }
        }, "lingua-scope-watch");
        scopeWatcher.setDaemon(true);
        scopeWatcher.start();

        // 词表热更新：保存 config/lingua-franca-glossary.tsv 后 3 秒内生效，不需要重启
        Path watchedGlossary = userGlossary;
        long[] lastStamp = {stamp(watchedGlossary)};
        Thread glossaryWatcher = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(3000L);
                } catch (InterruptedException e) {
                    return;
                }
                try {
                    if (!java.nio.file.Files.exists(watchedGlossary)) {
                        continue;
                    }
                    long now = java.nio.file.Files.getLastModifiedTime(watchedGlossary).toMillis();
                    if (now == lastStamp[0]) {
                        continue;
                    }
                    lastStamp[0] = now;
                    glossary.loadFile(watchedGlossary);
                    TextTranslator.invalidateCaches();
                    LOG.info("[lingua] glossary reloaded: {} entries", glossary.size());
                } catch (Throwable ignored) {
                }
            }
        }, "lingua-glossary-watch");
        glossaryWatcher.setDaemon(true);
        glossaryWatcher.start();
        Runtime.getRuntime().addShutdownHook(new Thread(engine::close, "lingua-cache-flush"));

        Thread statsThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(300_000L);
                } catch (InterruptedException e) {
                    return;
                }
                var s = engine.stats();
                LOG.info("[lingua] stats: lookups={} localHits={} machine={} cache={}",
                        s.lookups(), s.cacheHits() + s.glossaryHits(), s.machineHits(), s.cacheSize());
            }
        }, "lingua-stats");
        statsThread.setDaemon(true);
        statsThread.start();

        LOG.info("[lingua] ready: glossary={} backend={} cache={} scope={}", glossary.size(),
                backend == null ? "none" : backend.id(), cache.loaded(), cache.scope());
        LOG.info("[lingua] flags: enabled={} fontSwitch={} textDisplay={} itemName={} itemLore={} signs={} advancements={}",
                cfg.enabled, cfg.useDefaultFontForChinese, cfg.translateTextDisplay,
                cfg.translateItemName, cfg.translateItemLore, cfg.translateSigns, cfg.translateAdvancements);
        if (backend == null) {
            LOG.warn("[lingua] no apiKey configured - only dictionary/cache will work");
        }


        // 启动自检：-Dmzkf.selftest=1 时真实调用一次接口，用来验证 Key 与网络是否通
        if (System.getProperty("lingua.selftest", System.getProperty("mzkf.selftest")) != null) {
            Thread t = new Thread(() -> {
                try {
                    String out = backend == null ? null : backend.translate("Totem of Keeping");
                    LOG.info("[lingua] selftest result: {}", out);
                } catch (Exception e) {
                    LOG.warn("[lingua] selftest failed", e);
                }
            }, "lingua-selftest");
            t.setDaemon(true);
            t.start();
        }
    }

    private static long stamp(Path path) {
        try {
            return java.nio.file.Files.exists(path)
                    ? java.nio.file.Files.getLastModifiedTime(path).toMillis() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }
}
