package com.adonis.fluid.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

@OnlyIn(Dist.CLIENT)
public class FluidSlotRenderer {

	public static void renderFluidSlot(GuiGraphics graphics, int x, int y, FluidStack stack) {
		if (stack.isEmpty() || stack.getFluid() == Fluids.EMPTY) {
			return;
		}

		FluidStack renderStack = stack;
		if (stack.getAmount() == 0) {
			renderStack = stack.copyWithAmount(1);
		}

		IClientFluidTypeExtensions clientFluid = IClientFluidTypeExtensions.of(renderStack.getFluid());

		TextureAtlasSprite sprite = Minecraft.getInstance()
				.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
				.apply(clientFluid.getStillTexture(renderStack));

		int color = clientFluid.getTintColor(renderStack);
		float r = ((color >> 16) & 0xFF) / 255.0f;
		float g = ((color >> 8) & 0xFF) / 255.0f;
		float b = (color & 0xFF) / 255.0f;
		float a = ((color >> 24) & 0xFF) / 255.0f;
		if (a == 0) a = 1.0f;

		RenderSystem.enableBlend();
		graphics.blit(x + 1, y + 1, 2, 14, 14, sprite, r, g, b, a);
		RenderSystem.disableBlend();
	}
}
