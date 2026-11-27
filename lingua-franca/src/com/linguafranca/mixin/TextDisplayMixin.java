package com.linguafranca.mixin;

import com.linguafranca.TextTranslator;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 浮空字的文本（名字、告示等）。 */
@Mixin(Display.TextDisplay.class)
public abstract class TextDisplayMixin {
    @Inject(method = "getText", at = @At("RETURN"), cancellable = true, require = 0)
    private void lingua$text(CallbackInfoReturnable<Component> cir) {
        if (!TextTranslator.textDisplayEnabled()) {
            return;
        }
        Component original = cir.getReturnValue();
        Component translated = TextTranslator.safeWorld(original);
        if (translated != original) {
            cir.setReturnValue(translated);
        }
    }
}
