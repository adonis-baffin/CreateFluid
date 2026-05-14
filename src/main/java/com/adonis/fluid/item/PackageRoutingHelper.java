package com.adonis.fluid.item;

import java.util.List;

import com.adonis.fluid.datacomponent.BrassBoxRoutingData;
import com.adonis.fluid.logistics.data.ContentRoute;
import com.adonis.fluid.registry.CFDataComponents;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.box.PackageItem;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

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
		BrassBoxRoutingData routing = getRoutingData(stack);
		if (routing != null && !routing.isEmpty()) {
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
			if (hasUp && hasDownOrStandard)
				return;
		}

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

	public static void highlightVisibleItemRoutes(ItemStack stack, List<Component> tooltip) {
		BrassBoxRoutingData routing = getRoutingData(stack);
		if (routing == null || routing.isEmpty() || !stack.has(AllDataComponents.PACKAGE_CONTENTS))
			return;

		int searchStart = 0;
		int visibleNames = 0;
		ItemStackHandler contents = PackageItem.getContents(stack);
		for (int i = 0; i < contents.getSlots(); i++) {
			ItemStack itemstack = contents.getStackInSlot(i);
			if (itemstack.isEmpty())
				continue;
			if (itemstack.getItem() instanceof SpawnEggItem)
				continue;
			if (visibleNames > 2)
				continue;

			ContentRoute route = routing.routeForItem(itemstack);
			if (route == ContentRoute.UP || route == ContentRoute.DOWN) {
				int lineIndex = findContentLine(tooltip, itemstack, searchStart);
				if (lineIndex >= 0) {
					tooltip.add(lineIndex, formatRouteEntry(itemstack.getHoverName(), route));
					searchStart = lineIndex + 2;
				}
			}

			visibleNames++;
		}
	}

	public static Component formatUpstreamEntry(Component base) {
		return formatRouteEntry(base, ContentRoute.UP);
	}

	public static Component formatRouteEntry(Component base, ContentRoute route) {
		ChatFormatting color = route == ContentRoute.UP ? ChatFormatting.GOLD : ChatFormatting.AQUA;
		String arrow = route == ContentRoute.UP ? "\u2191 " : "\u2193 ";
		return Component.literal(arrow).withStyle(color)
			.append(base.copy().withStyle(color));
	}

	private static int findContentLine(List<Component> tooltip, ItemStack itemstack, int startIndex) {
		String expected = itemstack.getHoverName().getString() + " x" + itemstack.getCount();
		for (int i = Math.max(0, startIndex); i < tooltip.size(); i++) {
			if (tooltip.get(i).getString().equals(expected))
				return i;
		}
		return -1;
	}
}
