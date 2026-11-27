package com.linguafranca.mixin;

import com.linguafranca.TextTranslator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 木牌四行文字。 */
@Mixin(SignText.class)
public abstract class SignTextMixin {
    @Inject(method = "getMessages", at = @At("RETURN"), cancellable = true, require = 0)
    private void lingua$messages(boolean front, CallbackInfoReturnable<List<Component>> cir) {
        if (!TextTranslator.signsEnabled()) {
            return;
        }
        List<Component> lines = cir.getReturnValue();
        if (lines == null || lines.isEmpty()) {
            return;
        }
        // 全部行都已有结论时，跳过每帧的字符串收集和批量预取
        boolean allCached = true;
        for (Component line : lines) {
            if (!TextTranslator.cached(line)) {
                allCached = false;
                break;
            }
        }
        if (!allCached) {
            List<String> texts = new ArrayList<>(lines.size());
            for (Component line : lines) {
                if (line != null) {
                    texts.add(line.getString());
                }
            }
            com.linguafranca.TextTranslator.prefetchLines(texts);
        }
        // 只在真的发生变化时才复制列表，避免每帧分配
        List<Component> out = null;
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            Component mapped = line == null ? null : TextTranslator.safeWorld(line);
            if (mapped != line) {
                if (out == null) {
                    out = new ArrayList<>(lines);
                }
                out.set(i, mapped);
            }
        }
        if (out != null) {
            cir.setReturnValue(out);
        }
    }
}
