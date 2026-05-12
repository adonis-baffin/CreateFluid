package com.adonis.fluid.mixin;

import com.adonis.fluid.item.FluidManifestItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FilterItemStack.class)
public class FilterItemStackMixin {

	@Shadow(remap = false)
	@Final
	private ItemStack filterItemStack;

	@Inject(method = "test(Lnet/minecraft/world/level/Level;Lnet/neoforged/neoforge/fluids/FluidStack;Z)Z",
		at = @At("HEAD"), cancellable = true, remap = false)
	private void fluid$testManifestAsFluid(Level world, FluidStack stack, boolean matchNBT,
		CallbackInfoReturnable<Boolean> cir) {
		if (!(filterItemStack.getItem() instanceof FluidManifestItem))
			return;

		if (stack.isEmpty()) {
			cir.setReturnValue(false);
			return;
		}

		FluidStack manifestFluid = FluidManifestItem.read(filterItemStack);
		if (manifestFluid.isEmpty()) {
			cir.setReturnValue(false);
			return;
		}

		cir.setReturnValue(matchNBT
			? FluidStack.isSameFluidSameComponents(manifestFluid, stack)
			: manifestFluid.getFluid().isSame(stack.getFluid()));
	}
}
