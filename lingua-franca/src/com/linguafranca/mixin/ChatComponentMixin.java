package com.linguafranca.mixin;

import com.linguafranca.TextTranslator;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Inject(method = "addServerSystemMessage", at = @At("HEAD"))
    private void lingua$serverMessage(Component message, CallbackInfo ci) {
        TextTranslator.onChat((ChatComponent) (Object) this, message);
    }

    @Inject(method = "addClientSystemMessage", at = @At("HEAD"))
    private void lingua$clientMessage(Component message, CallbackInfo ci) {
        TextTranslator.onChat((ChatComponent) (Object) this, message);
    }

    @Inject(method = "addPlayerMessage", at = @At("HEAD"))
    private void lingua$playerMessage(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        TextTranslator.onChat((ChatComponent) (Object) this, message);
    }
}
