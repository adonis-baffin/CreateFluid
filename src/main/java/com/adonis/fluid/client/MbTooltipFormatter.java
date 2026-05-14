package com.adonis.fluid.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.fluids.FluidStack;

public final class MbTooltipFormatter {

	private MbTooltipFormatter() {}

	public static void draw(GuiGraphics graphics, Font font, FluidStack fluid, int x, int y) {
		draw(graphics, font, fluid, fluid.getAmount(), x, y);
	}

	public static void draw(GuiGraphics graphics, Font font, FluidStack fluid, int amountMb, int x, int y) {
		if (!fluid.isEmpty()) {
			graphics.renderComponentTooltip(font, java.util.List.of(formatLine(fluid, amountMb)), x, y);
		}
	}

	public static Component formatLine(FluidStack fluid, int amountMb) {
		MutableComponent result = Component.literal(FluidAmountHelper.formatWithUnit(amountMb) + " ");
		result.append(fluid.getHoverName().copy());
		return result;
	}

	public static Component formatLineWithCapacity(FluidStack fluid, int amountMb, int capacityMb) {
		MutableComponent result = Component.literal(FluidAmountHelper.formatWithUnit(amountMb));
		result.append(" / ").append(FluidAmountHelper.formatWithUnit(capacityMb)).append(" ");
		result.append(fluid.getHoverName().copy());
		return result;
	}
}
