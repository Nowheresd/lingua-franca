package com.linguafranca.mixin;

import com.linguafranca.TextTranslator;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 右侧计分板每行的名字。
 * 不去改构造器参数（那里有两个 Component 会歧义），而是等行对象生成后，
 * 反射造一个名字已翻译的新对象替换。
 */
@Mixin(Hud.class)
public abstract class SidebarEntryFixMixin {
    private static final java.util.concurrent.atomic.AtomicInteger DIAG =
            new java.util.concurrent.atomic.AtomicInteger();
    private static java.lang.reflect.Constructor<?> ENTRY_CTOR;
    private static boolean CTOR_FAILED;

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), require = 0)
    private void lingua$prefetch(net.minecraft.client.gui.GuiGraphicsExtractor gfx,
                                net.minecraft.world.scores.Objective objective,
                                org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!TextTranslator.tabBossEnabled(true) || objective == null) {
            return;
        }
        try {
            java.util.List<String> lines = new java.util.ArrayList<>();
            for (PlayerScoreEntry e : objective.getScoreboard().listPlayerScores(objective)) {
                String text = e.ownerName().getString();
                if (!text.isBlank()) {
                    lines.add(text);
                }
            }
            if (!lines.isEmpty()) {
                TextTranslator.prefetchLines(lines);
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "lambda$displayScoreboardSidebar$1", at = @At("RETURN"),
            cancellable = true, require = 0)
    private void lingua$row(Scoreboard scoreboard, NumberFormat format,
                            PlayerScoreEntry entry,
                            CallbackInfoReturnable<Object> cir) {
        if (!TextTranslator.tabBossEnabled(true) || CTOR_FAILED) {
            return;
        }
        try {
            Object row = cir.getReturnValue();
            if (row == null) {
                return;
            }
            Component name = (Component) row.getClass().getMethod("name").invoke(row);
            Component fixed = TextTranslator.safeHud(name);
            if (fixed == name) {
                return;
            }
            if (ENTRY_CTOR == null) {
                java.lang.reflect.Constructor<?> c = row.getClass().getDeclaredConstructor(
                        Component.class, Component.class, int.class);
                c.setAccessible(true);
                ENTRY_CTOR = c;
            }
            Component score = (Component) row.getClass().getMethod("score").invoke(row);
            int width = (int) row.getClass().getMethod("scoreWidth").invoke(row);
            cir.setReturnValue(ENTRY_CTOR.newInstance(fixed, score, width));
        } catch (Throwable t) {
            CTOR_FAILED = true;
        }
    }
}
