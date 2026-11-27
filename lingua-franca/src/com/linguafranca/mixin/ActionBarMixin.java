package com.linguafranca.mixin;

import com.linguafranca.TextTranslator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 动作栏文本。 */
@Mixin(net.minecraft.client.multiplayer.ClientPacketListener.class)
public abstract class ActionBarMixin {
    @ModifyVariable(method = "setActionBarText", at = @At("HEAD"), argsOnly = true, require = 0)
    private ClientboundSetActionBarTextPacket lingua$actionBar(ClientboundSetActionBarTextPacket packet) {
        if (packet == null || !TextTranslator.tabBossEnabled(true)) {
            return packet;
        }
        try {
            Component original = packet.text();
            Component translated = TextTranslator.safeHud(original);
            if (translated != original) {
                return new ClientboundSetActionBarTextPacket(translated);
            }
        } catch (Throwable ignored) {
        }
        return packet;
    }
}
