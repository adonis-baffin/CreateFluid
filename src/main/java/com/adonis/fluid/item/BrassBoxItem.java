package com.adonis.fluid.item;

import java.util.ArrayList;
import java.util.List;

import com.adonis.fluid.datacomponent.BrassBoxFluidContent;
import com.adonis.fluid.datacomponent.BrassBoxFluidContent.FluidEntry;
import com.adonis.fluid.datacomponent.BrassBoxRoutingData;
import com.adonis.fluid.registry.CFDataComponents;
import com.adonis.fluid.registry.CFItems;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.logistics.box.PackageStyles.PackageStyle;
import com.simibubi.create.foundation.item.ItemHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;

public class BrassBoxItem extends PackageItem {
	public static final int MAX_FLUID_TYPES = 4;
	public static final int MAX_FLUID_AMOUNT = 4000;
	public static final PackageStyle BRASS_BOX_STYLE =
		new PackageStyles.PackageStyle("rare_creeper", 12, 10, 21f, true);

	public BrassBoxItem(Properties properties) {
		super(properties, BRASS_BOX_STYLE);
		PackageStyles.ALL_BOXES.remove(this);
		PackageStyles.RARE_BOXES.remove(this);
	}

	@Override
	public String getDescriptionId() {
		return "item." + com.adonis.fluid.CreateFluid.MOD_ID + ".brass_box";
	}

	public static boolean isBrassBox(ItemStack stack) {
		return stack.getItem() instanceof BrassBoxItem;
	}

	public static ItemStack create(ItemStackHandler itemContents, List<FluidEntry> fluids, BrassBoxRoutingData routing) {
		ItemStack stack = new ItemStack(CFItems.BRASS_BOX.get());
		stack.set(com.simibubi.create.AllDataComponents.PACKAGE_CONTENTS, ItemHelper.containerContentsFromHandler(itemContents));
		if (fluids != null && !fluids.isEmpty()) {
			List<FluidEntry> validFluids = new ArrayList<>();
			for (FluidEntry entry : fluids) {
				if (entry == null || entry.isEmpty())
					continue;
				validFluids.add(entry.copy());
			}
			if (!validFluids.isEmpty())
				stack.set(CFDataComponents.BRASS_BOX_FLUIDS.get(), new BrassBoxFluidContent(validFluids));
		}
		PackageRoutingHelper.setRoutingData(stack, routing);
		return stack;
	}

	public static BrassBoxFluidContent getFluidContent(ItemStack stack) {
		return stack.getOrDefault(CFDataComponents.BRASS_BOX_FLUIDS.get(), BrassBoxFluidContent.EMPTY);
	}

	public static BrassBoxRoutingData getRoutingData(ItemStack stack) {
		return PackageRoutingHelper.getRoutingData(stack);
	}

	public static void setRoutingData(ItemStack stack, BrassBoxRoutingData data) {
		PackageRoutingHelper.setRoutingData(stack, data);
	}

	@Override
	public InteractionResultHolder<ItemStack> open(Level worldIn, Player playerIn, InteractionHand handIn) {
		return InteractionResultHolder.fail(playerIn.getItemInHand(handIn));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
		super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
		PackageRoutingHelper.highlightVisibleItemRoutes(stack, tooltipComponents);
		PackageRoutingHelper.appendRoutingSummary(stack, tooltipComponents);
		BrassBoxFluidContent fluidContent = getFluidContent(stack);
		for (FluidEntry entry : fluidContent.fluids()) {
			if (entry.isEmpty())
				continue;
			Component fluidLine = entry.fluid().getHoverName()
				.copy();
			tooltipComponents.add(entry.route() == com.adonis.fluid.logistics.data.ContentRoute.UP
				? PackageRoutingHelper.formatUpstreamEntry(fluidLine)
				: fluidLine.copy().withStyle(ChatFormatting.GRAY));
		}
	}
}
