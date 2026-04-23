package com.adonis.fluid.compat.jei;

import com.adonis.fluid.item.FluidManifestItem;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import com.simibubi.create.foundation.gui.menu.GhostItemSubmitPacket;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public class FluidGhostTarget<I, T extends GhostItemMenu<?>> implements IGhostIngredientHandler.Target<I> {

	private final Rect2i area;
	private final AbstractSimiContainerScreen<T> gui;
	private final int slotIndex;

	public FluidGhostTarget(AbstractSimiContainerScreen<T> gui, int slotIndex) {
		this.gui = gui;
		this.slotIndex = slotIndex;
		Slot slot = gui.getMenu().slots.get(slotIndex + 36);
		this.area = new Rect2i(gui.getGuiLeft() + slot.x, gui.getGuiTop() + slot.y, 16, 16);
	}

	@Override
	public Rect2i getArea() {
		return area;
	}

	@Override
	public void accept(I ingredient) {
		FluidStack fluid = (FluidStack) ingredient;
		ItemStack manifest = FluidManifestItem.of(fluid, 1);
		gui.getMenu().ghostInventory.setStackInSlot(slotIndex, manifest);
		CatnipServices.NETWORK.sendToServer(new GhostItemSubmitPacket(manifest, slotIndex));
	}
}
