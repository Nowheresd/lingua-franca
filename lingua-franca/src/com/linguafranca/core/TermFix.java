package com.linguafranca.core;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 品牌/货币等专有名词的兜底修正：模型偶尔会把 OR-Bucks 当普通英文拆开翻。 */
public final class TermFix {
    private static final Map<String, String> TERMS = new LinkedHashMap<>();

    static {
        TERMS.put("or-bucks", "OR币");
        TERMS.put("or bucks", "OR币");
        TERMS.put("orbucks", "OR币");
        TERMS.put("or-buck", "OR币");
        // 模型曾把 OR-Bucks 拆成「OR + Bucks」翻成“或币”，这里一并纠正
        TERMS.put("或币", "OR币");
        TERMS.put("或 币", "OR币");
        TERMS.put("or币", "OR币");
    }

    /** 文本不含可疑字符时直接返回，避免每帧创建字符串。 */
    public static String guard(String text) {
        if (text == null || text.indexOf('\u6216') < 0) {
            return text;
        }
        return apply(text);
    }

    private TermFix() {
    }

    public static String apply(String text) {
        if (text == null) {
            return null;
        }
        if (text.indexOf('\u00a7') >= 0) {
            text = net.minecraft.ChatFormatting.stripFormatting(text);
        }
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = text;
        for (Map.Entry<String, String> e : TERMS.entrySet()) {
            out = replaceIgnoreCase(out, e.getKey(), e.getValue());
        }
        return out;
    }

    private static String replaceIgnoreCase(String text, String target, String replacement) {
        String lower = text.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(target);
        if (idx < 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length() + 8);
        int from = 0;
        while (idx >= 0) {
            sb.append(text, from, idx).append(replacement);
            from = idx + target.length();
            idx = lower.indexOf(target, from);
        }
        sb.append(text, from, text.length());
        return sb.toString();
    }
}
