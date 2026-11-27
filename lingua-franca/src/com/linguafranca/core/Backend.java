package com.linguafranca.core;

/** 机器翻译后端。返回 null 表示这条没翻出来（网络失败、限流、原文未变等）。 */
public interface Backend {
    String id();

    String translate(String english) throws Exception;

    /** 中文 -> 英文（发消息用）；不支持时返回 null。 */
    default String translateToEnglish(String chinese) throws Exception {
        return null;
    }

    /** 批量翻译；不支持时返回 null，调用方退回逐条。 */
    default java.util.Map<String, String> translateBatch(java.util.List<String> texts) throws Exception {
        return null;
    }
}
