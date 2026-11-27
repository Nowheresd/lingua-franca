package com.linguafranca.mixin;

import com.linguafranca.TextTranslator;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Tab 列表的标题/页脚。 */
@Mixin(PlayerTabOverlay.class)
public abstract class TabListMixin {
    @ModifyVariable(method = "setHeader", at = @At("HEAD"), argsOnly = true, require = 0)
    private Component lingua$header(Component text) {
        return TextTranslator.tabBossEnabled(true) ? TextTranslator.safeHud(text) : text;
    }

    @ModifyVariable(method = "setFooter", at = @At("HEAD"), argsOnly = true, require = 0)
    private Component lingua$footer(Component text) {
        return TextTranslator.tabBossEnabled(true) ? TextTranslator.safeHud(text) : text;
    }
}
