package com.adonis.fluid.registry;

import com.adonis.fluid.item.BatonItem;
import com.adonis.fluid.item.FluidManifestItem;
import com.adonis.fluid.item.CopperCanItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;

import static com.adonis.fluid.CreateFluid.REGISTRATE;

public class CFItems {

	// 指挥棒
	public static final ItemEntry<BatonItem> BATON = REGISTRATE
		.item("baton", BatonItem::new)
		.properties(p -> p.stacksTo(1))
		.register();

	// 蜂巢模具
	public static final ItemEntry<Item> HONEYCOMB_MOLD = REGISTRATE
		.item("honeycomb_mold", Item::new)
		.properties(p -> p.stacksTo(64))
		.register();

	// 铜罐
	public static final ItemEntry<CopperCanItem> COPPER_CAN = REGISTRATE
		.item("copper_can", CopperCanItem::new)
		.properties(p -> p.stacksTo(1).fireResistant())
		.register();

	// 流体清单（仅物流内部使用）
	public static final ItemEntry<FluidManifestItem> FLUID_MANIFEST = REGISTRATE
		.item("fluid_manifest", FluidManifestItem::new)
		.properties(p -> p.stacksTo(1))
		.register();

	public static void register() {
		// Static initialization
	}
}
