package com.adonis.fluid.mixin.client;

import com.adonis.fluid.client.FluidAmountHelper;
import com.adonis.fluid.client.FluidSlotAmountRenderer;
import com.adonis.fluid.client.FluidSlotRenderer;
import com.adonis.fluid.item.FluidManifestItem;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterMenu;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterScreen;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@OnlyIn(Dist.CLIENT)
@Mixin(RedstoneRequesterScreen.class)
public abstract class RedstoneRequesterScreenMixin extends AbstractSimiContainerScreen<RedstoneRequesterMenu> {

	@Unique
	private static final int fluid$defaultRequesterAmountMb = 250;

	@Unique
	private static final int fluid$requesterStepAmountMb = 10;

	@Unique
	private static final int fluid$minRequesterAmountMb = 10;

	@Unique
	private static final int fluid$maxRequesterAmountMb = 50000;

	@Shadow(remap = false)
	@Final
	private List<Integer> amounts;

	@Unique
	private final boolean[] fluid$manifestSlotInitialized = new boolean[9];

	protected RedstoneRequesterScreenMixin(RedstoneRequesterMenu container, Inventory inv, Component title) {
		super(container, inv, title);
	}

	@Override
	protected void renderSlot(GuiGraphics graphics, Slot slot) {
		if (slot instanceof SlotItemHandler handlerSlot) {
			int slotIndex = handlerSlot.getSlotIndex();
			if (slotIndex >= 0 && slotIndex < menu.ghostInventory.getSlots()) {
				ItemStack ghostStack = menu.ghostInventory.getStackInSlot(slotIndex);
				if (ghostStack.getItem() instanceof FluidManifestItem) {
					FluidStack fluid = FluidManifestItem.read(ghostStack);
					if (!fluid.isEmpty()) {
						FluidSlotRenderer.renderFluidSlot(graphics, slot.x, slot.y, fluid);
						return;
					}
				}
			}
		}

		super.renderSlot(graphics, slot);
	}

	@Inject(method = "containerTick", at = @At("TAIL"), remap = false)
	private void fluid$normalizeFluidAmounts(CallbackInfo ci) {
		for (int i = 0; i < amounts.size() && i < fluid$manifestSlotInitialized.length; i++) {
			ItemStack ghostStack = menu.ghostInventory.getStackInSlot(i);
			boolean isManifest = ghostStack.getItem() instanceof FluidManifestItem && !FluidManifestItem.read(ghostStack).isEmpty();

			if (!isManifest) {
				fluid$manifestSlotInitialized[i] = false;
				continue;
			}

			if (!fluid$manifestSlotInitialized[i] && amounts.get(i) <= 1)
				amounts.set(i, fluid$defaultRequesterAmountMb);

			fluid$manifestSlotInitialized[i] = true;
		}
	}

	@Redirect(
		method = "renderForeground",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
			remap = true
		),
		remap = false
	)
	private void fluid$renderFluidAmount(GuiGraphics graphics, Font font, ItemStack stack, int x, int y, String text) {
		if (stack.getItem() instanceof FluidManifestItem) {
			FluidStack fluid = FluidManifestItem.read(stack);
			int slotIndex = (x - (getGuiLeft() + 27)) / 20;
			if (!fluid.isEmpty() && slotIndex >= 0 && slotIndex < amounts.size()) {
				FluidSlotAmountRenderer.renderAt(graphics, amounts.get(slotIndex), x + 1, y + 10);
				return;
			}
		}

		graphics.renderItemDecorations(font, stack, x, y, text);
	}

	@Inject(method = "getTooltipFromContainerItem", at = @At("HEAD"), cancellable = true, remap = true)
	private void fluid$modifyFluidTooltip(ItemStack stack, CallbackInfoReturnable<List<Component>> cir) {
		if (!(hoveredSlot instanceof SlotItemHandler))
			return;

		int slotIndex = hoveredSlot.getSlotIndex();
		if (slotIndex < 0 || slotIndex >= amounts.size())
			return;

		if (!(stack.getItem() instanceof FluidManifestItem))
			return;

		FluidStack fluid = FluidManifestItem.read(stack);
		if (fluid.isEmpty())
			return;

		String amountText = fluid$formatTooltipAmount(amounts.get(slotIndex));
		cir.setReturnValue(List.of(
			CreateLang.translate("gui.factory_panel.send_item",
				CreateLang.text(fluid.getHoverName().getString()).add(CreateLang.text(" x" + amountText)))
				.color(ScrollInput.HEADER_RGB)
				.component(),
			CreateLang.translate("gui.factory_panel.scroll_to_change_amount")
				.style(ChatFormatting.DARK_GRAY)
				.style(ChatFormatting.ITALIC)
				.component()));
	}

	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, remap = false)
	private void fluid$handleFluidScroll(double mouseX, double mouseY, double scrollX, double scrollY,
		CallbackInfoReturnable<Boolean> cir) {
		int x = getGuiLeft();
		int y = getGuiTop();
		int direction = (int) Math.signum(scrollY);

		if (direction == 0)
			return;

		for (int i = 0; i < amounts.size(); i++) {
			int inputX = x + 27 + i * 20;
			int inputY = y + 28;
			if (mouseX < inputX || mouseX >= inputX + 16 || mouseY < inputY || mouseY >= inputY + 16)
				continue;

			ItemStack itemStack = menu.ghostInventory.getStackInSlot(i);
			if (!(itemStack.getItem() instanceof FluidManifestItem))
				return;

			FluidStack fluid = FluidManifestItem.read(itemStack);
			if (fluid.isEmpty())
				return;

			amounts.set(i, Math.clamp(amounts.get(i) + direction * fluid$requesterStepAmountMb,
				fluid$minRequesterAmountMb, fluid$maxRequesterAmountMb));
			cir.setReturnValue(true);
			return;
		}
	}

	@Unique
	private String fluid$formatTooltipAmount(int amountMb) {
		int clamped = Math.max(0, amountMb);
		if (clamped < 1000)
			return clamped + "mb";
		if (clamped % 1000 == 0)
			return clamped / 1000 + "B";
		return FluidAmountHelper.format(clamped) + "B";
	}
}
