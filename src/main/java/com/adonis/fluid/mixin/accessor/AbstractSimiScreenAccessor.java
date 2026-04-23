package com.adonis.fluid.mixin.accessor;

import net.createmod.catnip.gui.AbstractSimiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractSimiScreen.class)
public interface AbstractSimiScreenAccessor {

	@Accessor(value = "guiLeft", remap = false)
	int fluid$getGuiLeft();

	@Accessor(value = "guiTop", remap = false)
	int fluid$getGuiTop();
}
