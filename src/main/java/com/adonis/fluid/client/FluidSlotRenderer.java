package com.adonis.fluid.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

@OnlyIn(Dist.CLIENT)
public class FluidSlotRenderer {

	private static final int DEFAULT_SLOT_SIZE = 16;
	private static final int DEFAULT_SLOT_INSET = 1;

	public static void renderFluidSlot(GuiGraphics graphics, int x, int y, FluidStack stack) {
		renderFluidIcon(graphics, stack, x + DEFAULT_SLOT_INSET, y + DEFAULT_SLOT_INSET,
			DEFAULT_SLOT_SIZE - DEFAULT_SLOT_INSET * 2, DEFAULT_SLOT_SIZE - DEFAULT_SLOT_INSET * 2);
	}

	public static void renderFluidIcon(GuiGraphics graphics, FluidStack stack, int x, int y, int width, int height) {
		FluidIcon icon = FluidIcon.from(stack);
		if (icon == null) {
			return;
		}

		icon.draw(graphics, x, y, width, height);
	}

	private record FluidIcon(TextureAtlasSprite sprite, float red, float green, float blue, float alpha) {

		private static FluidIcon from(FluidStack stack) {
			if (stack.isEmpty() || stack.getFluid() == Fluids.EMPTY) {
				return null;
			}

			FluidStack displayStack = stack.getAmount() == 0 ? stack.copyWithAmount(1) : stack;
			IClientFluidTypeExtensions clientFluid = IClientFluidTypeExtensions.of(displayStack.getFluid());
			ResourceLocation stillTexture = clientFluid.getStillTexture(displayStack);
			TextureAtlasSprite sprite = Minecraft.getInstance()
				.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
				.apply(stillTexture);
			return fromTint(sprite, clientFluid.getTintColor(displayStack));
		}

		private static FluidIcon fromTint(TextureAtlasSprite sprite, int tint) {
			float alpha = ((tint >>> 24) & 0xFF) / 255.0f;
			if (alpha <= 0) {
				alpha = 1.0f;
			}

			return new FluidIcon(sprite, channel(tint, 16), channel(tint, 8), channel(tint, 0), alpha);
		}

		private static float channel(int tint, int shift) {
			return ((tint >>> shift) & 0xFF) / 255.0f;
		}

		private void draw(GuiGraphics graphics, int x, int y, int width, int height) {
			RenderSystem.enableBlend();
			graphics.blit(x, y, 0, width, height, sprite, red, green, blue, alpha);
			RenderSystem.disableBlend();
		}
	}
}
