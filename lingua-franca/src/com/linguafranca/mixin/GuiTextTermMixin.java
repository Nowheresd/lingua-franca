package com.linguafranca.mixin;

import com.linguafranca.TextTranslator;
import com.linguafranca.core.TermFix;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 自定义界面（商店等）自己画文字，不走 tooltip 管线。
 * 这里只处理已知错译术语（例如“或币”），不做整句翻译，避免布局错乱。
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiTextTermMixin {
    @ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private String lingua$textStr(String text) {
        return TermFix.guard(text);
    }

    @ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private String lingua$textStrShadow(String text) {
        return TermFix.guard(text);
    }

    @ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private Component lingua$textComp(Component text) {
        return TextTranslator.guard(text);
    }

    @ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private Component lingua$textCompShadow(Component text) {
        return TextTranslator.guard(text);
    }

    @ModifyVariable(method = "centeredText(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private String lingua$centeredStr(String text) {
        return TermFix.guard(text);
    }

    @ModifyVariable(method = "centeredText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private Component lingua$centeredComp(Component text) {
        return TextTranslator.guard(text);
    }

    @ModifyVariable(method = "textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private Component lingua$backdropComp(Component text) {
        return TextTranslator.guard(text);
    }

    @ModifyVariable(method = "setTooltipForNextFrame(Lnet/minecraft/network/chat/Component;II)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private Component lingua$tip1(Component text) {
        return TextTranslator.guard(text);
    }

    @ModifyVariable(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;II)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private Component lingua$tip2(Component text) {
        return TextTranslator.guard(text);
    }

    @ModifyVariable(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IILnet/minecraft/resources/Identifier;)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private Component lingua$tip3(Component text) {
        return TextTranslator.guard(text);
    }

    @ModifyVariable(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;II)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private java.util.List<Component> lingua$tipList(java.util.List<Component> lines) {
        return TextTranslator.guardList(lines);
    }

    @ModifyVariable(method = "setComponentTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private java.util.List<Component> lingua$tipList2(java.util.List<Component> lines) {
        return TextTranslator.guardList(lines);
    }

    @ModifyVariable(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private java.util.List<Component> lingua$tipList3(java.util.List<Component> lines) {
        return TextTranslator.guardList(lines);
    }

    @ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private net.minecraft.util.FormattedCharSequence lingua$textFcs(net.minecraft.util.FormattedCharSequence text) {
        return TextTranslator.guardSequence(text);
    }

    @ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private net.minecraft.util.FormattedCharSequence lingua$textFcsShadow(net.minecraft.util.FormattedCharSequence text) {
        return TextTranslator.guardSequence(text);
    }

    @ModifyVariable(method = "centeredText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private net.minecraft.util.FormattedCharSequence lingua$centeredFcs(net.minecraft.util.FormattedCharSequence text) {
        return TextTranslator.guardSequence(text);
    }
}
