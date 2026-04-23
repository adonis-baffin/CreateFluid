package com.adonis.fluid.logistics.data;

public record FluidNetworkEntry(FluidRequestKey key, int amountMb) {
	public FluidNetworkEntry {
		if (amountMb < 0) {
			throw new IllegalArgumentException("amountMb must be >= 0");
		}
	}
}
