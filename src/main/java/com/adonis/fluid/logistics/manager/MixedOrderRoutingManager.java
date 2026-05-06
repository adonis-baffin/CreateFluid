package com.adonis.fluid.logistics.manager;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.adonis.fluid.datacomponent.BrassBoxRoutingData;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;

public class MixedOrderRoutingManager {
	private static final Map<PackageOrderWithCrafts, BrassBoxRoutingData> ORDER_IDENTITIES = new IdentityHashMap<>();
	private static final Map<Integer, BrassBoxRoutingData> ORDER_IDS = new ConcurrentHashMap<>();

	private MixedOrderRoutingManager() {}

	public static synchronized void attachToOrder(PackageOrderWithCrafts order, BrassBoxRoutingData data) {
		if (order == null || data == null || data.isEmpty())
			return;
		ORDER_IDENTITIES.put(order, data);
	}

	public static synchronized BrassBoxRoutingData lookupOrder(PackageOrderWithCrafts order) {
		if (order == null)
			return BrassBoxRoutingData.EMPTY;
		return ORDER_IDENTITIES.getOrDefault(order, BrassBoxRoutingData.EMPTY);
	}

	public static BrassBoxRoutingData resolveAndBind(int orderId, PackageOrderWithCrafts order) {
		BrassBoxRoutingData existing = ORDER_IDS.get(orderId);
		if (existing != null)
			return existing;
		BrassBoxRoutingData resolved = lookupOrder(order);
		if (!resolved.isEmpty())
			ORDER_IDS.put(orderId, resolved);
		return resolved;
	}

	public static BrassBoxRoutingData getForOrderId(int orderId) {
		return ORDER_IDS.getOrDefault(orderId, BrassBoxRoutingData.EMPTY);
	}
}
