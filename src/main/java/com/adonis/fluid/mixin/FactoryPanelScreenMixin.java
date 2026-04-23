package com.adonis.fluid.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.adonis.fluid.client.FluidSlotAmountRenderer;
import com.adonis.fluid.client.FluidSlotRenderer;
import com.adonis.fluid.item.FluidManifestItem;
import com.adonis.fluid.mixin.accessor.AbstractSimiScreenAccessor;
import com.simibubi.create.content.logistics.AddressEditBox;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelScreen;
import com.simibubi.create.content.logistics.BigItemStack;

import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.fluids.FluidStack;

@Mixin(FactoryPanelScreen.class)
public class FactoryPanelScreenMixin {

	@Shadow(remap = false)
	@Final
	private FactoryPanelBehaviour behaviour;

	@Shadow(remap = false)
	private AddressEditBox addressBox;

	@Shadow(remap = false)
	private boolean craftingActive;

	@Shadow(remap = false)
	private boolean restocker;

	@Shadow(remap = false)
	private BigItemStack outputConfig;

	@Shadow(remap = false)
	private java.util.List<BigItemStack> inputConfig;

	@Unique
	private FluidStack fluid$previewFluid = FluidStack.EMPTY;

	@Redirect(
		method = "renderInputItem",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;renderItem(Lnet/minecraft/world/item/ItemStack;II)V"
		)
	)
	private void fluid$renderInputFluidSlot(GuiGraphics graphics, ItemStack stack, int x, int y) {
		if (stack.getItem() instanceof FluidManifestItem) {
			FluidStack fluid = FluidManifestItem.read(stack);
			if (!fluid.isEmpty()) {
				FluidSlotRenderer.renderFluidSlot(graphics, x, y, fluid);
				return;
			}
		}
		graphics.renderItem(stack, x, y);
	}

	@Redirect(
		method = "renderInputItem",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"
		)
	)
	private void fluid$renderInputFluidAmount(GuiGraphics graphics, Font font, ItemStack stack, int x, int y,
		String text) {
		if (stack.getItem() instanceof FluidManifestItem) {
			FluidStack fluid = FluidManifestItem.read(stack);
			if (!fluid.isEmpty()) {
				try {
					int amount = Integer.parseInt(text);
					FluidSlotAmountRenderer.renderAt(graphics, amount, x + 1, y + 10);
					return;
				} catch (NumberFormatException ignored) {
				}
			}
		}
		graphics.renderItemDecorations(font, stack, x, y, text);
	}

	@Redirect(
		method = "renderWindow",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;renderItem(Lnet/minecraft/world/item/ItemStack;II)V"
		)
	)
	private void fluid$renderWindowFluidSlot(GuiGraphics graphics, ItemStack stack, int x, int y) {
		if (stack.getItem() instanceof FluidManifestItem) {
			FluidStack fluid = FluidManifestItem.read(stack);
			if (!fluid.isEmpty()) {
				FluidSlotRenderer.renderFluidSlot(graphics, x, y, fluid);
				return;
			}
		}
		graphics.renderItem(stack, x, y);
	}

	@Redirect(
		method = "renderWindow",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"
		)
	)
	private void fluid$renderWindowFluidAmount(GuiGraphics graphics, Font font, ItemStack stack, int x, int y,
		String text) {
		if (stack.getItem() instanceof FluidManifestItem) {
			FluidStack fluid = FluidManifestItem.read(stack);
			if (!fluid.isEmpty()) {
				try {
					int amount = Integer.parseInt(text);
					FluidSlotAmountRenderer.renderAt(graphics, amount, x + 1, y + 10);
					return;
				} catch (NumberFormatException ignored) {
				}
			}
		}
		graphics.renderItemDecorations(font, stack, x, y, text);
	}

	@Redirect(
		method = "renderWindow",
		at = @At(
			value = "INVOKE",
			target = "Lnet/createmod/catnip/gui/element/GuiGameElement;of(Lnet/minecraft/world/item/ItemStack;)Lnet/createmod/catnip/gui/element/GuiGameElement$GuiRenderBuilder;",
			ordinal = 1,
			remap = false
		),
		remap = false
	)
	private GuiGameElement.GuiRenderBuilder fluid$redirectPreviewFilter(ItemStack stack) {
		fluid$previewFluid = FluidStack.EMPTY;
		if (stack.getItem() instanceof FluidManifestItem) {
			FluidStack fluid = FluidManifestItem.read(stack);
			if (!fluid.isEmpty()) {
				fluid$previewFluid = fluid;
				return GuiGameElement.of(Blocks.AIR.asItem().getDefaultInstance());
			}
		}
		return GuiGameElement.of(stack);
	}

	@Inject(
		method = "renderWindow",
		at = @At("TAIL")
	)
	private void fluid$renderPreviewFluid(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks,
		CallbackInfo ci) {
		if (!fluid$previewFluid.isEmpty()) {
			AbstractSimiScreenAccessor screen = (AbstractSimiScreenAccessor) this;
			int previewY = behaviour.panelBE().restocker ? 0 : 60;
			FluidSlotRenderer.renderFluidSlot(graphics, screen.fluid$getGuiLeft() + 208,
				screen.fluid$getGuiTop() + 62 + previewY, fluid$previewFluid);
		}
	}

	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, remap = false)
	private void fluid$scrollFluidAmounts(double mouseX, double mouseY, double scrollX, double scrollY,
		org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if (addressBox.mouseScrolled(mouseX, mouseY, scrollX, scrollY) || craftingActive) {
			return;
		}

		AbstractSimiScreenAccessor screen = (AbstractSimiScreenAccessor) this;
		int x = screen.fluid$getGuiLeft();
		int y = screen.fluid$getGuiTop();
		int step = Screen.hasShiftDown() ? 10000 : 1000;
		int direction = (int) Math.signum(scrollY);

		if (direction == 0) {
			return;
		}

		for (int i = 0; i < inputConfig.size(); i++) {
			int inputX = x + 68 + (i % 3 * 20);
			int inputY = y + 26 + (i / 3 * 20);
			if (mouseX < inputX || mouseX >= inputX + 16 || mouseY < inputY || mouseY >= inputY + 16) {
				continue;
			}

			BigItemStack itemStack = inputConfig.get(i);
			if (!(itemStack.stack.getItem() instanceof FluidManifestItem)) {
				return;
			}

			itemStack.count = Math.clamp(itemStack.count + direction * step, 1000, 50000);
			cir.setReturnValue(true);
			return;
		}

		if (restocker) {
			return;
		}

		int outputX = x + 160;
		int outputY = y + 48;
		if (mouseX < outputX || mouseX >= outputX + 16 || mouseY < outputY || mouseY >= outputY + 16) {
			return;
		}

		if (!(outputConfig.stack.getItem() instanceof FluidManifestItem)) {
			return;
		}

		outputConfig.count = Math.clamp(outputConfig.count + direction * step, 1000, 50000);
		cir.setReturnValue(true);
	}
}
