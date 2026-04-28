package com.adonis.fluid.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

public final class FluidTooltipHelper {

	private FluidTooltipHelper() {}

	public static List<Component> getTooltipLines(FluidStack fluid) {
		return getTooltipLines(fluid, fluid.getAmount());
	}

	public static List<Component> getTooltipLines(FluidStack fluid, int amountMb) {
		if (fluid.isEmpty()) {
			return List.of();
		}
		return List.of(Component.literal(FluidAmountHelper.formatWithUnit(amountMb) + " ")
			.append(fluid.getHoverName().copy()));
	}

	public static void renderTooltip(GuiGraphics graphics, Font font, FluidStack fluid, int x, int y) {
		renderTooltip(graphics, font, fluid, fluid.getAmount(), x, y);
	}

	public static void renderTooltip(GuiGraphics graphics, Font font, FluidStack fluid, int amountMb, int x, int y) {
		if (fluid.isEmpty()) {
			return;
		}
		graphics.renderComponentTooltip(font, getTooltipLines(fluid, amountMb), x, y);
	}
}
