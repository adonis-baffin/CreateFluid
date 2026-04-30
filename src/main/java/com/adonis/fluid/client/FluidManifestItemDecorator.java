package com.adonis.fluid.client;

import com.adonis.fluid.item.FluidManifestItem;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

@OnlyIn(Dist.CLIENT)
public class FluidManifestItemDecorator {

	public static final IItemDecorator DECORATOR = (guiGraphics, font, stack, xOffset, yOffset) -> {
		if (!(stack.getItem() instanceof FluidManifestItem))
			return false;

		if (Minecraft.getInstance().screen instanceof StockKeeperRequestScreen)
			return false;

		FluidStack fluid = FluidManifestItem.read(stack);
		if (fluid.isEmpty())
			return false;

		FluidStack renderStack = fluid.getAmount() == 0 ? fluid.copyWithAmount(1) : fluid;
		IClientFluidTypeExtensions clientFluid = IClientFluidTypeExtensions.of(renderStack.getFluid());
		TextureAtlasSprite sprite = Minecraft.getInstance()
			.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
			.apply(clientFluid.getStillTexture(renderStack));

		int color = clientFluid.getTintColor(renderStack) | 0xFF000000;
		float r = ((color >> 16) & 0xFF) / 255.0f;
		float g = ((color >> 8) & 0xFF) / 255.0f;
		float b = (color & 0xFF) / 255.0f;
		float a = ((color >> 24) & 0xFF) / 255.0f;

		float u0 = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * (3f / 16f);
		float u1 = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * (13f / 16f);
		float v0 = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * (3f / 16f);
		float v1 = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * (13f / 16f);

		PoseStack poseStack = guiGraphics.pose();
		poseStack.pushPose();
		poseStack.translate(0, 0, 200);

		VertexConsumer builder = guiGraphics.bufferSource().getBuffer(RenderType.text(InventoryMenu.BLOCK_ATLAS));
		PoseStack.Pose pose = poseStack.last();
		float x0 = xOffset + 3;
		float y0 = yOffset + 3;
		float x1 = x0 + 10;
		float y1 = y0 + 10;
		int light = 0x00F000F0;

		RenderSystem.enableBlend();
		builder.addVertex(pose, x0, y1, 0)
			.setColor(r, g, b, a)
			.setUv(u0, v1)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light)
			.setNormal(pose, 0, 0, 1);
		builder.addVertex(pose, x1, y1, 0)
			.setColor(r, g, b, a)
			.setUv(u1, v1)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light)
			.setNormal(pose, 0, 0, 1);
		builder.addVertex(pose, x1, y0, 0)
			.setColor(r, g, b, a)
			.setUv(u1, v0)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light)
			.setNormal(pose, 0, 0, 1);
		builder.addVertex(pose, x0, y0, 0)
			.setColor(r, g, b, a)
			.setUv(u0, v0)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light)
			.setNormal(pose, 0, 0, 1);
		RenderSystem.disableBlend();

		poseStack.popPose();
		return false;
	};

	private FluidManifestItemDecorator() {}
}
