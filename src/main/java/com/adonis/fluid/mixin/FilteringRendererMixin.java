package com.adonis.fluid.mixin;

import com.adonis.fluid.client.FluidValueBoxRenderer;
import com.adonis.fluid.item.FluidManifestItem;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FilteringRenderer.class)
public class FilteringRendererMixin {

	@Redirect(
		method = "renderOnBlockEntity",
		at = @At(
			value = "INVOKE",
			target = "Lcom/simibubi/create/foundation/blockEntity/behaviour/ValueBoxRenderer;renderItemIntoValueBox(Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"
		),
		remap = false
	)
	private static void fluid$renderManifestIntoValueBox(ItemStack filter, PoseStack ms, MultiBufferSource buffer,
		int light, int overlay) {
		if (filter.getItem() instanceof FluidManifestItem) {
			FluidStack fluid = FluidManifestItem.read(filter);
			if (!fluid.isEmpty()) {
				FluidValueBoxRenderer.renderFluidIntoValueBox(fluid, ms, buffer, light);
				return;
			}
		}

		ValueBoxRenderer.renderItemIntoValueBox(filter, ms, buffer, light, overlay);
	}

	@Redirect(
		method = "renderOnBlockEntity",
		at = @At(
			value = "INVOKE",
			target = "Lcom/simibubi/create/foundation/blockEntity/behaviour/ValueBoxRenderer;renderFlatItemIntoValueBox(Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"
		),
		remap = false
	)
	private static void fluid$renderManifestIntoFlatValueBox(ItemStack filter, PoseStack ms,
		MultiBufferSource buffer, int light, int overlay) {
		if (filter.getItem() instanceof FluidManifestItem) {
			FluidStack fluid = FluidManifestItem.read(filter);
			if (!fluid.isEmpty()) {
				FluidValueBoxRenderer.renderFluidIntoValueBox(fluid, ms, buffer, light);
				return;
			}
		}

		ValueBoxRenderer.renderFlatItemIntoValueBox(filter, ms, buffer, light, overlay);
	}
}
