package com.adonis.fluid.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

public final class FluidTooltipHelper {

	private FluidTooltipHelper() {}

	public static List<Component> getTooltipLines(FluidStack fluid) {
		if (fluid.isEmpty()) {
			return List.of();
		}
		return List.of(fluid.getHoverName().copy());
	}

	public static void renderTooltip(GuiGraphics graphics, Font font, FluidStack fluid, int x, int y) {
		if (fluid.isEmpty()) {
			return;
		}
		graphics.renderComponentTooltip(font, getTooltipLines(fluid), x, y);
	}
}
