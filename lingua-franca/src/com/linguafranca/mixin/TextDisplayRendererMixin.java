package com.linguafranca.mixin;

import com.linguafranca.TextTranslator;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 浮空字：翻译文本并重建绘制缓存（cachedInfo）。
 * 这是 5.5.2 的行为：就地翻译，不做浮层、不做防抖。
 */
@Mixin(targets = "net.minecraft.client.renderer.entity.DisplayRenderer$TextDisplayRenderer")
public abstract class TextDisplayRendererMixin {
    private static java.lang.reflect.Method SPLIT;
    private static boolean SPLIT_FAILED;

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Display$TextDisplay;Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;F)V",
            at = @At("TAIL"), require = 0)
    private void lingua$afterExtract(net.minecraft.world.entity.Display.TextDisplay entity,
                                     net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState state,
                                     float partial,
                                     CallbackInfo ci) {
        if (!TextTranslator.textDisplayEnabled() || state == null || state.textRenderState == null) {
            return;
        }
        try {
            Component current = state.textRenderState.text();
            Component zh = TextTranslator.safeWorld(current);
            if (zh == current) {
                return;
            }
            var old = state.textRenderState;
            state.textRenderState = new net.minecraft.world.entity.Display.TextDisplay.TextRenderState(
                    zh, old.lineWidth(), old.textOpacity(), old.backgroundColor(), old.flags());
            rebuildCachedInfo(state);
        } catch (Throwable ignored) {
        }
    }

    private void rebuildCachedInfo(
            net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState state) {
        if (SPLIT_FAILED) {
            return;
        }
        try {
            if (SPLIT == null) {
                java.lang.reflect.Method m = getClass().getDeclaredMethod(
                        "splitLines", Component.class, int.class);
                m.setAccessible(true);
                SPLIT = m;
            }
            Object info = SPLIT.invoke(this, state.textRenderState.text(),
                    state.textRenderState.lineWidth());
            state.cachedInfo = (net.minecraft.world.entity.Display.TextDisplay.CachedInfo) info;
        } catch (Throwable t) {
            SPLIT_FAILED = true;
        }
    }
}
