package com.linguafranca.cache;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;

/**
 * 翻译结果的两级缓存：
 * 一级按组件对象（木牌/浮空字每帧返回同一对象），二级按文本+样式（tooltip 每帧新建对象）。
 * “无需翻译”的结论也缓存一小段时间，避免每帧重复判断。
 */
public final class ResultCache {
    public record Entry(Component out, long until) {
    }

    private static final long NEGATIVE_TTL = 900L;
    private static final int MAX_ENTRIES = 4096;

    private final Map<Component, Entry> byIdentity =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<String, Entry> byKey = new ConcurrentHashMap<>();

    public Entry identity(Component component) {
        return byIdentity.get(component);
    }

    public Entry key(String key) {
        return byKey.get(key);
    }

    public void put(Component component, String key, Component original, Component result) {
        Entry entry = result == original
                ? new Entry(null, System.currentTimeMillis() + NEGATIVE_TTL)
                : new Entry(result, Long.MAX_VALUE);
        if (byIdentity.size() > MAX_ENTRIES) {
            byIdentity.clear();
        }
        byIdentity.put(component, entry);
        if (key != null) {
            if (byKey.size() > MAX_ENTRIES) {
                byKey.clear();
            }
            byKey.put(key, entry);
        }
    }

    public void clear() {
        byIdentity.clear();
        byKey.clear();
    }

    public boolean hasResult(Component component) {
        Entry e = byIdentity.get(component);
        return e != null && (e.out != null || System.currentTimeMillis() < e.until);
    }

    /** 命中则返回可直接使用的结果；返回 null 表示需要重新计算。 */
    public static Component replay(Entry entry, Component input) {
        if (entry == null) {
            return null;
        }
        if (entry.out != null) {
            return entry.out;
        }
        return System.currentTimeMillis() < entry.until ? input : null;
    }
}
