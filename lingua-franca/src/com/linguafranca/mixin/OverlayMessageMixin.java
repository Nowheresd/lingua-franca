package com.linguafranca.mixin;

import com.linguafranca.TextTranslator;
import com.linguafranca.dialogue.DialogueFonts;
import com.linguafranca.dialogue.WynnDialogueTranslator;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Hud.class)
public abstract class OverlayMessageMixin {
    @ModifyVariable(
            method = "setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private Component lingua$overlay(Component message) {
        if (message == null || !TextTranslator.dialogueEnabled()) {
            return message;
        }
        try {
            if (!DialogueFonts.hasDialogueFont(message)) {
                return message;
            }
            return WynnDialogueTranslator.transform(message);
        } catch (Throwable t) {
            return message;
        }
    }
}
