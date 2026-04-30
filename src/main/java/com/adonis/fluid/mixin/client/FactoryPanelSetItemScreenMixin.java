package com.adonis.fluid.mixin.client;

import com.adonis.fluid.client.FluidSlotRenderer;
import com.adonis.fluid.item.FluidManifestItem;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSetItemMenu;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSetItemScreen;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.spongepowered.asm.mixin.Mixin;

@OnlyIn(Dist.CLIENT)
@Mixin(FactoryPanelSetItemScreen.class)
public abstract class FactoryPanelSetItemScreenMixin extends AbstractSimiContainerScreen<FactoryPanelSetItemMenu> {

	protected FactoryPanelSetItemScreenMixin(FactoryPanelSetItemMenu container, Inventory inv, Component title) {
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
}
