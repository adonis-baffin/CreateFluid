package com.adonis.fluid.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.minecraft.client.gui.GuiGraphics;

public class FluidSlotAmountRenderer {

	private static final int STOCK_KEEPER_COUNT_X = 14;
	private static final int STOCK_KEEPER_COUNT_Y = 10;

	public static void renderInStockKeeper(GuiGraphics graphics, int amount) {
		String text = FluidAmountHelper.format(amount);
		if (text.isBlank()) {
			return;
		}

		int visibleLength = 0;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) != ',') {
				visibleLength++;
			}
		}

		int renderX = STOCK_KEEPER_COUNT_X + (int) Math.floor(-visibleLength * 2.5);
		renderAmount(graphics, text, renderX, STOCK_KEEPER_COUNT_Y);
	}

	public static void renderAt(GuiGraphics graphics, int amount, int x, int y) {
		String text = FluidAmountHelper.format(amount);
		if (text.isBlank()) {
			return;
		}
		renderAmount(graphics, text, x, y);
	}

	private static void renderAmount(GuiGraphics graphics, String text, int startX, int startY) {
		int x = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = Character.toLowerCase(text.charAt(i));

			if (c == ',') continue;

			int index = c - '0';
			int xOffset = index * 6;
			int spriteWidth = AllGuiTextures.NUMBERS.getWidth();

			switch (c) {
				case ' ':
					x += 4;
					continue;
				case '.':
					spriteWidth = 3;
					xOffset = 60;
					break;
				case 'k':
					xOffset = 64;
					break;
				case 'm':
					spriteWidth = 7;
					xOffset = 70;
					break;
				case 'b':
					xOffset = 78;
					break;
				case '+':
					spriteWidth = 9;
					xOffset = 84;
					break;
			}

			RenderSystem.enableBlend();
			graphics.blit(AllGuiTextures.NUMBERS.location, startX + x, startY, 0,
					AllGuiTextures.NUMBERS.getStartX() + xOffset, AllGuiTextures.NUMBERS.getStartY(),
					spriteWidth, AllGuiTextures.NUMBERS.getHeight(), 256, 256);
			x += spriteWidth - 1;
		}
	}
}
