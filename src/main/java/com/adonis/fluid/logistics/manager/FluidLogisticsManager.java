package com.adonis.fluid.logistics.manager;

import org.jetbrains.annotations.Nullable;

import com.adonis.fluid.logistics.data.FluidNetworkSummary;
import com.adonis.fluid.logistics.data.FluidPackagingPlan;
import com.adonis.fluid.logistics.data.FluidRequestKey;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class FluidLogisticsManager {
	private FluidLogisticsManager() {}

	public static FluidNetworkSummary summarize(IFluidHandler fluidHandler) {
		FluidNetworkSummary summary = new FluidNetworkSummary();
		for (int i = 0; i < fluidHandler.getTanks(); i++) {
			FluidStack fluid = fluidHandler.getFluidInTank(i);
			if (fluid.isEmpty()) {
				continue;
			}
			summary.add(FluidRequestKey.of(fluid), fluid.getAmount());
		}
		return summary;
	}

	public static int getAvailableAmount(IFluidHandler fluidHandler, FluidRequestKey key) {
		int available = 0;
		for (int i = 0; i < fluidHandler.getTanks(); i++) {
			FluidStack fluid = fluidHandler.getFluidInTank(i);
			if (key.matches(fluid)) {
				available += fluid.getAmount();
			}
		}
		return available;
	}

	@Nullable
	public static FluidPackagingPlan planPackaging(IFluidHandler fluidHandler, FluidRequestKey key, int requestedMb,
		int packageCapacityMb) {
		if (requestedMb <= 0) {
			return null;
		}

		int available = getAvailableAmount(fluidHandler, key);
		if (available <= 0) {
			return null;
		}

		int plannedMb = Math.min(Math.min(available, requestedMb), packageCapacityMb);
		if (plannedMb <= 0) {
			return null;
		}

		return new FluidPackagingPlan(key, requestedMb, plannedMb, packageCapacityMb);
	}

	public static FluidStack drainForPlan(IFluidHandler fluidHandler, FluidStack requestedFluid,
		FluidPackagingPlan plan, IFluidHandler.FluidAction action) {
		return fluidHandler.drain(requestedFluid.copyWithAmount(plan.plannedMb()), action);
	}
}
