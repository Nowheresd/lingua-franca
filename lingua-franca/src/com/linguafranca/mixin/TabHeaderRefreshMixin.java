package com.linguafranca.mixin;

import com.linguafranca.TextTranslator;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tab 列表标题/页脚在收到数据包时只设置一次，若那时译文还没回来就永远是英文。
 * 这里在每次绘制前重新取一次（命中缓存时零开销）。
 */
@Mixin(PlayerTabOverlay.class)
public abstract class TabHeaderRefreshMixin {
    private static java.lang.reflect.Field HEADER;
    private static java.lang.reflect.Field FOOTER;
    private static boolean FAILED;

    @Inject(method = "extractRenderState", at = @At("HEAD"), require = 0)
    private void lingua$refresh(net.minecraft.client.gui.GuiGraphicsExtractor gfx, int width,
                                net.minecraft.world.scores.Scoreboard scoreboard,
                                net.minecraft.world.scores.Objective objective,
                                CallbackInfo ci) {
        if (FAILED || !TextTranslator.tabBossEnabled(true)) {
            return;
        }
        try {
            PlayerTabOverlay self = (PlayerTabOverlay) (Object) this;
            if (HEADER == null) {
                HEADER = PlayerTabOverlay.class.getDeclaredField("header");
                HEADER.setAccessible(true);
                FOOTER = PlayerTabOverlay.class.getDeclaredField("footer");
                FOOTER.setAccessible(true);
            }
            Component header = (Component) HEADER.get(self);
            if (header != null) {
                Component fixed = TextTranslator.safeHud(header);
                if (fixed != header) {
                    HEADER.set(self, fixed);
                }
            }
            Component footer = (Component) FOOTER.get(self);
            if (footer != null) {
                Component fixed = TextTranslator.safeHud(footer);
                if (fixed != footer) {
                    FOOTER.set(self, fixed);
                }
            }
        } catch (Throwable t) {
            FAILED = true;
        }
    }
}
