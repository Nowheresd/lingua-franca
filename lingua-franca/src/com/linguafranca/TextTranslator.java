package com.linguafranca;

import com.linguafranca.core.Normalizer;
import com.linguafranca.cache.ResultCache;
import com.linguafranca.core.TranslationEngine;
import com.linguafranca.text.Segmenter;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

/** 把翻译引擎接到 Minecraft 的文本组件上。 */
public final class TextTranslator {
    private static final String PREFIX = "\u00a78[\u8bd1] \u00a77";
    private record PendingChat(ChatComponent target, String raw, NameGuard.Masked masked, String speaker) {
    }

    private static final java.util.Queue<PendingChat> CHAT_QUEUE = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final java.util.concurrent.atomic.AtomicBoolean CHAT_FLUSHER = new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.regex.Pattern NAME_TOKEN = java.util.regex.Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final java.util.regex.Pattern DIGIT = java.util.regex.Pattern.compile("\\d");
    private static final java.util.regex.Pattern RANK_NAME = java.util.regex.Pattern.compile("^\\[[A-Za-z0-9]{2,12}\\]\\s+[A-Za-z0-9_]{2,16}$");
    private static final java.util.concurrent.ConcurrentHashMap<String, String> HIT = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> SKIP = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final java.util.regex.Pattern SPEAKER = java.util.regex.Pattern.compile("^([A-Za-z0-9_]{2,16})\\s*[:：]\\s*(.+)$");
    private static final java.util.regex.Pattern TRAILING_UI = java.util.regex.Pattern.compile(
            "(?i)\\s*(?:→|➜|»|>)?\\s*(?:menu|next|continue|close|back|previous|view|shop|leave|exit)\\s*(?:→|➜|»|>)?\\s*$");

    private static final ThreadLocal<Boolean> PREFETCH = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static volatile TranslationEngine engine;
    private static volatile LinguaConfig config;
    private static volatile ChatComponent chat;

    private TextTranslator() {
    }

    static LinguaConfig configRef() {
        return config;
    }

    static TranslationEngine engineRef() {
        return engine;
    }

    public static void init(LinguaConfig cfg, TranslationEngine e) {
        startLaneMetrics();
        config = cfg;
        engine = e;
    }

    public static void setChat(ChatComponent component) {
        chat = component;
    }

    /** 物品名 / Lore / 标题 / Boss 条：逐节点重建，保留颜色。 */

