package com.adonis.fluid.mixin;

import com.adonis.fluid.client.FluidSlotAmountRenderer;
import com.adonis.fluid.client.FluidSlotRenderer;
import com.adonis.fluid.client.FluidTooltipHelper;
import com.adonis.fluid.item.FluidManifestItem;
import com.adonis.fluid.mixin.accessor.StockKeeperRequestScreenAccessor;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;

import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StockKeeperRequestScreen.class)
public class StockKeeperRequestScreenMixin {

	@Unique
	private boolean fluid$isFluidManifest = false;
	@Unique
	private int fluid$fluidAmount = 0;
	@Unique
	private FluidStack fluid$cachedFluid = FluidStack.EMPTY;
	@Unique
	private GuiGraphics fluid$cachedGraphics = null;

	@Inject(
		method = "renderItemEntry(Lnet/minecraft/client/gui/GuiGraphics;FLcom/simibubi/create/content/logistics/BigItemStack;ZZ)V",
		at = @At("HEAD")
	)
	private void fluid$onRenderItemEntryHead(GuiGraphics graphics, float partialTicks, BigItemStack entry,
			boolean isStackHovered, boolean isRenderingOrders, CallbackInfo ci) {
		fluid$isFluidManifest = false;
		fluid$fluidAmount = 0;
		fluid$cachedFluid = FluidStack.EMPTY;
		fluid$cachedGraphics = graphics;

		if (entry.stack.getItem() instanceof FluidManifestItem) {
			FluidStack fluid = FluidManifestItem.read(entry.stack);
			if (!fluid.isEmpty()) {
				fluid$isFluidManifest = true;
				fluid$fluidAmount = entry.count;
				fluid$cachedFluid = fluid;
			}
		}
	}

	@Redirect(
		method = "renderItemEntry",
		at = @At(
			value = "INVOKE",
			target = "Lnet/createmod/catnip/gui/element/GuiGameElement;of(Lnet/minecraft/world/item/ItemStack;)Lnet/createmod/catnip/gui/element/GuiGameElement$GuiRenderBuilder;",
			remap = false
		),
		remap = false
	)
	private GuiGameElement.GuiRenderBuilder fluid$redirectGuiGameElementOf(ItemStack itemStack) {
		if (fluid$isFluidManifest && !fluid$cachedFluid.isEmpty()) {
			FluidSlotRenderer.renderFluidSlot(fluid$cachedGraphics, 0, 0, fluid$cachedFluid);
			return GuiGameElement.of(Blocks.AIR.asItem().getDefaultInstance());
		}
		return GuiGameElement.of(itemStack);
	}

	@Redirect(
		method = "renderItemEntry",
		at = @At(
			value = "INVOKE",
			target = "Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestScreen;drawItemCount(Lnet/minecraft/client/gui/GuiGraphics;II)V",
			remap = false
		),
		remap = false
	)
	private void fluid$redirectDrawItemCount(StockKeeperRequestScreen instance, GuiGraphics graphics, int count, int customCount) {
		if (fluid$isFluidManifest) {
			if (fluid$fluidAmount > 1) {
				FluidSlotAmountRenderer.renderInStockKeeper(graphics, fluid$fluidAmount);
			}
			return;
		}
		((StockKeeperRequestScreenAccessor) instance).callDrawItemCount(graphics, count, customCount);
	}

	@Redirect(
		method = "renderForeground",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
			remap = true
		)
	)
	private void fluid$redirectTooltip(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
		if (stack.getItem() instanceof FluidManifestItem) {
			FluidStack fluid = FluidManifestItem.read(stack);
			if (!fluid.isEmpty()) {
				FluidTooltipHelper.renderTooltip(graphics, font, fluid, fluid$fluidAmount, x, y);
				return;
			}
		}
		graphics.renderTooltip(font, stack, x, y);
	}

	@ModifyExpressionValue(
		method = "mouseClicked",
		at = @At(value = "CONSTANT", args = "intValue=1"),
		remap = false
	)
	private int fluid$modifyTransferNormal(int original, @Local BigItemStack entry) {
		if (entry != null && entry.stack.getItem() instanceof FluidManifestItem) {
			return 1000;
		}
		return original;
	}

	@ModifyExpressionValue(
		method = "mouseClicked",
		at = @At(value = "CONSTANT", args = "intValue=10"),
		remap = false
	)
	private int fluid$modifyTransferCtrl(int original, @Local BigItemStack entry) {
		if (entry != null && entry.stack.getItem() instanceof FluidManifestItem) {
			return 10000;
		}
		return original;
	}

	@ModifyExpressionValue(
		method = "mouseClicked",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I"),
		remap = false
	)
	private int fluid$modifyTransferShift(int original, @Local BigItemStack entry) {
		if (entry != null && entry.stack.getItem() instanceof FluidManifestItem) {
			return 50000;
		}
		return original;
	}

	@ModifyExpressionValue(
		method = "mouseScrolled",
		at = @At(value = "CONSTANT", args = "intValue=1"),
		remap = false
	)
	private int fluid$modifyScrollNormal(int original, @Local BigItemStack entry) {
		if (entry != null && entry.stack.getItem() instanceof FluidManifestItem) {
			return 1000;
		}
		return original;
	}

	@ModifyExpressionValue(
		method = "mouseScrolled",
		at = @At(value = "CONSTANT", args = "intValue=10"),
		remap = false
	)
	private int fluid$modifyScrollCtrl(int original, @Local BigItemStack entry) {
		if (entry != null && entry.stack.getItem() instanceof FluidManifestItem) {
			return 10000;
		}
		return original;
	}
}
