package com.adonis.fluid.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class FluidSlotAmountRenderer {

	private static final int STOCK_KEEPER_AMOUNT_CENTER_X = 14;
	private static final int STOCK_KEEPER_AMOUNT_BASELINE_Y = 12;
	private static final int SLOT_DECORATION_RIGHT_X = 17;
	private static final int SLOT_DECORATION_DOWN_SHIFT = 2;
	private static final int AMOUNT_TEXT_COLOR = 0xFFFFFF;
	private static final float AMOUNT_TEXT_SCALE = 0.65f;

	public static void renderInStockKeeper(GuiGraphics graphics, int amount) {
		renderCentered(graphics, FluidAmountHelper.format(amount), STOCK_KEEPER_AMOUNT_CENTER_X,
			STOCK_KEEPER_AMOUNT_BASELINE_Y);
	}

	public static void renderAt(GuiGraphics graphics, int amount, int x, int y) {
		renderRightAligned(graphics, FluidAmountHelper.format(amount), x + SLOT_DECORATION_RIGHT_X,
			y + SLOT_DECORATION_DOWN_SHIFT);
	}

	public static void renderCentered(GuiGraphics graphics, String text, int centerX, int y) {
		TextLayout layout = TextLayout.of(text);
		if (layout.isEmpty())
			return;

		draw(graphics, layout, centerX - layout.width() / 2, y);
	}

	public static void renderRightAligned(GuiGraphics graphics, String text, int rightX, int y) {
		TextLayout layout = TextLayout.of(text);
		if (layout.isEmpty())
			return;

		draw(graphics, layout, rightX - layout.width(), y);
	}

	private static void draw(GuiGraphics graphics, TextLayout layout, int x, int y) {
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 200);
		graphics.pose().scale(AMOUNT_TEXT_SCALE, AMOUNT_TEXT_SCALE, 1);
		graphics.drawString(layout.font(), layout.text(), Math.round(x / AMOUNT_TEXT_SCALE),
			Math.round(y / AMOUNT_TEXT_SCALE), AMOUNT_TEXT_COLOR, true);
		graphics.pose().popPose();
	}

	private record TextLayout(Font font, String text, int width) {
		private static TextLayout of(String text) {
			Font font = Minecraft.getInstance().font;
			String compact = sanitize(text);
			return new TextLayout(font, compact, Math.round(font.width(compact) * AMOUNT_TEXT_SCALE));
		}

		private static String sanitize(String text) {
			if (text == null)
				return "";
			return text.replace(",", "").trim();
		}

		private boolean isEmpty() {
			return text.isBlank();
		}
	}
}
