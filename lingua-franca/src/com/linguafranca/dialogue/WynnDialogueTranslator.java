package com.linguafranca.dialogue;

import com.linguafranca.TextTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;

/** 把 Wynncraft 对话模板翻成中文，再交给移植过来的 InlinePresenter 重建组件。 */
public final class WynnDialogueTranslator {
    private WynnDialogueTranslator() {
    }

    public static Component transform(Component original) {
        if (original == null) {
            return null;
        }
        WynnDialogueTextTemplate template = WynnDialogueTextTemplateParser.parse(original);
        if (template == null) {
            return original;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) {
            return original;
        }
        try {
            String npc = translate(template.npcName(), template.nameplate() == null
                    ? Style.EMPTY : template.nameplate().style());
            String dialogue = translate(template.dialogue(), template.bodyLines().isEmpty()
                    ? Style.EMPTY : template.bodyLines().get(0).style());
            List<WynnDialoguePresentation.OptionPresentation> options = new ArrayList<>();
            for (WynnDialogueTextTemplate.ChoiceSlot choice : template.choices()) {
                String text = translate(choice.readableText(), choice.style());
                options.add(new WynnDialoguePresentation.OptionPresentation(
                        choice.index(), choice.readableText(), text, false, ""));
            }
            WynnDialoguePresentation presentation = new WynnDialoguePresentation(
                    0L, "", template.npcName(), npc, template.dialogue(), dialogue,
                    options, false, false, "", List.of(), "");
            return new WynnDialogueInlinePresenter(mc.font).render(template, presentation);
        } catch (Throwable t) {
            return original;
        }
    }

    private static String translate(String text, Style style) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        try {
            Component source = Component.literal(text).setStyle(style == null ? Style.EMPTY : style);
            Component result = TextTranslator.safe(source);
            return result == null ? text : result.getString();
        } catch (Throwable t) {
            return text;
        }
    }
}
