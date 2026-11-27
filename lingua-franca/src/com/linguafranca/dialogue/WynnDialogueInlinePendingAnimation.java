package com.linguafranca.dialogue;

import net.minecraft.network.chat.Style;

/** 移植自参考实现：标记“待动画”的样式，我们不做动画，只保留标记语义。 */
public final class WynnDialogueInlinePendingAnimation {
    private static final String INSERTION = "linguafranca:wynn_dialogue_inline_pending_animation";

    private WynnDialogueInlinePendingAnimation() {
    }

    static Style mark(Style style) {
        return (style == null ? Style.EMPTY : style).withInsertion(INSERTION);
    }

    static boolean isMarked(Style style) {
        return style != null && INSERTION.equals(style.getInsertion());
    }
}
