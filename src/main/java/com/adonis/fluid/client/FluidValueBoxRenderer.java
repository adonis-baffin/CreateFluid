package com.adonis.fluid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

@OnlyIn(Dist.CLIENT)
public class FluidValueBoxRenderer {

	public static void renderFluidIntoValueBox(FluidStack stack, PoseStack ms, MultiBufferSource buffer, int light) {
		if (stack.isEmpty() || stack.getFluid() == Fluids.EMPTY) {
			return;
		}

		FluidStack renderStack = stack.getAmount() == 0 ? stack.copyWithAmount(1) : stack;
		IClientFluidTypeExtensions clientFluid = IClientFluidTypeExtensions.of(renderStack.getFluid());
		TextureAtlasSprite sprite = Minecraft.getInstance()
			.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
			.apply(clientFluid.getStillTexture(renderStack));
		int color = clientFluid.getTintColor(renderStack);

		float r = ((color >> 16) & 0xFF) / 255.0f;
		float g = ((color >> 8) & 0xFF) / 255.0f;
		float b = (color & 0xFF) / 255.0f;
		float a = ((color >> 24) & 0xFF) / 255.0f;
		if (a == 0) {
			a = 1.0f;
		}

		ms.pushPose();
		ms.scale(0.5f, 0.5f, 0.5f);
		ms.translate(-0.5f, -0.5f, -0.14f);

		VertexConsumer builder = buffer.getBuffer(RenderType.translucent());
		PoseStack.Pose pose = ms.last();

		putVertex(builder, pose, 0, 1, 0, sprite.getU0(), sprite.getV0(), r, g, b, a, light);
		putVertex(builder, pose, 1, 1, 0, sprite.getU1(), sprite.getV0(), r, g, b, a, light);
		putVertex(builder, pose, 1, 0, 0, sprite.getU1(), sprite.getV1(), r, g, b, a, light);
		putVertex(builder, pose, 0, 0, 0, sprite.getU0(), sprite.getV1(), r, g, b, a, light);

		ms.popPose();
	}

	private static void putVertex(VertexConsumer builder, PoseStack.Pose pose, float x, float y, float z, float u,
		float v, float r, float g, float b, float a, int light) {
		builder.addVertex(pose, x, y, z)
			.setColor(r, g, b, a)
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light)
			.setNormal(pose, 0, 0, 1);
	}
}
