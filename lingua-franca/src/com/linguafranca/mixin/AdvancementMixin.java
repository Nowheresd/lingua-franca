package com.linguafranca.mixin;

import com.linguafranca.TextTranslator;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 只翻成就标题（“目标已达成！”），描述保持服务器原文。 */
@Mixin(DisplayInfo.class)
public abstract class AdvancementMixin {
    @Inject(method = "title", at = @At("RETURN"), cancellable = true, require = 0)
    private void lingua$title(CallbackInfoReturnable<Component> cir) {
        if (!TextTranslator.advancementsEnabled()) {
            return;
        }
        Component original = cir.getReturnValue();
        Component translated = TextTranslator.safe(original);
        if (translated != original) {
            cir.setReturnValue(translated);
        }
    }
}
