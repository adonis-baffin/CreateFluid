package com.adonis.fluid.mixin.compat.jei;

import com.adonis.fluid.compat.jei.FluidGhostTarget;
import com.simibubi.create.compat.jei.GhostIngredientHandler;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSetItemScreen;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterScreen;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedList;
import java.util.List;

@Mixin(value = GhostIngredientHandler.class, priority = 1100)
public class GhostIngredientHandlerMixin<T extends GhostItemMenu<?>> {

	@Inject(
		method = "getTargetsTyped",
		at = @At("HEAD"),
		cancellable = true
	)
	private <I> void fluid$addFluidTargets(AbstractSimiContainerScreen<T> gui, ITypedIngredient<I> ingredient,
										   boolean doStart, CallbackInfoReturnable<List<IGhostIngredientHandler.Target<I>>> cir) {
		if (ingredient.getType() == NeoForgeTypes.FLUID_STACK && gui instanceof FactoryPanelSetItemScreen) {
			List<IGhostIngredientHandler.Target<I>> targets = new LinkedList<>();
			for (int i = 36; i < gui.getMenu().slots.size(); i++) {
				if (gui.getMenu().slots.get(i).isActive()) {
					targets.add(new FluidGhostTarget<>(gui, i - 36));
					break;
				}
			}
			cir.setReturnValue(targets);
			return;
		}

		if (ingredient.getType() == NeoForgeTypes.FLUID_STACK && gui instanceof RedstoneRequesterScreen) {
			List<IGhostIngredientHandler.Target<I>> targets = new LinkedList<>();
			for (int i = 36; i < gui.getMenu().slots.size(); i++) {
				if (gui.getMenu().slots.get(i).isActive()) {
					targets.add(new FluidGhostTarget<>(gui, i - 36));
				}
			}
			cir.setReturnValue(targets);
		}
	}
}
