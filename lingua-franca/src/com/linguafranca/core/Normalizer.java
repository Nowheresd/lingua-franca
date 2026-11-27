package com.linguafranca.core;

import java.util.Locale;
import java.util.regex.Pattern;

/** 文本清洗：剥掉颜色/格式码、统一空白、生成缓存键与模板键。 */
public final class Normalizer {
    private static final Pattern FORMAT_CODE = Pattern.compile("§[0-9a-fk-orA-FK-ORxX]");
    private static final Pattern SPACES = Pattern.compile("[ \t\u00a0\u3000]+");
    private static final Pattern NUMBERS = Pattern.compile("\\d+(?:[.,]\\d+)?");
    /** 服务器用图形字编码颜色，剥掉图形后常留下孤立颜色码数字（如 "7Event"）。 */
    private static final Pattern ORPHAN_CODE = Pattern.compile("(?<![A-Za-z0-9])[0-9](?=[A-Z][a-z])");

    private Normalizer() {
    }

    /** 各种“看起来像连字符”的 Unicode 字符，统一成普通 '-'，否则词表匹配不到。 */
    private static final String DASH_LOOKALIKES = "\u2010\u2011\u2012\u2013\u2014\u2015\u2212\uFE58\uFE63\uFF0D";

    private static boolean hasDashLookalike(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (DASH_LOOKALIKES.indexOf(s.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static String unifyDashes(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(DASH_LOOKALIKES.indexOf(c) >= 0 ? '-' : c);
        }
        return sb.toString();
    }

    /** 去掉 §a 之类的格式码与多余空白，保留原始大小写。 */
    public static String clean(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String s = raw;
        if (s.indexOf('\u00a7') >= 0) {
            s = FORMAT_CODE.matcher(s).replaceAll("");
        }
        if (hasDashLookalike(s)) {
            s = unifyDashes(s);
        }
        // 常见情况：已经是干净文本，直接原样返回，省掉 sanitize 和两次正则
        if (isSimple(s)) {
            return s;
        }
        s = sanitize(s);
        s = s.replace('\n', ' ').replace('\r', ' ');
        s = ORPHAN_CODE.matcher(s).replaceAll("");
        s = s.replace('\u25A1', ' ').replace('\uFFFD', ' ');
        s = SPACES.matcher(s).replaceAll(" ").trim();
        return s;
    }

    private static boolean isSimple(String s) {
        int n = s.length();
        if (n == 0 || s.charAt(0) == ' ' || s.charAt(n - 1) == ' ') {
            return false;
        }
        boolean space = false;
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == ' ') {
                if (space) {
                    return false;
                }
                space = true;
                continue;
            }
            space = false;
            if (!allowed(c)) {
                return false;
            }
        }
        return true;
    }

    /** 只保留真正能当文字显示的字符；服务器常拿生僻 Unicode 块当图片用，混进译文就会变成巨图。 */
    public static String sanitize(String s) {
        boolean clean = true;
        for (int i = 0; i < s.length(); i++) {
            if (!allowed(s.charAt(i))) {
                clean = false;
                break;
            }
        }
        if (clean) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp <= 0xFFFF && allowed((char) cp)) {
                sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
    }

    private static boolean allowed(char c) {
        return c == 0x09 || c == 0x20 || (c >= 0x21 && c <= 0x7E)
                || (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF)
                || (c >= 0x3000 && c <= 0x303F) || (c >= 0xFF00 && c <= 0xFFEF)
                || c == 0x00D7 || c == 0x00F7 || c == 0x00B0 || c == 0x00B1
                || c == 0x00B7 || c == 0x2026 || c == 0x2014
                || c == 0x2018 || c == 0x2019 || c == 0x201C || c == 0x201D;
    }

    /** 大小写无关的精确缓存键。 */
    public static String cacheKey(String cleaned) {
        boolean simple = !cleaned.isEmpty();
        for (int i = 0; i < cleaned.length() && simple; i++) {
            char c = cleaned.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                simple = false;
            } else if (c == ' ' && (i == 0 || i == cleaned.length() - 1 || cleaned.charAt(i - 1) == ' ')) {
                simple = false;
            }
        }
        if (simple) {
            return cleaned.toLowerCase(Locale.ROOT);
        }
        return SPACES.matcher(cleaned.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    /** 把数字替换成 # 的模板键，"You earned 250 Coins" 与 "You earned 9 Coins" 共用一个条目。 */
    public static String templateKey(String cleaned) {
        return NUMBERS.matcher(cacheKey(cleaned)).replaceAll("#");
    }

    public static boolean hasLatinLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF);
    }

    public static boolean hasCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (isCjk(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** 中文占比够高就不翻了：国服文本、已经汉化的内容不该再烧 token。 */
    public static boolean isMostlyCjk(String s) {
        int cjk = 0;
        int latin = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isCjk(c)) {
                cjk++;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                latin++;
            }
        }
        return cjk >= 2 && cjk >= latin;
    }

    public static int letterCount(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) {
                n++;
            }
        }
        return n;
    }
}
