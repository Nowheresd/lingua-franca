package com.linguafranca.text;

import java.util.ArrayList;
import java.util.List;

/** 把一行文本切成“可翻译的文字段”和“需要原样保留的图标段”。 */
public final class Segmenter {
    private Segmenter() {
    }

    public static boolean isAscii(char c) {
        return c <= 0x7E;
    }

    public static boolean allAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!isAscii(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** 按 ASCII / 非 ASCII 交替切段，顺序保持不变。 */
    public static List<String> split(String s) {
        List<String> out = new ArrayList<>(4);
        int i = 0;
        while (i < s.length()) {
            boolean ascii = isAscii(s.charAt(i));
            int j = i + 1;
            while (j < s.length() && isAscii(s.charAt(j)) == ascii) {
                j++;
            }
            out.add(s.substring(i, j));
            i = j;
        }
        return out;
    }
}
