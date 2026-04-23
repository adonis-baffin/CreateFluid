package com.adonis.fluid.logistics.data;

import java.util.Objects;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;

public record FluidRequestKey(ResourceLocation fluidId) {
	public FluidRequestKey {
		Objects.requireNonNull(fluidId, "fluidId");
	}

	public static FluidRequestKey of(FluidStack fluid) {
		return new FluidRequestKey(BuiltInRegistries.FLUID.getKey(fluid.getFluid()));
	}

	public boolean matches(FluidStack fluid) {
		if (fluid.isEmpty()) {
			return false;
		}
		return fluidId.equals(BuiltInRegistries.FLUID.getKey(fluid.getFluid()));
	}
}
