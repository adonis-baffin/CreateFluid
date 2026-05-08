package com.adonis.fluid.item;

import java.util.List;

import com.adonis.fluid.datacomponent.BrassBoxRoutingData;
import com.adonis.fluid.logistics.data.ContentRoute;
import com.adonis.fluid.registry.CFDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class PackageRoutingHelper {
	private PackageRoutingHelper() {
	}

	public static BrassBoxRoutingData getRoutingData(ItemStack stack) {
		return stack.getOrDefault(CFDataComponents.PACKAGE_ROUTING.get(), BrassBoxRoutingData.EMPTY);
	}

	public static boolean hasRoutingData(ItemStack stack) {
		return !getRoutingData(stack).isEmpty();
	}

	public static void setRoutingData(ItemStack stack, BrassBoxRoutingData data) {
		if (data == null || data.isEmpty()) {
			stack.remove(CFDataComponents.PACKAGE_ROUTING.get());
			return;
		}
		stack.set(CFDataComponents.PACKAGE_ROUTING.get(), data);
	}

	public static void appendRoutingSummary(ItemStack stack, List<Component> tooltip) {
		Component summary = getRoutingSummary(getRoutingData(stack));
		if (summary != null)
			tooltip.add(summary.copy().withStyle(ChatFormatting.GRAY));
	}

	public static Component getRoutingSummary(BrassBoxRoutingData routing) {
		if (routing == null || routing.isEmpty())
			return null;

		boolean hasUp = false;
		boolean hasDownOrStandard = false;

		for (var itemRoute : routing.itemRoutes()) {
			if (itemRoute.route() == ContentRoute.UP)
				hasUp = true;
			else
				hasDownOrStandard = true;
		}

		for (var fluidRoute : routing.fluidRoutes()) {
			if (fluidRoute.route() == ContentRoute.UP)
				hasUp = true;
			else
				hasDownOrStandard = true;
		}

		if (!hasUp && !hasDownOrStandard)
			return null;
		if (hasUp && !hasDownOrStandard)
			return Component.translatable("tooltip.fluid.package_routing.upstream");
		if (!hasUp)
			return Component.translatable("tooltip.fluid.package_routing.downstream");
		return Component.translatable("tooltip.fluid.package_routing.mixed");
	}
}
