package com.linguafranca.mixin;

import com.linguafranca.TextTranslator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 浮空字一出现就提前请求译文，减少“先看到英文、过一会儿才变中文”的等待。 */
@Mixin(Display.TextDisplay.class)
public abstract class TextDisplayPrefetchMixin {
    @Inject(method = "setText", at = @At("TAIL"), require = 0)
    private void lingua$prefetch(Component text, CallbackInfo ci) {
        if (text == null || !TextTranslator.textDisplayEnabled()) {
            return;
        }
        try {
            List<String> lines = new ArrayList<>();
            for (String line : text.getString().split("\n", -1)) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
            if (!lines.isEmpty()) {
                TextTranslator.prefetchLines(lines);
            }
        } catch (Throwable ignored) {
        }
    }
}
