package com.adonis.fluid.mixin.accessor;

import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(StockKeeperRequestScreen.class)
public interface StockKeeperRequestScreenAccessor {

	@Invoker(value = "drawItemCount", remap = false)
	void callDrawItemCount(GuiGraphics graphics, int count, int customCount);
}
