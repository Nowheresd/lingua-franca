package com.linguafranca.mixin;

import com.linguafranca.TextTranslator;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 实体头顶的名字（盔甲架、命名牌、NPC 名牌）。 */
@Mixin(Entity.class)
public abstract class EntityNameMixin {
    private static final AtomicInteger DIAG = new AtomicInteger();
    private static final AtomicInteger LOBBY = new AtomicInteger();

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private void lingua$displayName(CallbackInfoReturnable<Component> cir) {
        Component original = cir.getReturnValue();
        if (original != null && LOBBY.get() < 30) {
            String low = original.getString().toLowerCase(java.util.Locale.ROOT);
            if (low.contains("click") || low.contains("press") || low.contains("travel")
                    || low.contains("cycle") || low.contains("hold")) {
                LOBBY.incrementAndGet();
                int id = ((net.minecraft.world.entity.Entity) (Object) this).getId();
                System.out.println("[lingua] lobby-entity id=" + id
                        + " text=[" + original.getString().replace((char) 10, '|') + "]");
            }
        }
        if (original != null && DIAG.get() < 8) {
            String raw = original.getString();
            if (raw.toLowerCase().contains("wagon")) {
                DIAG.incrementAndGet();
                StringBuilder cp = new StringBuilder();
                for (int i = 0; i < raw.length() && i < 80; i++) {
                    char c = raw.charAt(i);
                    cp.append(c > 0x7E ? String.format("U+%04X", (int) c) : String.valueOf(c));
                }
                System.out.println("[lingua] npcname=[" + raw.replace('\n', '|') + "] cp=[" + cp + "]");
            }
        }
        Component translated = TextTranslator.safeWorld(original);
        if (translated != original) {
            cir.setReturnValue(translated);
        }
    }

    @Inject(method = "getName", at = @At("RETURN"), cancellable = true, require = 0)
    private void lingua$name(CallbackInfoReturnable<Component> cir) {
        Component original = cir.getReturnValue();
        Component translated = TextTranslator.safeWorld(original);
        if (translated != original) {
            cir.setReturnValue(translated);
        }
    }
}
