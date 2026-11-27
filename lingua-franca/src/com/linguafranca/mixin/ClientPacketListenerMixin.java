package com.linguafranca.mixin;

import com.linguafranca.OutboundChat;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void lingua$sendChat(String message, CallbackInfo ci) {
        if (OutboundChat.handle((ClientPacketListener) (Object) this, message)) {
            ci.cancel();
        }
    }
}
