package com.linguafranca;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * 玩家名保护：翻译前把已知玩家名换成 {{0}} 占位符，翻完再还原。
 * 好处有两个：名字不会被机翻（PeaceLove -> 和平爱），而且同一个句子对不同玩家只翻一次。
 */
public final class NameGuard {
    private static volatile List<String> names = List.of();
    private static volatile long refreshedAt;

    private NameGuard() {
    }

    public record Masked(String text, List<String> tokens) {
        public String restore(String out) {
            if (out == null || tokens.isEmpty()) {
                return out;
            }
            String s = out;
            for (int i = 0; i < tokens.size(); i++) {
                s = s.replace("{{" + i + "}}", tokens.get(i));
                s = s.replace("{{ " + i + " }}", tokens.get(i));
            }
            return s;
        }
    }

    public static List<String> knownNames() {
        long now = System.currentTimeMillis();
        List<String> cached = names;
        if (now - refreshedAt < 5000 && !cached.isEmpty()) {
            return cached;
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                set.add(mc.player.getGameProfile().name());
            }
            ClientPacketListener conn = mc == null ? null : mc.getConnection();
            if (conn != null) {
                for (PlayerInfo info : conn.getOnlinePlayers()) {
                    if (info != null && info.getProfile() != null) {
                        set.add(info.getProfile().name());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            LinguaConfig cfg = LinguaConfig.get();
            if (cfg != null && cfg.protectedNames != null) {
                for (String extra : cfg.protectedNames) {
                    if (extra != null && !extra.isBlank()) {
                        set.add(extra.trim());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        List<String> list = new ArrayList<>();
        for (String n : set) {
            if (n != null && n.length() >= 3) {
                list.add(n);
            }
        }
        list.sort((a, b) -> Integer.compare(b.length(), a.length()));
        names = list;
        refreshedAt = now;
        return list;
    }

    private static final java.util.regex.Pattern LABEL = java.util.regex.Pattern.compile(
            "(?i)(seller|owner|player|buyer|author|卖家|买家|所有者|玩家)\\s*[:：]\\s*(.+)");

    /** 从“卖家: [ELITE] Extracted”这类行里，把冒号后面的名字抠出来保护。 */
    public static java.util.Set<String> protectedTokens(String line) {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (line == null || line.isEmpty()) {
            return out;
        }
        java.util.regex.Matcher m = LABEL.matcher(line);
        if (!m.find()) {
            return out;
        }
        for (String tok : m.group(2).split("[\\s\\[\\]()（）]+")) {
            String t = tok.replaceAll("[^A-Za-z0-9_]", "");
            if (t.length() >= 3 && t.length() <= 16 && t.matches("[A-Za-z0-9_]+")) {
                out.add(t);
            }
        }
        return out;
    }

    /** 整段就是一个玩家名（比如 Lore 里的“卖家”单独一个组件）。 */
    public static boolean isOnlyName(String text) {
        String t = text.trim();
        if (t.length() < 3) {
            return false;
        }
        for (String name : knownNames()) {
            if (name.equalsIgnoreCase(t)) {
                return true;
            }
        }
        return false;
    }

    public static Masked mask(String text) {
        String s = text;
        List<String> tokens = new ArrayList<>();
        for (String name : knownNames()) {
            int from = 0;
            while (true) {
                int idx = indexOfIgnoreCase(s, name, from);
                if (idx < 0) {
                    break;
                }
                int end = idx + name.length();
                boolean leftOk = idx == 0 || !isNameChar(s.charAt(idx - 1));
                boolean rightOk = end >= s.length() || !isNameChar(s.charAt(end));
                if (leftOk && rightOk) {
                    String token = "{{" + tokens.size() + "}}";
                    s = s.substring(0, idx) + token + s.substring(end);
                    tokens.add(name);
                    from = idx + token.length();
                } else {
                    from = idx + 1;
                }
            }
        }
        return new Masked(s, tokens);
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int from) {
        String h = haystack.toLowerCase(Locale.ROOT);
        String n = needle.toLowerCase(Locale.ROOT);
        return h.indexOf(n, from);
    }
}
