package com.adonis.fluid.item;

import com.adonis.fluid.datacomponent.FluidPackageContent;
import com.adonis.fluid.registry.CFDataComponents;
import com.adonis.fluid.registry.CFItems;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.logistics.box.PackageStyles.PackageStyle;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public class FluidPackageItem extends PackageItem {
	// Phase 1: 复用原版稀有包裹模型作为占位，避免 PackageEntity 渲染黑紫色
	public static final PackageStyle FLUID_STYLE = new PackageStyles.PackageStyle("rare_creeper", 12, 10, 21f, true);

	public FluidPackageItem(Properties properties) {
		super(properties, FLUID_STYLE);
		PackageStyles.ALL_BOXES.remove(this);
		PackageStyles.RARE_BOXES.remove(this);
	}

	@Override
	public String getDescriptionId() {
		return "item." + com.adonis.fluid.CreateFluid.MOD_ID + ".fluid_package";
	}

	public static boolean isFluidPackage(ItemStack stack) {
		return stack.getItem() instanceof FluidPackageItem;
	}

	public static FluidStack getFluid(ItemStack stack) {
		FluidPackageContent content = stack.get(CFDataComponents.FLUID_PACKAGE_CONTENTS.get());
		return content != null ? content.fluid() : FluidStack.EMPTY;
	}

	public static int getCapacity(ItemStack stack) {
		FluidPackageContent content = stack.get(CFDataComponents.FLUID_PACKAGE_CONTENTS.get());
		return content != null ? content.capacity() : FluidPackageContent.DEFAULT_CAPACITY;
	}

	public static ItemStack create(FluidStack fluid, int capacity) {
		ItemStack stack = new ItemStack(CFItems.FLUID_PACKAGE.get());
		stack.set(CFDataComponents.FLUID_PACKAGE_CONTENTS.get(), new FluidPackageContent(fluid.copy(), capacity));
		return stack;
	}
}
