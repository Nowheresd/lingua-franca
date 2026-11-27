package com.linguafranca.mixin;

import com.linguafranca.TextTranslator;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Boss 条名称（部分服务器用它显示事件与提示）。 */
@Mixin(BossEvent.class)
public abstract class BossBarMixin {
    @Inject(method = "getName", at = @At("RETURN"), cancellable = true, require = 0)
    private void lingua$name(CallbackInfoReturnable<Component> cir) {
        if (!TextTranslator.tabBossEnabled(false)) {
            return;
        }
        Component original = cir.getReturnValue();
        Component translated = TextTranslator.safeHud(original);
        if (translated != original) {
            cir.setReturnValue(translated);
        }
    }
}
