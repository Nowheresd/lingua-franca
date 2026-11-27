package com.linguafranca.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 按服务器分文件存放的译文缓存。
 * 文件名形如 translations-server_play.example.net.tsv，换服就换文件，互不污染。
 */
public final class TranslationCache implements Closeable {
    public record Entry(String text, String source) {
    }

    private final Path dir;
    private final String prefix;
    private final int capacity;
    private final Supplier<String> scopeSupplier;
    private final Map<String, Entry> map = new ConcurrentHashMap<>();

    private volatile String scope = "";
    private volatile boolean dirty;
    private volatile int loaded;
    private volatile long lastScopeCheck;
    /** 缓存文件异步加载：进服时不在渲染线程读 13MB 文本，避免卡顿。 */
    private final java.util.concurrent.ExecutorService loader =
            java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "lingua-cache-loader");
                thread.setDaemon(true);
                return thread;
            });
    private volatile boolean loading;

    public TranslationCache(Path dir, String prefix, int capacity, Supplier<String> scopeSupplier) {
        this.dir = dir;
        this.prefix = prefix;
        this.capacity = Math.max(100, capacity);
        this.scopeSupplier = scopeSupplier;
        ensureScope();

        Thread flusher = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(10_000L);
                } catch (InterruptedException e) {
                    return;
                }
                flush();
            }
        }, "mzkf-cache-flush");
        flusher.setDaemon(true);
        flusher.start();

        // 关游戏时强制落盘：否则最后的译文会丢，下次又要重新请求
        Thread hook = new Thread(() -> {
            try {
                flush();
            } catch (Throwable ignored) {
            }
        }, "mzkf-cache-shutdown");
        try {
            Runtime.getRuntime().addShutdownHook(hook);
        } catch (Throwable ignored) {
        }
    }

    public String scope() {
        return scope;
    }

    private Path file() {
        return dir.resolve(prefix + "-" + scope + ".tsv");
    }

    private synchronized void ensureScope() {
        String now = scopeSupplier == null ? "global" : scopeSupplier.get();
        if (now == null || now.isBlank()) {
            now = "global";
        }
        lastScopeCheck = System.currentTimeMillis();
        if (now.equals(scope)) {
            return;
        }
        if (!scope.isEmpty()) {
            flushLocked();
        }
        scope = now;
        map.clear();
        loaded = 0;
        startLoadAsync();
    }

    private static boolean looksCorrupt(String key) {
        return key.matches("(?s).*(\\b(cl|bl)[0-9]|[0-9](cl|bl)\\b|(^|\\s)4[a-z]|(^|\\s)[0-9][A-Z][a-z]).*");
    }

    /** 后台线程读取缓存文件，读完后再合并进 map；渲染线程不再被 IO 阻塞。 */
    private void startLoadAsync() {
        Path target = file();
        String expectedScope = scope;
        if (!Files.exists(target)) {
            loading = false;
            return;
        }
        loading = true;
        loader.submit(() -> {
            java.util.Map<String, Entry> tmp = new java.util.HashMap<>(32768);
            try (BufferedReader r = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] parts = line.split("\t", 3);
                    if (parts.length == 3 && !parts[0].isEmpty() && !looksCorrupt(parts[0])) {
                        tmp.put(parts[0], new Entry(stripCodes(parts[1]), parts[2]));
                    }
                }
            } catch (IOException ignored) {
            }
            synchronized (this) {
                if (!scope.equals(expectedScope)) {
                    loading = false;
                    return;
                }
                int added = 0;
                for (java.util.Map.Entry<String, Entry> e : tmp.entrySet()) {
                    if (!map.containsKey(e.getKey())) {
                        map.put(e.getKey(), e.getValue());
                        added++;
                    }
                }
                loaded += added;
                dirty = true;
                loading = false;
            }
        });
    }

    public Entry get(String key) {
        if (System.currentTimeMillis() - lastScopeCheck > 500L) {
            ensureScope();
        }
        return map.get(key);
    }

    public void put(String key, String text, String source) {
        if (key == null || key.isEmpty() || text == null || text.isEmpty()) {
            return;
        }
        ensureScope();
        map.put(key, new Entry(stripCodes(text), source));
        dirty = true;
        if (map.size() > capacity) {
            int target = capacity * 9 / 10;
            Iterator<String> it = map.keySet().iterator();
            while (it.hasNext() && map.size() > target) {
                it.next();
                it.remove();
            }
        }
    }

    /** 导出当前服务器的词库，供其他玩家导入。 */
    public synchronized int exportTo(Path file) {
        try {
            flushLocked();
            java.nio.file.Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Entry> e : map.entrySet()) {
                sb.append(e.getKey()).append('\t').append(e.getValue().text())
                        .append('\t').append(e.getValue().source()).append('\n');
            }
            java.nio.file.Files.writeString(file, sb.toString());
            return map.size();
        } catch (Exception e) {
            return -1;
        }
    }

    /** 导入一个文件夹里所有 .tsv（分享词库），返回累计条目数。 */
    public synchronized int importFolder(Path folder) {
        try {
            if (folder == null || !java.nio.file.Files.isDirectory(folder)) {
                return 0;
            }
            int total = 0;
            try (var stream = java.nio.file.Files.list(folder)) {
                for (Path f : stream.filter(p -> p.toString().toLowerCase().endsWith(".tsv")).toList()) {
                    int n = importFrom(f);
                    if (n > 0) {
                        total += n;
                    }
                }
            }
            return total;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 导入别的玩家分享的词库；同 key 以导入内容为准。 */
    public synchronized int importFrom(Path file) {
        try {
            if (!java.nio.file.Files.exists(file)) {
                return 0;
            }
            int count = 0;
            for (String line : java.nio.file.Files.readAllLines(file)) {
                String[] parts = line.split("\t", 3);
                if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
                    continue;
                }
                map.put(parts[0], new Entry(stripCodes(parts[1]), parts.length > 2 ? parts[2] : "import"));
                count++;
            }
            flushLocked();
            return count;
        } catch (Exception e) {
            return -1;
        }
    }

    public int size() {
        return map.size();
    }

    public int loaded() {
        return loaded;
    }

    @Override
    public void close() {
        try {
            loader.shutdownNow();
        } catch (Throwable ignored) {
        }
        flush();
    }

    public void flush() {
        ensureScope();
        flushLocked();
    }

    private synchronized void flushLocked() {
        if (loading) {
            return;
        }
        if (!dirty || scope.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(dir);
            Path target = file();
            Path tmp = dir.resolve(target.getFileName() + ".tmp");
            try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, Entry> e : map.entrySet()) {
                    w.write(sanitize(e.getKey()));
                    w.write('\t');
                    w.write(sanitize(e.getValue().text()));
                    w.write('\t');
                    w.write(sanitize(e.getValue().source()));
                    w.write('\n');
                }
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException ignored) {
        }
    }

    private static String sanitize(String s) {
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    /** 去掉 § 颜色代码：缓存里历史遗留的、模型返回的、导入的都清理一遍。 */
    private static String stripCodes(String s) {
        if (s == null || s.indexOf('\u00a7') < 0) {
            return s;
        }
        return net.minecraft.ChatFormatting.stripFormatting(s);
    }
}
