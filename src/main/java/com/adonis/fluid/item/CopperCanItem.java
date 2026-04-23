package com.adonis.fluid.item;

import com.adonis.fluid.datacomponent.CopperCanContent;
import com.adonis.fluid.registry.CFDataComponents;
import com.adonis.fluid.registry.CFItems;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.logistics.box.PackageStyles.PackageStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

public class CopperCanItem extends PackageItem {
	// Phase 1: 复用原版稀有包裹模型作为占位，避免 PackageEntity 渲染黑紫色
	public static final PackageStyle COPPER_CAN_STYLE = new PackageStyles.PackageStyle("rare_creeper", 12, 10, 21f, true);

	public CopperCanItem(Properties properties) {
		super(properties, COPPER_CAN_STYLE);
		PackageStyles.ALL_BOXES.remove(this);
		PackageStyles.RARE_BOXES.remove(this);
	}

	@Override
	public String getDescriptionId() {
		return "item." + com.adonis.fluid.CreateFluid.MOD_ID + ".copper_can";
	}

	public static boolean isCopperCan(ItemStack stack) {
		return stack.getItem() instanceof CopperCanItem;
	}

	public static FluidStack getFluid(ItemStack stack) {
		CopperCanContent content = stack.get(CFDataComponents.COPPER_CAN_CONTENTS.get());
		return content != null ? content.fluid() : FluidStack.EMPTY;
	}

	public static int getCapacity(ItemStack stack) {
		CopperCanContent content = stack.get(CFDataComponents.COPPER_CAN_CONTENTS.get());
		return content != null ? content.capacity() : CopperCanContent.DEFAULT_CAPACITY;
	}

	public static ItemStack create(FluidStack fluid, int capacity) {
		ItemStack stack = new ItemStack(CFItems.COPPER_CAN.get());
		stack.set(CFDataComponents.COPPER_CAN_CONTENTS.get(), new CopperCanContent(fluid.copy(), capacity));
		return stack;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
		super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
		FluidStack fluid = getFluid(stack);
		if (!fluid.isEmpty()) {
			tooltipComponents.add(Component.translatable("item.fluid.copper_can.tooltip.fluid", fluid.getHoverName())
				.withStyle(ChatFormatting.GRAY));
			tooltipComponents.add(Component.translatable("item.fluid.copper_can.tooltip.amount",
				fluid.getAmount(), getCapacity(stack))
				.withStyle(ChatFormatting.GRAY));
		} else {
			tooltipComponents.add(Component.translatable("item.fluid.copper_can.tooltip.empty")
				.withStyle(ChatFormatting.GRAY));
		}
	}
}
