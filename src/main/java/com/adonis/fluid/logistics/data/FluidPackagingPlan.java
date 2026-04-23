package com.adonis.fluid.logistics.data;

public record FluidPackagingPlan(FluidRequestKey key, int requestedMb, int plannedMb, int packageCapacityMb) {
	public FluidPackagingPlan {
		if (requestedMb < 0) {
			throw new IllegalArgumentException("requestedMb must be >= 0");
		}
		if (plannedMb < 0) {
			throw new IllegalArgumentException("plannedMb must be >= 0");
		}
		if (packageCapacityMb <= 0) {
			throw new IllegalArgumentException("packageCapacityMb must be > 0");
		}
	}
}
