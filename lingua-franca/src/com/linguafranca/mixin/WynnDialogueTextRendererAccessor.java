package com.linguafranca.mixin;

import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Font.class)
public interface WynnDialogueTextRendererAccessor {
    @Accessor("provider")
    Font.Provider lingua$getGlyphsProvider();
}
