package com.adonis.fluid.mixin.accessor;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BlazeBurnerBlockEntity.class)
public interface BlazeBurnerBlockEntityAccessor {

	@Accessor("remainingBurnTime")
	int getRemainingBurnTime();

	@Accessor("remainingBurnTime")
	void setRemainingBurnTime(int value);

	@Accessor("activeFuel")
	BlazeBurnerBlockEntity.FuelType getActiveFuel();

	@Accessor("activeFuel")
	void setActiveFuel(BlazeBurnerBlockEntity.FuelType value);

	@Invoker("updateBlockState")
	void callUpdateBlockState();

	@Invoker("playSound")
	void callPlaySound();
}
