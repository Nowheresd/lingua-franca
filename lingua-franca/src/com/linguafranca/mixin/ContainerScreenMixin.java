package com.linguafranca.mixin;

import com.linguafranca.TextTranslator;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 容器界面只做一件事：打开时预取整屏物品文本。
 * 标题不再翻译——服务器标题常带自定义字形，替换后会出现方框。
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void lingua$prefetch(CallbackInfo ci) {
        try {
            AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
            TextTranslator.prefetchContainer(self.getMenu());
        } catch (Throwable ignored) {
        }
    }
}
