package com.adonis.fluid.mixin;

import com.adonis.fluid.item.FluidManifestItem;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import net.createmod.catnip.data.Pair;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FactoryPanelBehaviour.class)
public class FactoryPanelBehaviourMixin {

	@ModifyVariable(method = "setFilter", at = @At("HEAD"), argsOnly = true)
	private ItemStack fluid$convertFluidContainerToManifest(ItemStack stack) {
		if (stack.isEmpty() || stack.getItem() instanceof FluidManifestItem)
			return stack;

		Level level = ((FactoryPanelBehaviour) (Object) this).blockEntity.getLevel();
		if (level == null)
			return stack;

		if (GenericItemEmptying.canItemBeEmptied(level, stack)) {
			Pair<FluidStack, ItemStack> result = GenericItemEmptying.emptyItem(level, stack, true);
			FluidStack fluid = result.getFirst();
			if (!fluid.isEmpty()) {
				return FluidManifestItem.of(fluid, 1);
			}
		}

		return stack;
	}
}
