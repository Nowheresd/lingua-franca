package com.linguafranca.mixin;

import com.linguafranca.TextTranslator;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 只翻物品名。Lore 在 tooltip 客户端组件那一层翻（TooltipTextMixin），不碰列表。 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true, require = 0)
    private void lingua$hoverName(CallbackInfoReturnable<Component> cir) {
        if (TextTranslator.autoTranslationSuppressed() || !TextTranslator.itemNameEnabled()) {
            return;
        }
        Component original = cir.getReturnValue();
        Component translated = TextTranslator.safeTooltip(original);
        if (translated != original) {
            cir.setReturnValue(translated);
        }
    }
}
