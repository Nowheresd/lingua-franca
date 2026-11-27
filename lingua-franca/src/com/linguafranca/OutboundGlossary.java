package com.linguafranca;

import java.util.LinkedHashMap;
import java.util.Map;

/** 高频整句直接查表，发消息零延迟、零 token。 */
public final class OutboundGlossary {
    private static final Map<String, String> MAP = new LinkedHashMap<>();

    static {
        MAP.put("你好", "hello");
        MAP.put("大家好", "hello everyone");
        MAP.put("谢谢", "thanks");
        MAP.put("多谢", "thanks a lot");
        MAP.put("再见", "bye");
        MAP.put("有人吗", "anyone here?");
        MAP.put("在吗", "you there?");
        MAP.put("多少钱", "how much?");
        MAP.put("怎么卖", "how much?");
        MAP.put("组队吗", "wanna team?");
        MAP.put("等我一下", "wait a sec");
        MAP.put("马上来", "coming");
        MAP.put("好的", "ok");
        MAP.put("可以", "ok");
        MAP.put("不行", "no");
        MAP.put("为什么", "why?");
        MAP.put("在哪里", "where?");
        MAP.put("帮我一下", "can you help me?");
        MAP.put("收吗", "buying?");
        MAP.put("卖吗", "selling?");
        MAP.put("换吗", "trade?");
        MAP.put("我爱你", "love you");
        MAP.put("笑死", "lol");
        MAP.put("牛逼", "nice");
    }

    private OutboundGlossary() {
    }

    public static String lookup(String text) {
        return MAP.get(text.trim());
    }
}
