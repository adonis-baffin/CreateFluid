package com.adonis.fluid.logistics.api;

import org.jetbrains.annotations.Nullable;

import com.adonis.fluid.logistics.data.FluidPackagingPlan;
import com.adonis.fluid.logistics.data.FluidRequestKey;

public interface IFluidLogisticsPackager extends IFluidLogisticsSource {
	boolean canFulfill(FluidRequestKey key);

	int getAvailableAmount(FluidRequestKey key);

	@Nullable
	FluidPackagingPlan planRequest(FluidRequestKey key, int requestedMb);
}