    /** 标题里带图标字形时整条不动：换字会丢图标，只剩方框。 */
    public static boolean titleSafe(Component title) {
        try {
            String s = title.getString();
            if (s.length() > 120) {
                return false;
            }
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c > 0x7E) {
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 已知错译术语的窄范围兜底：只处理含“或”的文本，其它原样返回。 */
    public static Component guard(Component component) {
        if (component == null) {
            return null;
        }
        String text = component.getString();
        if (text.indexOf('\u6216') < 0) {
            return component;
        }
        String fixed = com.linguafranca.core.TermFix.apply(text);
        if (fixed.equals(text)) {
            return component;
        }
        return legacy(fixed, component.getStyle());
    }

    /** 批量兜底：tooltip 行列表里可能只有一行含“或币”，只替换那一行。 */
    public static java.util.List<Component> guardList(java.util.List<Component> lines) {
        if (lines == null || lines.isEmpty()) {
            return lines;
        }
        java.util.List<Component> out = null;
        for (int i = 0; i < lines.size(); i++) {
            Component fixed = guard(lines.get(i));
            if (fixed != lines.get(i)) {
                if (out == null) {
                    out = new java.util.ArrayList<>(lines);
                }
                out.set(i, fixed);
            }
        }
        return out == null ? lines : out;
    }

    /** 任意 FormattedCharSequence 的窄范围术语兜底（跨样式段），无变化时原样返回。 */
    public static net.minecraft.util.FormattedCharSequence guardSequence(
            net.minecraft.util.FormattedCharSequence seq) {
        if (seq == null) {
            return null;
        }
        java.util.List<String> texts = new java.util.ArrayList<>();
        java.util.List<net.minecraft.network.chat.Style> styles = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        net.minecraft.network.chat.Style[] curStyle = new net.minecraft.network.chat.Style[1];
        seq.accept((index, style, codepoint) -> {
            if (curStyle[0] == null) {
                curStyle[0] = style;
            } else if (!curStyle[0].equals(style)) {
                texts.add(cur.toString());
                styles.add(curStyle[0]);
                cur.setLength(0);
                curStyle[0] = style;
            }
            cur.appendCodePoint(codepoint);
            return true;
        });
        if (curStyle[0] != null) {
            texts.add(cur.toString());
            styles.add(curStyle[0]);
        }
        net.minecraft.util.FormattedCharSequence fixed = termFixedSequence(texts, styles);
        return fixed == null ? seq : fixed;
    }

    private static boolean isPrivateGlyph(char c) {
        // 私用区 BMP + 代理对（补充平面私用区），以及部分服务器图标用的私用范围
        return (c >= 0xE000 && c <= 0xF8FF) || (c >= 0xD800 && c <= 0xDFFF) || c >= 0xF0000;
    }

    private static boolean hasSurrogateOrGlyph(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (isPrivateGlyph(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsCjk(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                return true;
            }
        }
        return false;
    }

    private static final java.util.regex.Pattern WIDE_GAP =
            java.util.regex.Pattern.compile("[ \t]{2,}");

    /**
     * 拼版式 HUD：按“两个以上空格”分段，只翻其中像句子的片段，
     * 分隔空格和图标字形原样保留，所以版式不会被挤乱。
     */
    public static String translateSegments(String text, java.util.Set<String> protect) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (text.indexOf('\u00a7') >= 0) {
            text = net.minecraft.ChatFormatting.stripFormatting(text);
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        StringBuilder chunk = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            boolean gap = c == ' ' || c == '\t';
            if (gap) {
                int j = i;
                while (j < text.length() && (text.charAt(j) == ' ' || text.charAt(j) == '\t')) {
                    j++;
                }
                int run = j - i;
                if (run >= 2) {
                    if (chunk.length() > 0) {
                        out.append(translateChunk(chunk.toString(), protect));
                        chunk.setLength(0);
                    }
                    out.append(text, i, j);
                    i = j;
                    continue;
                }
            }
            if (c > 0x7E) {
                if (chunk.length() > 0) {
                    out.append(translateChunk(chunk.toString(), protect));
                    chunk.setLength(0);
                }
                out.append(c);
                i++;
                continue;
            }
            chunk.append(c);
            i++;
        }
        if (chunk.length() > 0) {
            out.append(translateChunk(chunk.toString(), protect));
        }
        return out.toString();
    }

    private static String translateChunk(String chunk, java.util.Set<String> protect) {
        if (chunk.isEmpty()) {
            return chunk;
        }
        int letters = 0;
        int longest = 0;
        int run = 0;
        boolean asciiOnly = true;
        for (int i = 0; i < chunk.length(); i++) {
            char c = chunk.charAt(i);
            if (c > 0x7E) {
                asciiOnly = false;
                break;
            }
            if (Character.isLetter(c)) {
                letters++;
                run++;
                longest = Math.max(longest, run);
            } else {
                run = 0;
            }
        }
        if (!asciiOnly || longest < 3) {
            return chunk;
        }
        String trimmed = chunk.trim();
        if (trimmed.isEmpty() || trimmed.length() > 80) {
            return chunk;
        }
        String zh = lookup(trimmed, protect);
        if (zh == null || zh.equals(trimmed)) {
            return chunk;
        }
        int lead = chunk.indexOf(trimmed);
        int tail = chunk.length() - lead - trimmed.length();
        StringBuilder sb = new StringBuilder(chunk.length());
        for (int i = 0; i < lead; i++) {
            sb.append(chunk.charAt(i));
        }
        sb.append(zh);
        for (int i = 0; i < tail; i++) {
            sb.append(chunk.charAt(chunk.length() - tail + i));
        }
        return sb.toString();
    }

    /**
     * 对话框/说明板这类固定宽度的文本：先逐节点翻译，再按像素宽度手动换行，
     * 否则中文没有空格，原版自动换行不会断行，就会挤成一行。
     */
    public static net.minecraft.network.chat.Component translateDialog(
            net.minecraft.network.chat.Component c, int maxWidthPx) {
        return translateComposed(c, maxWidthPx);
    }

    /**
     * 保留组件树结构：每个文字节点单独翻译并换行，图标节点原样保留，
     * 不拍平，所以图标字体和位置都不会坏。
     */
    public static net.minecraft.network.chat.Component translateComposed(
            net.minecraft.network.chat.Component c, int maxWidthPx) {
        if (c == null) {
            return null;
        }
        try {
            boolean[] changed = new boolean[1];
            var out = composedRebuild(c, maxWidthPx, java.util.Set.of(), changed);
            return changed[0] ? out : c;
        } catch (Throwable t) {
            return c;
        }
    }

    /** 把带 § 颜色代码的字符串还原成组件：代码转成样式，不再作为可见字符残留。 */
    /** 模型偶尔会在译文里自己塞 § 颜色代码，统一剥掉，避免渲染成可见的 f/7/8。 */
    private static String cleanTranslated(String s) {
        if (s == null) {
            return null;
        }
        return s.indexOf('\u00a7') < 0 ? s : net.minecraft.ChatFormatting.stripFormatting(s);
    }

    /** 兜底：把仍然残留在译文里的 § 颜色代码剥掉，避免它们变成可见的 f/7/8。 */
    /** 泄漏定位：记录仍带 § 的返回文本 + 调用栈，只在日志里打一次，不影响渲染。 */
    private static final java.util.Set<String> LEAK_SEEN =
            java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());

    private static void logLeak(String where, String text) {
        if (text == null || text.indexOf('\u00a7') < 0) {
            return;
        }
        String key = where + '|' + text;
        if (LEAK_SEEN.size() > 40 || !LEAK_SEEN.add(key)) {
            return;
        }
        StringBuilder sb = new StringBuilder("[lingua] LEAK where=").append(where)
                .append(" text=[").append(text.replace((char) 10, '|')).append("] <- ");
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        for (int i = 2; i < Math.min(st.length, 9); i++) {
            sb.append(st[i].getClassName().replace("net.minecraft.", "")).append('.')
                    .append(st[i].getMethodName()).append(" <- ");
        }
        System.out.println(sb);
    }

    private static net.minecraft.network.chat.Component stripLegacy(
            net.minecraft.network.chat.Component c) {
        if (c == null) {
            return null;
        }
        try {
            if (c.getString().indexOf('\u00a7') < 0) {
                return c;
            }
            return deepStrip(c);
        } catch (Throwable t) {
            return c;
        }
    }

    private static net.minecraft.network.chat.MutableComponent deepStrip(
            net.minecraft.network.chat.Component c) {
        net.minecraft.network.chat.MutableComponent out;
        if (c.getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents plain) {
            out = legacy(plain.text(), c.getStyle());
        } else {
            out = c.plainCopy();
            out.setStyle(c.getStyle());
        }
        for (net.minecraft.network.chat.Component sibling : c.getSiblings()) {
            out.append(deepStrip(sibling));
        }
        return out;
    }

    private static net.minecraft.network.chat.MutableComponent legacy(
            String text, net.minecraft.network.chat.Style base) {
        net.minecraft.network.chat.Style start =
                base == null ? net.minecraft.network.chat.Style.EMPTY : base;
        if (text == null || text.isEmpty()) {
            return net.minecraft.network.chat.Component.empty().setStyle(start);
        }
        if (text.indexOf('\u00a7') < 0) {
            return net.minecraft.network.chat.Component.literal(text).setStyle(start);
        }
        net.minecraft.network.chat.MutableComponent root = net.minecraft.network.chat.Component.empty();
        net.minecraft.network.chat.Style style = start;
        StringBuilder run = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == 'x' && i + 13 < text.length()) {
                    StringBuilder hex = new StringBuilder(6);
                    for (int k = i + 2; k < i + 14 && hex.length() < 6; k++) {
                        char h = text.charAt(k);
                        if (h != '\u00a7' && h != 'x' && h != 'X') hex.append(h);
                    }
                    if (hex.length() == 6) {
                        try {
                            if (run.length() > 0) {
                                root.append(net.minecraft.network.chat.Component.literal(run.toString())
                                        .setStyle(style));
                                run.setLength(0);
                            }
                            style = style.withColor(net.minecraft.network.chat.TextColor.fromRgb(
                                    Integer.parseInt(hex.toString(), 16)));
                            i += 14;
                            continue;
                        } catch (Throwable ignored) {
                            // fall through, treat as plain text
                        }
                    }
                }
                net.minecraft.network.chat.Style next = legacyStyle(style, code);
                if (next != null) {
                    if (run.length() > 0) {
                        root.append(net.minecraft.network.chat.Component.literal(run.toString()).setStyle(style));
                        run.setLength(0);
                    }
                    style = next;
                    i += 2;
                    continue;
                }
            }
            run.append(c);
            i++;
        }
        if (run.length() > 0) {
            root.append(net.minecraft.network.chat.Component.literal(run.toString()).setStyle(style));
        }
        root.setStyle(start);
        return root;
    }

    private static net.minecraft.network.chat.Style legacyStyle(
            net.minecraft.network.chat.Style style, char code) {
        net.minecraft.ChatFormatting format = net.minecraft.ChatFormatting.getByCode(code);
        if (format == null) {
            return null;
        }
        if (format == net.minecraft.ChatFormatting.RESET) {
            return net.minecraft.network.chat.Style.EMPTY;
        }
        if (format.ordinal() <= 15) { // BLACK..WHITE are the colour constants
            return style.withColor(format);
        }
        return switch (format) {
            case OBFUSCATED -> style.withObfuscated(true);
            case BOLD -> style.withBold(true);
            case STRIKETHROUGH -> style.withStrikethrough(true);
            case UNDERLINE -> style.withUnderlined(true);
            case ITALIC -> style.withItalic(true);
            default -> style;
        };
    }

    private static net.minecraft.network.chat.MutableComponent composedRebuild(
            net.minecraft.network.chat.Component c, int maxWidthPx,
            java.util.Set<String> protect, boolean[] changed) {
        var style = c.getStyle() == null ? net.minecraft.network.chat.Style.EMPTY : c.getStyle();
        net.minecraft.network.chat.MutableComponent base;
        if (c.getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents plain) {
            String source = plain.text();
            String fixed = translateSegments(source, protect);
            boolean translated = !fixed.equals(source);
            if (translated) {
                changed[0] = true;
            }
            boolean cjk = containsCjk(fixed);
            if (translated && cjk && maxWidthPx > 0) {
                fixed = wrapByWidth(fixed, maxWidthPx);
            }
            // 同一节点里可能既有图标字形又有文字：拆成多段，图标用原字体、中文用默认字体
            MutableComponent holder = Component.empty();
            holder.setStyle(style);
            int i = 0;
            while (i < fixed.length()) {
                boolean glyph = isPrivateGlyph(fixed.charAt(i));
                int j = i + 1;
                while (j < fixed.length() && isPrivateGlyph(fixed.charAt(j)) == glyph) {
                    j++;
                }
                String run = fixed.substring(i, j);
                MutableComponent part = legacy(run, style);
                if (!glyph && containsCjk(run)) {
                    part.setStyle(cjkStyle(style));
                } else {
                    part.setStyle(style);
                }
                holder.append(part);
                i = j;
            }
            base = holder;
        } else {
            base = legacy(c.getString(), style);
            base.setStyle(style);
        }
        for (net.minecraft.network.chat.Component sibling : c.getSiblings()) {
            base.append(composedRebuild(sibling, maxWidthPx, protect, changed));
        }
        return base;
    }

    /** 把译文重新排成和原文一样的行数，避免浮空字因为行数变化整体位移。 */
    public static String packToLines(String text, int targetLines, int maxWidthPx) {
        if (text == null || text.isEmpty() || targetLines <= 0) {
            return text;
        }
        String flat = text.replace((char) 10, ' ').replaceAll(" {2,}", " ").trim();
        if (flat.isEmpty()) {
            return text;
        }
        java.util.List<String> wrapped = new java.util.ArrayList<>();
        String greedy = wrapByWidth(flat, Math.max(20, maxWidthPx));
        for (String line : greedy.split("\n", -1)) {
            if (!line.isEmpty() || wrapped.isEmpty()) {
                wrapped.add(line);
            }
        }
        while (wrapped.size() > targetLines && wrapped.size() > 1) {
            int best = 0;
            for (int i = 1; i < wrapped.size(); i++) {
                if (wrapped.get(i).length() < wrapped.get(best).length()) {
                    best = i;
                }
            }
            int take = Math.min(wrapped.get(best).length(),
                    wrapped.get(best - 1 < 0 ? 0 : best - 1).length());
            if (best > 0 && take > 1) {
                wrapped.set(best - 1, wrapped.get(best - 1) + wrapped.get(best).substring(0, take / 2));
                wrapped.set(best, wrapped.get(best).substring(take / 2));
                if (wrapped.get(best).isEmpty()) {
                    wrapped.remove(best);
                }
            } else {
                break;
            }
        }
        while (wrapped.size() < targetLines) {
            int best = 0;
            for (int i = 1; i < wrapped.size(); i++) {
                if (wrapped.get(i).length() > wrapped.get(best).length()) {
                    best = i;
                }
            }
            String line = wrapped.get(best);
            if (line.length() < 2) {
                break;
            }
            int mid = line.length() / 2;
            wrapped.set(best, line.substring(0, mid));
            wrapped.add(best + 1, line.substring(mid));
        }
        return String.join(String.valueOf((char) 10), wrapped);
    }

    private static String wrapByWidth(String text, int maxWidthPx) {
        if (hasSurrogateOrGlyph(text)) {
            return text;
        }
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null || mc.font == null) {
            return text;
        }
        var font = mc.font;
        StringBuilder out = new StringBuilder(text.length() + 16);
        String[] lines = text.split("\n", -1);
        for (int li = 0; li < lines.length; li++) {
            String line = lines[li];
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                cur.append(ch);
                if (font.width(cur.toString()) > maxWidthPx && cur.length() > 1) {
                    cur.setLength(cur.length() - 1);
                    out.append(cur).append((char) 10);
                    cur.setLength(0);
                    cur.append(ch);
                }
            }
            out.append(cur);
            if (li < lines.length - 1) {
                out.append((char) 10);
            }
        }
        return out.toString();
    }

    private static net.minecraft.network.chat.MutableComponent segmentedRebuild(
            net.minecraft.network.chat.Component c, java.util.Set<String> protect, boolean[] changed) {
        var style = c.getStyle() == null ? net.minecraft.network.chat.Style.EMPTY : c.getStyle();
        net.minecraft.network.chat.MutableComponent base;
        boolean translatedHere = false;
        if (c.getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents plain) {
            String source = plain.text();
            String fixed = translateSegments(source, protect);
            if (!fixed.equals(source)) {
                changed[0] = true;
                translatedHere = true;
            }
            base = legacy(fixed, style);
        } else {
            base = legacy(c.getString(), style);
        }
        // 翻译后含中文且不含图标时，换成带中文字形的默认字体，否则服务器自定义字体会画成方框
        if (translatedHere && containsCjk(base.getString()) && !hasPrivateGlyphs(base)) {
            base.setStyle(cjkStyle(style));
        } else {
            base.setStyle(style);
        }
        for (net.minecraft.network.chat.Component sibling : c.getSiblings()) {
            base.append(segmentedRebuild(sibling, protect, changed));
        }
        return base;
    }

    private static final java.util.List<String> OVERLAY_LINES =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private static volatile long dialogueOverlayUntil;

    /** 把译文放进屏幕浮层（多行累积，最多 4 行，9 秒后清空）。 */
    public static void setDialogueOverlay(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        synchronized (OVERLAY_LINES) {
            if (!OVERLAY_LINES.isEmpty()
                    && OVERLAY_LINES.get(OVERLAY_LINES.size() - 1).equals(text)) {
                dialogueOverlayUntil = System.currentTimeMillis() + 9_000L;
                return;
            }
            OVERLAY_LINES.add(text);
            while (OVERLAY_LINES.size() > 4) {
                OVERLAY_LINES.remove(0);
            }
        }
        dialogueOverlayUntil = System.currentTimeMillis() + 9_000L;
    }

    public static java.util.List<String> dialogueOverlayLines() {
        if (System.currentTimeMillis() > dialogueOverlayUntil) {
            synchronized (OVERLAY_LINES) {
                OVERLAY_LINES.clear();
            }
            return java.util.List.of();
        }
        synchronized (OVERLAY_LINES) {
            return new java.util.ArrayList<>(OVERLAY_LINES);
        }
    }

    public static boolean overlayModeEnabled() {
        LinguaConfig c = config;
        return c != null && c.enabled && c.translateTextDisplay;
    }

    /** 文本里是否含服务器自定义图标字形（私用区），这类多用于拼版式 HUD。 */
    public static boolean hasPrivateGlyphs(net.minecraft.network.chat.Component text) {
        if (text == null) {
            return false;
        }
        try {
            String raw = text.getString();
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (isPrivateGlyph(c)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 对话（动作栏覆盖消息）翻译：只在白名单服务器启用，默认仅 wynncraft。 */
    public static boolean dialogueEnabled() {
        LinguaConfig c = config;
        if (c == null || !c.enabled || c.compatSafeMode || !c.translateTextDisplay) {
            return false;
        }
        String allow = c.tabBossServers;
        if (allow == null || allow.isBlank()) {
            return true;
        }
        String scope = ServerScope.current();
        // 明确排除：起源领域永远不走这套对话处理
        if (scope.contains("originrealms")) {
            return false;
        }
        for (String token : allow.split(",")) {
            String t = token.trim();
            if (!t.isEmpty() && scope.contains(t)) {
                return true;
            }
        }
        return false;
    }

    /** Tab 列表 / Boss 条是否在当前服务器启用。 */
    public static boolean tabBossEnabled(boolean tabList) {
        LinguaConfig c = config;
        if (c == null || !c.enabled || c.compatSafeMode) {
            return false;
        }
        if (tabList ? !c.translateTabList : !c.translateBossBar) {
            return false;
        }
        String allow = c.tabBossServers;
        if (allow == null || allow.isBlank()) {
            return true;
        }
        String scope = ServerScope.current();
        for (String token : allow.split(",")) {
            String t = token.trim();
            if (!t.isEmpty() && scope.contains(t)) {
                return true;
            }
        }
        return false;
    }

    public static boolean itemNameEnabled() {
        LinguaConfig c = config;
        return c != null && c.enabled && c.translateItemName;
    }

    public static boolean itemLoreEnabled() {
        LinguaConfig c = config;
        return c != null && c.enabled && c.translateItemLore;
    }

    public static boolean signsEnabled() {
        LinguaConfig c = config;
        return c != null && c.enabled && c.translateSigns;
    }

    public static boolean advancementsEnabled() {
        LinguaConfig c = config;
        return c != null && c.enabled && c.translateAdvancements;
    }

    public static boolean textDisplayEnabled() {
        LinguaConfig c = config;
        return c != null && c.enabled && c.translateTextDisplay;
    }

    /** 世界内文字（木牌/浮空字/实体名）默认不替换。 */
    private static final ResultCache RESULTS = new ResultCache();

    /** 词表热更新后清掉内存结论，让新词条立刻生效。 */
    public static void invalidateCaches() {
        HIT.clear();
        SKIP.clear();
        RESULTS.clear();
    }

    /** 这一行是否已经有结论（译文或“无需翻译”），用于跳过每帧的批量预取。 */
    public static boolean cached(Component c) {
        return c == null || RESULTS.hasResult(c);
    }

    private static final java.util.Set<String> WARMED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 任何异常都退回原文：绝不让翻译 bug 影响别的模组或渲染。 */
    private static final java.util.concurrent.atomic.AtomicInteger CALLER_DIAG =
            new java.util.concurrent.atomic.AtomicInteger();

    public static Component safe(Component input) {
        if (autoTranslationSuppressed()) return input;

        try {
            Component out = translate(input);
            if (out != input && input != null && CALLER_DIAG.get() < 25) {
                String raw = input.getString().toLowerCase(java.util.Locale.ROOT);
                if (raw.contains("mayor") || raw.contains("passing through")
                        || raw.contains("howdy") || raw.contains("wagon")) {
                    CALLER_DIAG.incrementAndGet();
                    StackTraceElement[] st = Thread.currentThread().getStackTrace();
                    StringBuilder sb = new StringBuilder("[lingua] who in=["
                            + input.getString().replace((char) 10, '|') + "]");
                    for (int i = 1; i < Math.min(st.length, 7); i++) {
                        sb.append(" <- ").append(st[i].getClassName().replace("net.minecraft.", ""))
                                .append('.').append(st[i].getMethodName());
                    }
                    System.out.println(sb);
                }
            }
            out = stripLegacy(out);
            if (out != null) {
                logLeak("safe", out.getString());
            }
            return out;
        } catch (Throwable t) {
            return input;
        }
    }


    public static Component translate(Component input) {
        Component out = stripLegacy(translate0(input));
        if (out != null) {
            logLeak("translate", out.getString());
        }
        return out;
    }

    private static Component translate0(Component input) {
        if (autoTranslationSuppressed()) return input;

        LinguaConfig cfg = config;
        if (cfg == null || !cfg.enabled || cfg.compatSafeMode || input == null) {
            return input;
        }
        Component hit = ResultCache.replay(RESULTS.identity(input), input);
        if (hit != null) {
            return stripLegacy(hit);
        }
        String key = null;
        try {
            key = cacheKey(input);
        } catch (Throwable ignored) {
        }
        if (key != null) {
            Component keyHit = ResultCache.replay(RESULTS.key(key), input);
            if (keyHit != null) {
                RESULTS.put(input, key, input, keyHit);
                return stripLegacy(keyHit);
            }
        }
        try {
            java.util.Set<String> protect = NameGuard.protectedTokens(input.getString());
            boolean[] changed = new boolean[1];
            MutableComponent out = rebuild(input, protect, changed, null);
            Component result = changed[0] ? out : input;
            // 术语可能被拆在不同子节点里（“或”和“币”分属两段样式），按字符样式重建并替换
            try {
                result = termFixDeep(result);
            } catch (Throwable ignored) {
            }
            RESULTS.put(input, key, input, result);
            return stripLegacy(result);
        } catch (Throwable t) {
            return input;
        }
    }

    private static String cacheKey(Component c) {
        StringBuilder sb = new StringBuilder(64);
        sb.append(c.getString()).append('\u0000');
        net.minecraft.network.chat.Style s = c.getStyle();
        if (s != null) {
            sb.append(s.getColor() == null ? "-" : s.getColor().getValue()).append('/');
            sb.append(s.getFont() == null ? "-" : s.getFont()).append('/');
            sb.append(s.isBold()).append(s.isItalic()).append(s.isUnderlined())
                    .append(s.isStrikethrough()).append(s.isObfuscated());
        }
        return sb.toString();
    }

    /** 给 GUI 字符串用：命中译文就返回中文，否则原样返回。 */
    /** 调试用：各车道排队情况。 */
    public static String engineLaneMetrics() {
        TranslationEngine e = engine;
        return e == null ? "" : e.laneMetrics();
    }

    /** 在指定车道上执行翻译请求（对话等模块用）。 */
    public static <T> T withLane(java.util.concurrent.ExecutorService lane,
                                 java.util.function.Supplier<T> body) {
        TranslationEngine e = engine;
        if (e == null || lane == null) {
            return body.get();
        }
        java.util.concurrent.ExecutorService previous = e.currentLane();
        e.setThreadLane(lane);
        try {
            return body.get();
        } finally {
            e.setThreadLane(previous);
        }
    }

    /** 调试指标：每 30 秒打印各车道排队（debugMetrics 开启时）。 */
    private static void startLaneMetrics() {
        Thread thread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(30_000L);
                } catch (InterruptedException interrupted) {
                    return;
                }
                try {
                    LinguaConfig c = config;
                    if (c == null || !c.debugMetrics) {
                        continue;
                    }
                    TranslationEngine e = engine;
                    if (e == null) {
                        continue;
                    }
                    System.out.println("[lingua] lanes " + e.laneMetrics());
                } catch (Throwable ignored) {
                }
            }
        }, "lingua-lane-metrics");
        thread.setDaemon(true);
        thread.start();
    }

    /** 悬停提示：走 tooltip 车道。 */
    public static Component safeTooltip(Component input) {
        TranslationEngine e = engine;
        return e == null ? safe(input) : withLane(e.tooltipLane(), () -> safe(input));
    }

    /** HUD（Tab/记分板/Boss 条/动作栏）：走 hud 车道。 */
    public static Component safeHud(Component input) {
        TranslationEngine e = engine;
        return e == null ? safe(input) : withLane(e.hudLane(), () -> safe(input));
    }

    /** 世界内文本（木牌/浮空字/实体名）：走 world 车道。 */
    public static Component safeWorld(Component input) {
        TranslationEngine e = engine;
        return e == null ? safe(input) : withLane(e.worldLane(), () -> safe(input));
    }

    public static String translateString(String text) {
        String outText = translateString0(text);
        logLeak("translateString", outText);
        return outText;
    }

    private static String translateString0(String text) {
        if (autoTranslationSuppressed()) return text;

        if (text == null || text.isBlank()) {
            return text;
        }
        String translated = lookup(text, java.util.Set.of());
        return cleanTranslated(translated != null ? translated : text);
    }

    /** 木牌这类多行文本：先整块批量排队，避免一行一个请求。 */
    /** 别的模组（目前是 Wynntils）打开自己的界面时，暂停自动翻译。
     *  这些模组要靠原始英文解析服务器 GUI，读到中文会解析失败；它们自己的 UI 文本走 translateUi。 */
    /** 显式调用（其它模组通过 translateUi / translateUiComponent）时跳过“像玩家名”的启发式。 */
    private static final ThreadLocal<Boolean> EXPLICIT =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final ThreadLocal<Boolean> BYPASS =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static boolean autoTranslationSuppressed() {
        if (Boolean.TRUE.equals(BYPASS.get())) return false;
        try {
            net.minecraft.client.gui.screens.Screen screen =
                    net.minecraft.client.Minecraft.getInstance().gui.screen();
            return screen != null && screen.getClass().getName().startsWith("com.wynntils.");
        } catch (Throwable t) {
            return false;
        }
    }

    /** 别的模组显式请求翻译一个组件（保留样式）：不受“Wynntils 界面静默”影响。 */
    public static net.minecraft.network.chat.Component translateUiComponent(
            net.minecraft.network.chat.Component c) {
        if (c == null) {
            return null;
        }
        boolean previous = Boolean.TRUE.equals(BYPASS.get());
        boolean previousExplicit = Boolean.TRUE.equals(EXPLICIT.get());
        BYPASS.set(Boolean.TRUE);
        EXPLICIT.set(Boolean.TRUE);
        try {
            return stripLegacy(translate(c));
        } finally {
            BYPASS.set(previous);
            EXPLICIT.set(previousExplicit);
        }
    }

    /** 给其它客户端模组（如 Wynntils 内容手册）用的 UI 文本翻译：跳过“像玩家名”的启发式判断。
     *  命中缓存直接返回中文；未命中先返回原文并异步排队，下一帧再取回中文。 */
    public static String translateUi(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        if (text.indexOf('\u00a7') >= 0) {
            text = net.minecraft.ChatFormatting.stripFormatting(text);
        }
        TranslationEngine e = engine;
        if (e == null || !Normalizer.hasLatinLetter(text)) {
            return text;
        }
        String memo = HIT.get(text);
        if (memo != null) {
            return cleanTranslated(memo);
        }
        TranslationEngine.Result now = e.quickWithEnqueue(text.trim());
        if (now == null || now.pending() || now.text() == null || now.text().isBlank()) {
            return text;
        }
        return cleanTranslated(now.text());
    }

    public static void prefetchLines(java.util.List<String> texts) {
        TranslationEngine e = engine;
        if (e != null && texts != null && !texts.isEmpty()) {
            e.translateAll(texts);
        }
    }

    /** 悬停整块提示：先合并成一次批量请求，再逐行替换。 */
    /** 把 FormattedCharSequence 翻译后重建，样式沿用原文第一个字符的样式。 */
    /** tooltip 的行对象每帧都会重建，按对象缓存一次，避免重复切段和查表。 */
    private static final java.util.Map<net.minecraft.util.FormattedCharSequence,
            net.minecraft.util.FormattedCharSequence> SEQ_CACHE =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** 兜底：把返回给渲染器的字符序列里残留的 § 代码全部剥掉（无论它来自模型还是原文）。 */
    private static net.minecraft.util.FormattedCharSequence stripSequence(
            net.minecraft.util.FormattedCharSequence seq) {
        if (seq == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        java.util.List<net.minecraft.network.chat.Style> styles = new java.util.ArrayList<>();
        seq.accept((index, style, codepoint) -> {
            sb.appendCodePoint(codepoint);
            for (int k = 0; k < Character.charCount(codepoint); k++) {
                styles.add(style);
            }
            return true;
        });
        String s = sb.toString();
        if (s.indexOf('\u00a7') < 0) {
            return seq;
        }
        java.util.List<net.minecraft.util.FormattedCharSequence> parts = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        net.minecraft.network.chat.Style curStyle = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) {
                i++;
                continue;
            }
            net.minecraft.network.chat.Style style = styles.get(i);
            if (curStyle == null) {
                curStyle = style;
            } else if (!style.equals(curStyle)) {
                parts.add(net.minecraft.util.FormattedCharSequence.forward(cur.toString(), curStyle));
                cur.setLength(0);
                curStyle = style;
            }
            cur.append(c);
        }
        if (cur.length() > 0) {
            parts.add(net.minecraft.util.FormattedCharSequence.forward(cur.toString(), curStyle));
        }
        return parts.isEmpty()
                ? net.minecraft.util.FormattedCharSequence.EMPTY
                : net.minecraft.util.FormattedCharSequence.composite(parts);
    }

    public static net.minecraft.util.FormattedCharSequence translateSequence(
            net.minecraft.util.FormattedCharSequence seq) {
        if (seq == null) {
            return null;
        }
        net.minecraft.util.FormattedCharSequence hit = SEQ_CACHE.get(seq);
        if (hit != null) {
            return stripSequence(hit);
        }
        net.minecraft.util.FormattedCharSequence out = stripSequence(translateSequence0(seq));
        if (out != seq) {
            if (SEQ_CACHE.size() > 2048) {
                SEQ_CACHE.clear();
            }
            SEQ_CACHE.put(seq, out);
        }
        return out;
    }

    private static net.minecraft.util.FormattedCharSequence translateSequence0(
            net.minecraft.util.FormattedCharSequence seq) {
        if (seq == null) {
            return null;
        }
        java.util.List<String> texts = new java.util.ArrayList<>();
        java.util.List<net.minecraft.network.chat.Style> styles = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        net.minecraft.network.chat.Style[] curStyle = new net.minecraft.network.chat.Style[1];
        boolean[] tooLong = new boolean[1];
        seq.accept((index, style, codepoint) -> {
            if (curStyle[0] == null) {
                curStyle[0] = style;
            } else if (!curStyle[0].equals(style)) {
                texts.add(cur.toString());
                styles.add(curStyle[0]);
                cur.setLength(0);
                curStyle[0] = style;
            }
            if (cur.length() < 400) {
                cur.appendCodePoint(codepoint);
            } else {
                tooLong[0] = true;
            }
            return true;
        });
        texts.add(cur.toString());
        styles.add(curStyle[0]);
        if (tooLong[0]) {
            return seq;
        }

        net.minecraft.util.FormattedCharSequence termFixed = termFixedSequence(texts, styles);
        if (termFixed != null) {
            return termFixed;
        }

        java.util.List<net.minecraft.util.FormattedCharSequence> parts = new java.util.ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < texts.size(); i++) {
            String run = texts.get(i);
            net.minecraft.network.chat.Style style = styles.get(i);
            if (run.indexOf('\u00a7') >= 0) {
                // 关键：不能把 §7 里的 7 当成正文送去翻译，否则译文会带回 “7/F” 前缀
                run = net.minecraft.ChatFormatting.stripFormatting(run);
            }
            if (run.isEmpty()) {
                continue;
            }
            // 同一样式内部再按 ASCII / 图标切：图标段保留原字体，文字段单独翻译
            int k = 0;
            while (k < run.length()) {
                boolean ascii = run.charAt(k) <= 0x7E;
                int j = k + 1;
                while (j < run.length() && (run.charAt(j) <= 0x7E) == ascii) {
                    j++;
                }
                String piece = run.substring(k, j);
                if (ascii) {
                    String zh = translateString(piece);
                    if (!zh.equals(piece)) {
                        changed = true;
                        parts.add(net.minecraft.util.FormattedCharSequence.forward(zh, cjkFont(style)));
                        k = j;
                        continue;
                    }
                }
                String fixed = com.linguafranca.core.TermFix.apply(piece);
                if (!fixed.equals(piece)) {
                    changed = true;
                    parts.add(net.minecraft.util.FormattedCharSequence.forward(fixed, style));
                } else {
                    parts.add(net.minecraft.util.FormattedCharSequence.forward(piece, style));
                }
                k = j;
            }
        }
        if (!changed) {
            return seq;
        }
        return net.minecraft.util.FormattedCharSequence.composite(parts);
    }

    /** tooltip 的“或币”可能被拆在两个样式片段里，按字符重建并替换，保留各段样式。 */
    private static net.minecraft.util.FormattedCharSequence termFixedSequence(
            java.util.List<String> texts, java.util.List<net.minecraft.network.chat.Style> styles) {
        StringBuilder all = new StringBuilder();
        java.util.List<net.minecraft.network.chat.Style> perChar = new java.util.ArrayList<>();
        for (int k = 0; k < texts.size(); k++) {
            String chunk = texts.get(k);
            all.append(chunk);
            for (int q = 0; q < chunk.length(); q++) {
                perChar.add(styles.get(k));
            }
        }
        String joined = all.toString();
        if (joined.indexOf('或') < 0) {
            return null;
        }
        String fixed = com.linguafranca.core.TermFix.apply(joined);
        if (fixed.equals(joined)) {
            return null;
        }
        java.util.List<net.minecraft.util.FormattedCharSequence> parts = new java.util.ArrayList<>();
        int i = 0;
        while (i < joined.length()) {
            int hit = -1;
            int hitLen = 0;
            for (String term : new String[]{"或币", "或 币"}) {
                int at = joined.indexOf(term, i);
                if (at >= 0 && (hit < 0 || at < hit)) {
                    hit = at;
                    hitLen = term.length();
                }
            }
            if (hit < 0) {
                appendStyledChars(parts, joined.substring(i), perChar, i);
                break;
            }
            if (hit > i) {
                appendStyledChars(parts, joined.substring(i, hit), perChar, i);
            }
            parts.add(net.minecraft.util.FormattedCharSequence.forward("OR币", perChar.get(hit)));
            i = hit + hitLen;
        }
        return net.minecraft.util.FormattedCharSequence.composite(parts);
    }

    private static void appendStyledChars(
            java.util.List<net.minecraft.util.FormattedCharSequence> parts, String text,
            java.util.List<net.minecraft.network.chat.Style> perChar, int start) {
        int k = 0;
        while (k < text.length()) {
            net.minecraft.network.chat.Style style = perChar.get(start + k);
            int j = k + 1;
            while (j < text.length() && perChar.get(start + j).equals(style)) {
                j++;
            }
            parts.add(net.minecraft.util.FormattedCharSequence.forward(text.substring(k, j), style));
            k = j;
        }
    }

    private static net.minecraft.network.chat.Style cjkFont(net.minecraft.network.chat.Style base) {
        try {
            return base.withFont(new net.minecraft.network.chat.FontDescription.Resource(
                    net.minecraft.resources.Identifier.withDefaultNamespace("default")));
        } catch (Throwable t) {
            return base;
        }
    }

    /** 跨节点做术语替换，同时保留每个字符原本的样式（颜色/字体）。 */
    private static Component termFixDeep(Component c) {
        java.util.List<String> chunks = new java.util.ArrayList<>();
        java.util.List<net.minecraft.network.chat.Style> styles = new java.util.ArrayList<>();
        c.visit((style, text) -> {
            chunks.add(text);
            styles.add(style);
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);

        StringBuilder joinedBuilder = new StringBuilder();
        java.util.List<net.minecraft.network.chat.Style> perChar = new java.util.ArrayList<>();
        for (int k = 0; k < chunks.size(); k++) {
            String chunk = chunks.get(k);
            joinedBuilder.append(chunk);
            for (int q = 0; q < chunk.length(); q++) {
                perChar.add(styles.get(k));
            }
        }
        String joined = joinedBuilder.toString();
        String fixed = com.linguafranca.core.TermFix.apply(joined);
        if (fixed.equals(joined)) {
            return c;
        }

        MutableComponent out = Component.empty();
        int i = 0;
        while (i < joined.length()) {
            int hit = -1;
            int hitLen = 0;
            for (String term : new String[]{"或币", "或 币"}) {
                int at = joined.indexOf(term, i);
                if (at >= 0 && (hit < 0 || at < hit)) {
                    hit = at;
                    hitLen = term.length();
                }
            }
            if (hit < 0) {
                appendStyled(out, joined.substring(i), perChar, i);
                break;
            }
            if (hit > i) {
                appendStyled(out, joined.substring(i, hit), perChar, i);
            }
            out.append(Component.literal("OR币").setStyle(perChar.get(hit)));
            i = hit + hitLen;
        }
        return out;
    }

    private static void appendStyled(MutableComponent out, String text,
                                     java.util.List<net.minecraft.network.chat.Style> perChar, int start) {
        int k = 0;
        while (k < text.length()) {
            net.minecraft.network.chat.Style style = perChar.get(start + k);
            int j = k + 1;
            while (j < text.length() && perChar.get(start + j).equals(style)) {
                j++;
            }
            out.append(Component.literal(text.substring(k, j)).setStyle(style));
            k = j;
        }
    }

    private static final java.util.regex.Pattern LEGACY_CODE =
            java.util.regex.Pattern.compile("§[0-9a-fk-orxA-FK-OR]");

    /** 把字面量里的 § 颜色码解析成样式；返回 {Style, String} 片段。 */
    private static java.util.List<Object[]> splitLegacy(
            String text, net.minecraft.network.chat.Style base) {
        java.util.List<Object[]> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        net.minecraft.network.chat.Style current = base;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                net.minecraft.ChatFormatting fmt = net.minecraft.ChatFormatting.getByCode(code);
                if (fmt != null) {
                    if (cur.length() > 0) {
                        out.add(new Object[]{current, cur.toString()});
                        cur.setLength(0);
                    }
                    current = "klmno".indexOf(code) >= 0 ? current : base.withColor(fmt);
                    i++;
                    continue;
                }
            }
            cur.append(c);
        }
        if (cur.length() > 0) {
            out.add(new Object[]{current, cur.toString()});
        }
        return out;
    }

    /** 方括号里的内容像正常文字才翻译，星级/图标/编号原样保留。 */
    private static boolean isTranslatableBracket(String inside) {
        if (inside == null || inside.isBlank() || inside.length() > 60) {
            return false;
        }
        int run = 0;
        int longest = 0;
        for (int i = 0; i < inside.length(); i++) {
            char c = inside.charAt(i);
            if (c > 0x7E) {
                return false;
            }
            if (Character.isLetter(c)) {
                run++;
                longest = Math.max(longest, run);
            } else {
                run = 0;
            }
            boolean ok = Character.isLetterOrDigit(c) || c == ' '
                    || ".,:;!?'-+/%&()".indexOf(c) >= 0;
            if (!ok) {
                return false;
            }
        }
        return longest >= 2;
    }

    private static boolean appendLine(MutableComponent out, String line,
                                      net.minecraft.network.chat.Style style,
                                      java.util.Set<String> protect) {
        boolean any = false;
        StringBuilder outside = new StringBuilder();
        int i = 0;
        while (i < line.length()) {
            if (line.charAt(i) == '[') {
                int close = line.indexOf(']', i + 1);
                if (close > i) {
                    if (outside.length() > 0) {
                        any |= appendOutside(out, outside.toString(), style, protect);
                        outside.setLength(0);
                    }
                    String inside = line.substring(i + 1, close);
                    if (isTranslatableBracket(inside)) {
                        String zh = lookup(inside, protect);
                        if (zh != null && !zh.equals(inside)) {
                            out.append(Component.literal("[").setStyle(style));
                            out.append(Component.literal(zh).setStyle(cjkStyle(style)));
                            out.append(Component.literal("]").setStyle(style));
                            any = true;
                        } else {
                            out.append(Component.literal(line.substring(i, close + 1)).setStyle(style));
                        }
                    } else {
                        out.append(Component.literal(line.substring(i, close + 1)).setStyle(style));
                    }
                    i = close + 1;
                    continue;
                }
            }
            outside.append(line.charAt(i));
            i++;
        }
        if (outside.length() > 0) {
            any |= appendOutside(out, outside.toString(), style, protect);
        }
        return any;
    }

    private static boolean appendOutside(MutableComponent out, String text,
                                         net.minecraft.network.chat.Style style,
                                         java.util.Set<String> protect) {
        boolean any = false;
        for (String run : Segmenter.split(text)) {
            if (Segmenter.allAscii(run)) {
                String zh = lookup(run, protect);
                if (zh != null && !zh.equals(run)) {
                    out.append(Component.literal(zh).setStyle(cjkStyle(style)));
                    any = true;
                } else {
                    out.append(Component.literal(run).setStyle(style));
                }
            } else {
                out.append(Component.literal(run).setStyle(style));
            }
        }
        return any;
    }

    private static MutableComponent rebuild(Component c, java.util.Set<String> protect, boolean[] changed) {
        return rebuild(c, protect, changed, null);
    }

    private static MutableComponent rebuild(Component c, java.util.Set<String> protect, boolean[] changed,
                                            net.minecraft.network.chat.Style inherited) {
        // 只复制节点自己的样式，不做任何合并：父子继承交给游戏渲染器，
        // 我们绝不改写颜色/字体/格式，避免“翻译把金色变成白色”这类问题
        net.minecraft.network.chat.Style own = c.getStyle();
        if (own == null) {
            own = net.minecraft.network.chat.Style.EMPTY;
        }
        // 重建后父级可能被摊平，必须把继承来的样式（字体/颜色）合并进本节点，
        // 否则 Wynncraft 名牌里那些靠父级字体渲染的图标字形会变成空白方块
        net.minecraft.network.chat.Style effective = inherited == null
                ? own : own.applyTo(inherited);
        if (own.getClickEvent() != null) {
            // 可点击的按钮原样保留。getString() 已含 siblings，不能再 append
            return Component.literal(c.getString()).setStyle(effective);
        }
        ComponentContents contents = c.getContents();
        MutableComponent base;
        boolean translatedNode = false;
        if (contents instanceof PlainTextContents plain) {
            String source = plain.text();
            MutableComponent composed = Component.empty();
            boolean anyTranslated = false;
            // 先按换行拆行，再按 § 颜色码拆段：颜色写回样式，文字照常翻译
            String[] lines = source.split("\n", -1);
            for (int li = 0; li < lines.length; li++) {
                if (li > 0) {
                    composed.append(Component.literal("\n").setStyle(effective));
                }
                String line = lines[li];
                if (line.isEmpty()) {
                    continue;
                }
                for (Object[] seg : splitLegacy(line, effective)) {
                    anyTranslated |= appendLine(composed, (String) seg[1],
                            (net.minecraft.network.chat.Style) seg[0], protect);
                }
            }
            if (anyTranslated) {
                composed.setStyle(net.minecraft.network.chat.Style.EMPTY);
                base = composed;
                changed[0] = true;
            } else {
                base = Component.literal(source);
            }
        } else if (contents instanceof TranslatableContents translatable) {
            Object[] args = translatable.getArgs();
            Object[] mapped = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                mapped[i] = args[i] instanceof Component nested ? rebuild(nested, protect, changed, effective) : args[i];
            }
            base = Component.translatable(translatable.getKey(), mapped);
        } else {
            String source = c.getString();
            String zh = lookup(source, protect);
            if (zh != null) {
                changed[0] = true;
                translatedNode = true;
            }
            base = Component.literal(zh != null ? zh : source);
        }
        base.setStyle(translatedNode ? cjkStyle(effective) : effective);
        for (Component sibling : c.getSiblings()) {
            base.append(rebuild(sibling, protect, changed, effective));
        }
        return base;
    }

    private static final java.util.regex.Pattern URL_LIKE = java.util.regex.Pattern.compile(
            "(?i)^(?:https?://|www\\.).*|.*\\.(?:com|net|org|cn|io|gg|tv|me)(?:/|$).*|^/\\S*$");

    /** 只有命令和网址保留原文；"0/5"、"HP/MP" 这类照常翻译。 */
    private static boolean looksLikeCommandOrUrl(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String t = text.trim();
        return URL_LIKE.matcher(t).matches();
    }

    /** 命中就返回译文，没命中就排进后台队列，本次先返回 null（渲染原文）。 */
    private static String lookup(String source, java.util.Set<String> protect) {
        TranslationEngine e = engine;
        if (Boolean.TRUE.equals(PREFETCH.get()) || source == null || source.isBlank()) {
            return null;
        }
        // 源文本里若混入 § 颜色代码，先剥掉：否则模型会把 §7 里的 7 当正文翻出来
        if (source.indexOf('\u00a7') >= 0) {
            source = net.minecraft.ChatFormatting.stripFormatting(source);
        }
        if (source.isBlank()) {
            return null;
        }
        String memo = HIT.get(source);
        if (memo != null) {
            return memo;
        }
        if (SKIP.contains(source)) {
            return null;
        }
        if (e == null || !Normalizer.hasLatinLetter(source)) {
            return null;
        }
        String trimmed = source.trim();
        // 单/双字母 token（罗盘 S/NW、单位 m、快捷键）绝对不要翻
        if (trimmed.length() <= 2) {
            if (SKIP.size() < 40000) {
                SKIP.add(source);
            }
            return null;
        }
        // 只有数字/符号 + 零散单字母的 HUD 串（例如 "580 S -1670"）也不要翻：
        // 模型会把 S 当成 seconds 翻成“秒”
        int run = 0;
        int longestRun = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isLetter(trimmed.charAt(i))) {
                run++;
                longestRun = Math.max(longestRun, run);
            } else {
                run = 0;
            }
        }
        if (longestRun < 3) {
            if (SKIP.size() < 40000) {
                SKIP.add(source);
            }
            return null;
        }
        if (!Boolean.TRUE.equals(EXPLICIT.get()) && NameGuard.isOnlyName(source)
                || (NAME_TOKEN.matcher(trimmed).matches() && (trimmed.indexOf('_') >= 0 || DIGIT.matcher(trimmed).find()))
                || RANK_NAME.matcher(trimmed).matches()
                || Normalizer.isMostlyCjk(source)
                || looksLikeCommandOrUrl(source)
                || (protect != null && protect.contains(trimmed))) {
            if (SKIP.size() < 40000) {
                SKIP.add(source);
            }
            return null;
        }
        NameGuard.Masked masked = NameGuard.mask(source);
        TranslationEngine.Result now = e.quickWithEnqueue(masked.text());
        String result = now.text() == null ? null
                : com.linguafranca.core.TermFix.apply(Normalizer.sanitize(masked.restore(now.text())));
        result = cleanTranslated(result);
        if (result != null && HIT.size() < 20000) {
            HIT.put(source, result);
        }
        return result;
    }

    /** 打开容器时预取整屏物品的文本，鼠标移过去时基本已经翻好。 */
    public static void prefetchContainer(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        LinguaConfig cfg = config;
        TranslationEngine e = engine;
        if (cfg == null || e == null || !cfg.enabled || !cfg.translateTooltip || menu == null) {
            return;
        }
        java.util.List<String> batch = new java.util.ArrayList<>();
        PREFETCH.set(Boolean.TRUE);
        try {
            net.minecraft.world.entity.player.Player player = Minecraft.getInstance().player;
            for (net.minecraft.world.inventory.Slot slot : menu.slots) {
                net.minecraft.world.item.ItemStack stack = slot.getItem();
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                batch.add(stack.getHoverName().getString());
                try {
                    for (Component line : stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.EMPTY,
                            player, net.minecraft.world.item.TooltipFlag.NORMAL)) {
                        batch.add(line.getString());
                    }
                } catch (Throwable ignored) {
                }
            }
        } finally {
            PREFETCH.set(Boolean.FALSE);
        }
        e.prefetch(batch);
    }

    private static final java.util.Map<String, Long> RECENT = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile boolean reposting;

    /** 聊天：原文保留，翻好之后在下面补一行 [译]。 */
    public static void onChat(ChatComponent target, Component message) {
        LinguaConfig cfg = config;
        TranslationEngine e = engine;
        if (reposting || cfg == null || e == null || !cfg.enabled || !cfg.translateChat || message == null) {
            return;
        }
        // 服务器常把换行写成字面量 "\n"，前面还夹着画头像用的生僻字，先拆行再逐行清洗
        StringBuilder plain = new StringBuilder();
        appendWithoutButtons(message, plain);
        String full = plain.toString().replace("\\n", "\n");
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String part : full.split("\n")) {
            String cleaned = Normalizer.clean(part).trim();
            if (!cleaned.isEmpty()) {
                lines.add(cleaned);
            }
        }
        String speaker = null;
        String body;
        if (lines.size() >= 2 && lines.get(0).length() <= 20
                && !lines.get(0).matches(".*[.!?。！？:：]$")) {
            speaker = lines.get(0);
            body = String.join(" ", lines.subList(1, lines.size()));
        } else {
            body = String.join(" ", lines);
            java.util.regex.Matcher m = SPEAKER.matcher(body);
            if (m.matches()) {
                speaker = m.group(1);
                body = m.group(2);
            }
        }
        for (int i = 0; i < 3; i++) {
            String trimmed = TRAILING_UI.matcher(body).replaceAll("").trim();
            if (trimmed.equals(body)) {
                break;
            }
            body = trimmed;
        }
        int codeCount = 0;
        String original = message.getString();
        for (int i = 0; i < original.length(); i++) {
            if (original.charAt(i) == '\u00a7') {
                codeCount++;
            }
        }
        if (codeCount >= 4 && codeCount * 2 >= original.length()) {
            return; // 逐字符都被颜色码包起来的才是协议握手；普通彩色聊天不该被跳过
        }
        String raw = Normalizer.clean(body);
        if (raw.isBlank() || raw.length() > 256 || raw.startsWith("[译]") || !Normalizer.hasLatinLetter(raw)) {
            return;
        }
        if (raw.matches(".*(https?://|www\\.|\\b[\\w-]+\\.(com|net|org|cn|io|gg)\\b).*")) {
            return; // 网址/域名不翻
        }
        if (Normalizer.isMostlyCjk(raw) || looksLikeCommandOrUrl(raw)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (RECENT.size() > 512) {
            RECENT.clear();
        }
        Long last = RECENT.put(raw.toLowerCase(), now);
        if (last != null && now - last < 10_000L) {
            return;
        }
        if (CHAT_FLUSHER.compareAndSet(false, true)) {
            startChatFlusher();
        }
        if (CHAT_QUEUE.size() < 64) {
            CHAT_QUEUE.add(new PendingChat(target, raw, NameGuard.mask(raw), speaker));
        }
    }

    /** 译文必须用带中文字形的字体，否则服务器自定义字体会把中文画成方框。 */
    private static net.minecraft.network.chat.Style cjkStyle(net.minecraft.network.chat.Style style) {
        LinguaConfig cfg = config;
        if (cfg == null || !cfg.useDefaultFontForChinese || style == null) {
            return style;
        }
        // 注意：withFont(null) 只是“未指定”，渲染时仍会继承父组件的自定义字体，
        // 必须显式指定 minecraft:default，才会真的用带中文字形的那套字体
        try {
            return style.withFont(new net.minecraft.network.chat.FontDescription.Resource(
                    net.minecraft.resources.Identifier.withDefaultNamespace("default")));
        } catch (Throwable ignored) {
            return style;
        }
    }

    /** 只翻纯 ASCII 的行：任何图标、特殊符号都原样保留，避免方框和图形丢失。 */
    /** 拼接文本时跳过带点击事件的按钮节点。 */
    private static void appendWithoutButtons(Component c, StringBuilder sb) {
        if (c.getStyle().getClickEvent() != null) {
            return;
        }
        if (c.getContents() instanceof PlainTextContents plain) {
            sb.append(plain.text());
        } else if (c.getContents() instanceof TranslatableContents translatable) {
            if (translatable.getArgs().length == 0) {
                sb.append(c.getString());
            } else {
                for (Object arg : translatable.getArgs()) {
                    if (arg instanceof Component nested) {
                        appendWithoutButtons(nested, sb);
                    }
                }
            }
        } else {
            sb.append(c.getString());
        }
        for (Component sibling : c.getSiblings()) {
            appendWithoutButtons(sibling, sb);
        }
    }

    /** 每 400ms 攒一批聊天，合并成一次请求：省系统提示词，也省请求数。 */
    private static void startChatFlusher() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(400L);
                } catch (InterruptedException e) {
                    return;
                }
                java.util.List<PendingChat> batch = new java.util.ArrayList<>();
                PendingChat p;
                while (batch.size() < 20 && (p = CHAT_QUEUE.poll()) != null) {
                    batch.add(p);
                }
                if (batch.isEmpty()) {
                    continue;
                }
                try {
                    java.util.List<String> texts = new java.util.ArrayList<>(batch.size());
                    for (PendingChat pc : batch) {
                        texts.add(pc.masked().text());
                    }
                    java.util.Map<String, String> done = engine.translateMany(texts).get(15, java.util.concurrent.TimeUnit.SECONDS);
                    Minecraft mc = Minecraft.getInstance();
                    if (mc == null) {
                        continue;
                    }
                    for (PendingChat pc : batch) {
                        String zh = done.get(pc.masked().text());
                        if (zh == null) {
                            continue;
                        }
                        String out = Normalizer.sanitize(pc.masked().restore(zh))
                                .replace('\n', ' ').replaceAll("\\s+", " ").trim();
                        if (pc.speaker() != null) {
                            out = pc.speaker() + ": " + out;
                        }
                        final String finalOut = out;
                        mc.execute(() -> post(pc.target(), finalOut));
                    }
                } catch (Exception ignored) {
                }
            }
        }, "lingua-chat-batch");
        t.setDaemon(true);
        t.start();
    }

    private static void post(ChatComponent target, String zh) {
        String safe = Normalizer.sanitize(zh);
        if (safe.isBlank()) {
            return;
        }
        reposting = true;
        try {
            target.addClientSystemMessage(Component.literal(PREFIX + safe));
        } finally {
            reposting = false;
        }
    }

}
