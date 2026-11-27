package com.linguafranca.dialogue;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;

import java.util.Optional;

/** 判断 Wynncraft 对话字体（minecraft:hud/dialogue/text/...）。 */
public final class DialogueFonts {
    private DialogueFonts() {
    }

    public static boolean isDialogueFont(FontDescription source) {
        if (!(source instanceof FontDescription.Resource font)) {
            return false;
        }
        return font.id().getPath().startsWith("hud/dialogue/text/");
    }

    public static boolean hasDialogueFont(Component component) {
        if (component == null) {
            return false;
        }
        final boolean[] found = {false};
        component.visit((style, text) -> {
            if (style instanceof Style s && isDialogueFont(s.getFont())) {
                found[0] = true;
            }
            return found[0] ? Optional.of(true) : Optional.<Boolean>empty();
        }, Style.EMPTY);
        return found[0];
    }
}
