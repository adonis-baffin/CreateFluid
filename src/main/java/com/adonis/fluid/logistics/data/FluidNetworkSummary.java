package com.adonis.fluid.logistics.data;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class FluidNetworkSummary {
	private final Map<FluidRequestKey, Integer> amounts = new LinkedHashMap<>();

	public void add(FluidRequestKey key, int amountMb) {
		if (amountMb <= 0) {
			return;
		}
		amounts.merge(key, amountMb, Integer::sum);
	}

	public int getAmount(FluidRequestKey key) {
		return amounts.getOrDefault(key, 0);
	}

	public boolean isEmpty() {
		return amounts.isEmpty();
	}

	public Collection<FluidNetworkEntry> entries() {
		return amounts.entrySet()
			.stream()
			.map(entry -> new FluidNetworkEntry(entry.getKey(), entry.getValue()))
			.toList();
	}

	public Map<FluidRequestKey, Integer> asMap() {
		return Collections.unmodifiableMap(amounts);
	}
}
