package com.linguafranca.core;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 翻译主流程：先本地（缓存 / 词表），兜不住才走机器翻译。
 * 渲染线程只调 lookupOnly / quickWithEnqueue，网络请求全部在后台线程。
 */
public final class TranslationEngine implements Closeable {
    public enum Source { CACHE, GLOSSARY, MACHINE, NONE }

    /** pending=true 表示"这次没有，后台已经在翻了"。 */
    public record Result(String original, String text, Source source, long millis, boolean pending) {
    }

    private static final Pattern NUMBERS = Pattern.compile("\\d+(?:[.,]\\d+)?");

    private final Glossary glossary;
    private volatile Glossary serverGlossary;

    public Glossary glossary() {
        return glossary;
    }

    public void setServerGlossary(Glossary g) {
        this.serverGlossary = g;
    }
    private final TranslationCache cache;
    private final Backend backend;
    private final double glossaryThreshold;
    private final ExecutorService pool;
    /** 内容预取车道：物品名/Lore/成就等大批量预取。 */
    private final ExecutorService contentPool;
    /** 批量车道：聊天/UI 合并请求。 */
    private final ExecutorService batchPool;
    /** 悬停提示车道：单条、要求快。 */
    private final ExecutorService tooltipPool;
    /** HUD 车道：记分板/Tab/Boss 条等，可容忍延迟。 */
    private final ExecutorService hudPool;
    /** 世界车道：木牌/浮空字/实体名。 */
    private final ExecutorService worldPool;
    /** 调用方线程指定的车道。 */
    private final ThreadLocal<ExecutorService> threadLane = new ThreadLocal<>();
    /** 同时在途请求上限：防止极端情况（大量新文本）把内存和队列堆爆。 */
    private static final int MAX_INFLIGHT = 400;
    private final ConcurrentHashMap<String, CompletableFuture<Result>> inflight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> failedUntil = new ConcurrentHashMap<>();
    private final Set<String> batchInflight = ConcurrentHashMap.newKeySet();

    private final AtomicLong lookups = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong glossaryHits = new AtomicLong();
    private final AtomicLong machineHits = new AtomicLong();
    private final AtomicLong pendingCount = new AtomicLong();

