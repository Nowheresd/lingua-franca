package com.linguafranca.mixin;

import com.linguafranca.gui.LinguaConfigScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 按 J 打开翻译设置。
 * 键码取的是 KeyEvent#keycode()（字母键这里等于小写 ASCII），
 * 并且只在没有打开任何界面时生效，避免聊天里打 j 被误触发。
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyOpenMixin {
    private static final int J = 106;
    private static java.lang.reflect.Field SCREEN_FIELD;
    private static boolean SCREEN_FAILED;
    private static long lastOpen;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true, require = 0)
    private void lingua$key(long window, int action, net.minecraft.client.input.KeyEvent event, CallbackInfo ci) {
        if (event == null) {
            return;
        }
        int code = event.keycode();
        if (code != J && code != 74) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastOpen < 400L || screenOpen()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            lastOpen = now;
            mc.setScreenAndShow(new LinguaConfigScreen());
            ci.cancel();
        }
    }

    private static boolean screenOpen() {
        if (SCREEN_FAILED) {
            return false;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return false;
            }
            if (SCREEN_FIELD == null) {
                for (Class<?> c = mc.getClass(); c != null; c = c.getSuperclass()) {
                    for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                        if (net.minecraft.client.gui.screens.Screen.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            SCREEN_FIELD = f;
                            break;
                        }
                    }
                    if (SCREEN_FIELD != null) {
                        break;
                    }
                }
            }
            return SCREEN_FIELD != null && SCREEN_FIELD.get(mc) != null;
        } catch (Throwable t) {
            SCREEN_FAILED = true;
            return false;
        }
    }
}
