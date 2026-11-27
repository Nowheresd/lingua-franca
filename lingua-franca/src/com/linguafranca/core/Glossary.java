package com.linguafranca.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** 本地术语表：英文 -> 中文，最长短语优先。 */
public final class Glossary {
    public record Composition(String text, double coverage) {
    }

    private final Map<String, String> map = new HashMap<>();
    private int maxLen = 2;

    public static Glossary create() {
        return new Glossary();
    }

    /** 从 mod 内置资源加载（jar 内的 assets/mzkf/vanilla-zh.tsv）。 */
    public void loadResource(String resource) throws IOException {
        try (InputStream in = Glossary.class.getResourceAsStream(resource)) {
            if (in != null) {
                loadStream(in);
            }
        }
    }

    public void loadFile(Path path) throws IOException {
        if (path != null && Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                loadStream(in);
            }
        }
    }

    private void loadStream(InputStream in) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\t", 3);
                if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
                    continue;
                }
                put(parts[0].trim(), parts[1].trim());
            }
        }
    }

    public void put(String en, String zh) {
        // 词表里可能带 § 颜色代码（历史上从服务器文本学来的），入库前一律剥掉
        if (zh != null && zh.indexOf('\u00a7') >= 0) {
            zh = net.minecraft.ChatFormatting.stripFormatting(zh);
        }
        String key = en.toLowerCase(Locale.ROOT);
        map.put(key, zh);
        maxLen = Math.max(maxLen, key.length());
    }

    public void clear() {
        map.clear();
        maxLen = 2;
    }

    public String exact(String cleaned) {
        return map.get(cleaned.toLowerCase(Locale.ROOT));
    }

    public int size() {
        return map.size();
    }

    public Composition compose(String cleaned) {
        String s = cleaned;
        int total = Normalizer.letterCount(s);
        if (total == 0) {
            return new Composition(s, 0.0);
        }
        StringBuilder out = new StringBuilder(s.length() + 16);
        int i = 0;
        int matched = 0;
        while (i < s.length()) {
            char start = s.charAt(i);
            boolean canStart = Character.isLetterOrDigit(start)
                    && (i == 0 || !Character.isLetterOrDigit(s.charAt(i - 1)));
            String best = null;
            int bestLen = 0;
            if (canStart) {
                int max = Math.min(maxLen, s.length() - i);
                for (int len = max; len >= 2; len--) {
                    int end = i + len;
                    if (end < s.length() && Character.isLetterOrDigit(s.charAt(end))) {
                        continue;
                    }
                    String zh = map.get(s.substring(i, end).toLowerCase(Locale.ROOT));
                    if (zh != null) {
                        best = zh;
                        bestLen = len;
                        break;
                    }
                }
            }
            if (best != null) {
                out.append(best);
                matched += Normalizer.letterCount(s.substring(i, i + bestLen));
                i += bestLen;
            } else {
                out.append(start);
                i++;
            }
        }
        return new Composition(tighten(out.toString()), (double) matched / total);
    }

    private static String tighten(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ' && sb.length() > 0 && i + 1 < s.length()) {
                char prev = sb.charAt(sb.length() - 1);
                char next = s.charAt(i + 1);
                if (prev == ' ') {
                    continue;
                }
                if (Normalizer.isCjk(prev) && Normalizer.isCjk(next)) {
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString().trim();
    }
}
