package com.linguafranca.mixin;

import com.linguafranca.TextTranslator;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tab 列表里每个条目的显示名。Wynncraft 这类服务器会用“假玩家”条目当分区标题
 * （Friends / Global / Party / Guild），所以这里也要翻。
 */
@Mixin(PlayerInfo.class)
public abstract class TabPlayerNameMixin {
    @ModifyVariable(method = "setTabListDisplayName", at = @At("HEAD"), argsOnly = true, require = 0)
    private Component lingua$set(Component name) {
        return TextTranslator.tabBossEnabled(true) ? TextTranslator.safeHud(name) : name;
    }

    @Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private void lingua$get(CallbackInfoReturnable<Component> cir) {
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
