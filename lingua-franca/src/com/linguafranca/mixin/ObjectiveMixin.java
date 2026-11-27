package com.linguafranca.mixin;

import com.linguafranca.TextTranslator;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 计分板标题（Tab 列表里显示为 "Objectives:" 之类）。 */
@Mixin(Objective.class)
public abstract class ObjectiveMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private void lingua$title(CallbackInfoReturnable<Component> cir) {
        if (!TextTranslator.tabBossEnabled(true)) {
            return;
        }
        Component original = cir.getReturnValue();
        Component translated = TextTranslator.safeHud(original);
        if (translated != original) {
            cir.setReturnValue(translated);
        }
    }
}
