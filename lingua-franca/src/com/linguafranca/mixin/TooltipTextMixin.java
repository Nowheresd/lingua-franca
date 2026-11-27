package com.linguafranca.mixin;

import com.linguafranca.TextTranslator;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 tooltip 的“文本行 → 客户端组件”这一步翻译。
 * 此时布局还没开始，宽度会按译文重新计算；也不碰调用方传进来的列表，
 * 所以不会和 AppleSkin 之类的 tooltip 附加组件打架。
 */
@Mixin(ClientTooltipComponent.class)
public interface TooltipTextMixin {
    @Inject(method = "create(Lnet/minecraft/util/FormattedCharSequence;)Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private static void lingua$create(FormattedCharSequence seq, CallbackInfoReturnable<ClientTooltipComponent> cir) {
        if (!TextTranslator.itemLoreEnabled() || seq == null) {
            return;
        }
        FormattedCharSequence translated = TextTranslator.translateSequence(seq);
        if (translated != seq) {
            cir.setReturnValue(new ClientTextTooltip(translated));
        }
    }
}