    public TranslationEngine(Glossary glossary, TranslationCache cache, Backend backend, double glossaryThreshold) {
        this.glossary = glossary;
        this.cache = cache;
        this.backend = backend;
        this.glossaryThreshold = glossaryThreshold;
        this.pool = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r, "yijie-worker");
            t.setDaemon(true);
            return t;
        });
        this.contentPool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "lingua-content-worker");
            t.setDaemon(true);
            return t;
        });
        this.batchPool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "lingua-batch-worker");
            t.setDaemon(true);
            return t;
        });
        this.tooltipPool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "lingua-tooltip-worker");
            t.setDaemon(true);
            return t;
        });
        this.hudPool = Executors.newFixedThreadPool(1, r -> {
            Thread t = new Thread(r, "lingua-hud-worker");
            t.setDaemon(true);
            return t;
        });
        this.worldPool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "lingua-world-worker");
            t.setDaemon(true);
            return t;
        });
    }

    /** 只查本地，绝不阻塞；给渲染线程用。 */
    public Result lookupOnly(String raw) {
        long t0 = System.nanoTime();
        String clean = Normalizer.clean(raw);
        lookups.incrementAndGet();
        if (clean.isEmpty() || !Normalizer.hasLatinLetter(clean)) {
            return new Result(raw, null, Source.NONE, millis(t0), false);
        }
        String key = Normalizer.cacheKey(clean);

        // 词表优先于缓存：改了词表就能立刻纠正旧译文
        String whole = glossary.exact(clean);
        if (whole != null) {
            cache.put(key, whole, "glossary");
            glossaryHits.incrementAndGet();
            return new Result(clean, whole, Source.GLOSSARY, millis(t0), false);
        }

        Glossary.Composition composed = glossary.compose(clean);
        if (composed.coverage() >= glossaryThreshold) {
            cache.put(key, composed.text(), "glossary");
            glossaryHits.incrementAndGet();
            return new Result(clean, composed.text(), Source.GLOSSARY, millis(t0), false);
        }

        Glossary scoped = serverGlossary;
        if (scoped != null) {
            String serverWhole = scoped.exact(clean);
            if (serverWhole != null) {
                cache.put(key, serverWhole, "server");
                glossaryHits.incrementAndGet();
                return new Result(clean, serverWhole, Source.GLOSSARY, millis(t0), false);
            }
            Glossary.Composition serverComposed = scoped.compose(clean);
            if (serverComposed.coverage() >= glossaryThreshold) {
                cache.put(key, serverComposed.text(), "server");
                glossaryHits.incrementAndGet();
                return new Result(clean, serverComposed.text(), Source.GLOSSARY, millis(t0), false);
            }
        }

        TranslationCache.Entry hit = cache.get(key);
        if (hit != null) {
            cacheHits.incrementAndGet();
            return new Result(clean, TermFix.apply(hit.text()), Source.CACHE, millis(t0), false);
        }

        Long backoff = failedUntil.get(key);
        if (backoff != null) {
            if (System.currentTimeMillis() < backoff) {
                return new Result(clean, null, Source.NONE, millis(t0), false);
            }
            failedUntil.remove(key);
        }

        String templateKey = Normalizer.templateKey(clean);
        if (!templateKey.equals(key)) {
            TranslationCache.Entry tpl = cache.get(templateKey);
            if (tpl != null) {
                String filled = fill(tpl.text(), clean);
                cache.put(key, filled, "template");
                cacheHits.incrementAndGet();
                return new Result(clean, filled, Source.CACHE, millis(t0), false);
            }
        }

        pendingCount.incrementAndGet();
        return new Result(clean, null, Source.NONE, millis(t0), backend != null);
    }

    /** 需要时自动排队机器翻译；同一个句子只会发一次请求。 */
    public CompletableFuture<Result> translate(String raw) {
        return translateFrom(lookupOnly(raw));
    }

    /** 只要结果、不关心 future：命中直接返回，未命中顺手排队（不重复做词表匹配）。 */
    public Result quickWithEnqueue(String raw) {
        Result quick = lookupOnly(raw);
        if (quick.pending() && inflight.size() < MAX_INFLIGHT) {
            String key = Normalizer.cacheKey(quick.original());
            CompletableFuture<Result> created = new CompletableFuture<>();
            if (inflight.putIfAbsent(key, created) == null) {
                submitWorker(quick, key, created);
            }
        }
        return quick;
    }

    private CompletableFuture<Result> translateFrom(Result quick) {
        if (!quick.pending()) {
            return CompletableFuture.completedFuture(quick);
        }
        if (inflight.size() >= MAX_INFLIGHT) {
            // 队列已满：本次先返回“待翻译”，不继续堆积
            return CompletableFuture.completedFuture(quick);
        }
        String key = Normalizer.cacheKey(quick.original());
        CompletableFuture<Result> created = new CompletableFuture<>();
        CompletableFuture<Result> existing = inflight.putIfAbsent(key, created);
        if (existing != null) {
            return existing;
        }
        submitWorker(quick, key, created);
        return created;
    }

    private void submitWorker(Result quick, String key, CompletableFuture<Result> created) {
        long t0 = System.nanoTime();
        ExecutorService lane = threadLane.get();
        if (lane == null) {
            lane = pool;
        }
        final ExecutorService target = lane;
        target.submit(() -> {
            try {
                String machine = backend.translate(key);
                if (machine == null) {
                    failedUntil.put(key, System.currentTimeMillis() + 60_000L);
                    created.complete(new Result(quick.original(), null, Source.NONE, millis(t0), false));
                    return;
                }
                failedUntil.remove(key);
                cache.put(key, machine, backend.id());
                String tplKey = Normalizer.templateKey(key);
                if (!tplKey.equals(key)) {
                    cache.put(tplKey, asTemplate(machine), backend.id());
                }
                machineHits.incrementAndGet();
                created.complete(new Result(quick.original(), machine, Source.MACHINE, millis(t0), false));
            } catch (Exception e) {
                created.complete(new Result(quick.original(), null, Source.NONE, millis(t0), false));
            } finally {
                inflight.remove(key, created);
            }
        });
    }

    /**
     * 预取：打开容器时把一屏物品的文本一次性排队。
     * 能本地命中的直接进缓存，剩下的合并成一次批量请求。
     */
    public void prefetch(Collection<String> raws) {
        if (raws == null || raws.isEmpty() || backend == null) {
            return;
        }
        LinkedHashSet<String> misses = new LinkedHashSet<>();
        for (String raw : raws) {
            String clean = Normalizer.clean(raw);
            if (clean.isEmpty() || !Normalizer.hasLatinLetter(clean) || Normalizer.isMostlyCjk(clean)) {
                continue;
            }
            Result quick = lookupOnly(clean);
            if (quick.text() == null && quick.pending()) {
                misses.add(clean);
            }
        }
        if (misses.isEmpty()) {
            return;
        }
        final List<String> all = new ArrayList<>(misses);
        final List<String> batch = all.size() > 300
                ? new ArrayList<>(all.subList(0, 300))
                : all;
        contentPool.submit(() -> {
            try {
                Map<String, String> done = backend.translateBatch(batch);
                if (done == null) {
                    return;
                }
                for (Map.Entry<String, String> e : done.entrySet()) {
                    if (e.getValue() != null && !e.getValue().isBlank()) {
                        cache.put(Normalizer.cacheKey(e.getKey()), e.getValue(), backend.id());
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    /** 悬停整块提示：合并成一次批量请求。 */
    public void translateAll(Collection<String> raws) {
        if (backend == null || raws == null || raws.isEmpty()) {
            return;
        }
        LinkedHashSet<String> misses = new LinkedHashSet<>();
        for (String raw : raws) {
            String clean = Normalizer.clean(raw);
            if (clean.isEmpty() || !Normalizer.hasLatinLetter(clean) || Normalizer.isMostlyCjk(clean)) {
                continue;
            }
            Result quick = lookupOnly(clean);
            if (quick.text() != null) {
                continue;
            }
            String key = Normalizer.cacheKey(clean);
            Long until = failedUntil.get(key);
            if (until != null && System.currentTimeMillis() < until) {
                continue;
            }
            if (batchInflight.add(clean)) {
                misses.add(clean);
            }
        }
        if (misses.isEmpty()) {
            return;
        }
        final List<String> all = new ArrayList<>(misses);
        final List<String> batch = all.size() > 300
                ? new ArrayList<>(all.subList(0, 300))
                : all;
        contentPool.submit(() -> {
            try {
                Map<String, String> done = backend.translateBatch(batch);
                if (done != null) {
                    for (Map.Entry<String, String> e : done.entrySet()) {
                        if (e.getValue() != null && !e.getValue().isBlank()) {
                            cache.put(Normalizer.cacheKey(e.getKey()), e.getValue(), backend.id());
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                batchInflight.removeAll(batch);
            }
        });
    }

    /** 一批文本一起翻：命中缓存的直接返回，剩下的合并成一次请求。 */
    public CompletableFuture<java.util.Map<String, String>> translateMany(java.util.List<String> raws) {
        java.util.Map<String, String> done = new java.util.LinkedHashMap<>();
        java.util.List<String> misses = new java.util.ArrayList<>();
        for (String raw : raws) {
            String clean = Normalizer.clean(raw);
            if (clean.isEmpty() || !Normalizer.hasLatinLetter(clean) || Normalizer.isMostlyCjk(clean)) {
                continue;
            }
            String key = Normalizer.cacheKey(clean);
            TranslationCache.Entry hit = cache.get(key);
            if (hit != null) {
                done.put(raw, hit.text());
                continue;
            }
            Long until = failedUntil.get(key);
            if (until != null && System.currentTimeMillis() < until) {
                continue;
            }
            misses.add(clean);
        }
        if (misses.isEmpty() || backend == null) {
            return CompletableFuture.completedFuture(done);
        }
        java.util.List<String> batch = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(misses));
        return CompletableFuture.supplyAsync(() -> {
            java.util.Map<String, String> out = new java.util.LinkedHashMap<>(done);
            try {
                java.util.Map<String, String> translated = backend.translateBatch(batch);
                if (translated != null) {
                    for (java.util.Map.Entry<String, String> e : translated.entrySet()) {
                        if (e.getValue() != null && !e.getValue().isBlank()) {
                            cache.put(Normalizer.cacheKey(e.getKey()), e.getValue(), backend.id());
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            for (String raw : raws) {
                if (out.containsKey(raw)) {
                    continue;
                }
                TranslationCache.Entry hit = cache.get(Normalizer.cacheKey(Normalizer.clean(raw)));
                if (hit != null) {
                    out.put(raw, hit.text());
                }
            }
            return out;
        }, batchPool);
    }

    /** 让当前线程的翻译请求走指定车道（对话等模块使用）。 */
    public java.util.concurrent.ExecutorService currentLane() {
        return threadLane.get();
    }

    public void setThreadLane(java.util.concurrent.ExecutorService lane) {
        if (lane == null) {
            threadLane.remove();
        } else {
            threadLane.set(lane);
        }
    }

    public ExecutorService tooltipLane() {
        return tooltipPool;
    }

    public ExecutorService hudLane() {
        return hudPool;
    }

    public ExecutorService worldLane() {
        return worldPool;
    }

    /** 各车道排队情况，调试用。 */
    public String laneMetrics() {
        return "mainQ=" + queueOf(pool)
                + " contentQ=" + queueOf(contentPool)
                + " batchQ=" + queueOf(batchPool)
                + " tooltipQ=" + queueOf(tooltipPool)
                + " hudQ=" + queueOf(hudPool)
                + " worldQ=" + queueOf(worldPool)
                + " pending=" + pendingCount.get();
    }

    private static int queueOf(ExecutorService executor) {
        if (executor instanceof java.util.concurrent.ThreadPoolExecutor tpe) {
            return tpe.getQueue().size();
        }
        return -1;
    }

    public String cachedOutbound(String chinese) {
        TranslationCache.Entry hit = cache.get("out:" + Normalizer.cacheKey(chinese));
        return hit == null ? null : hit.text();
    }

    /** 中文 -> 英文，给发送聊天用。 */
    public CompletableFuture<String> translateOutbound(String chinese) {
        String cached = cachedOutbound(chinese);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        if (backend == null) {
            return CompletableFuture.completedFuture(null);
        }
        String key = "out:" + Normalizer.cacheKey(chinese);
        return CompletableFuture.supplyAsync(() -> {
            try {
                String en = backend.translateToEnglish(chinese);
                if (en != null && !en.isBlank()) {
                    cache.put(key, en, "outbound");
                }
                return en;
            } catch (Exception e) {
                return null;
            }
        }, pool);
    }

    public Result translateBlocking(String raw, long timeoutMs) {
        try {
            return translate(raw).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException | InterruptedException e) {
            return lookupOnly(raw);
        }
    }

    public Stats stats() {
        return new Stats(lookups.get(), cacheHits.get(), glossaryHits.get(), machineHits.get(),
                pendingCount.get(), cache.size(), cache.loaded());
    }

    public record Stats(long lookups, long cacheHits, long glossaryHits, long machineHits,
                        long pending, int cacheSize, int cacheOnDisk) {
        public double hitRate() {
            return lookups == 0 ? 0 : (double) (cacheHits + glossaryHits) / lookups;
        }
    }

    private static String asTemplate(String text) {
        return NUMBERS.matcher(text).replaceAll("#");
    }

    private static String fill(String template, String source) {
        List<String> numbers = new ArrayList<>();
        Matcher m = NUMBERS.matcher(source);
        while (m.find()) {
            numbers.add(m.group());
        }
        if (numbers.isEmpty()) {
            return template;
        }
        StringBuilder sb = new StringBuilder(template.length() + 8);
        int idx = 0;
        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);
            if (c == '#' && idx < numbers.size()) {
                sb.append(numbers.get(idx++));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static long millis(long t0) {
        return (System.nanoTime() - t0) / 1_000_000L;
    }

    @Override
    public void close() {
        pool.shutdown();
        contentPool.shutdown();
        batchPool.shutdown();
        tooltipPool.shutdown();
        hudPool.shutdown();
        worldPool.shutdown();
        cache.flush();
    }
}
