package com.adonis.fluid.registry;

import com.adonis.fluid.item.BatonItem;
import com.adonis.fluid.item.BrassBoxItem;
import com.adonis.fluid.item.CopperCanItem;
import com.adonis.fluid.item.FluidManifestItem;
import com.adonis.fluid.item.QuicksandBucketItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;

import static com.adonis.fluid.CreateFluid.REGISTRATE;

public class CFItems {

	public static final ItemEntry<BatonItem> BATON = REGISTRATE
		.item("baton", BatonItem::new)
		.properties(p -> p.stacksTo(1))
		.register();

	public static final ItemEntry<Item> HONEYCOMB_MOLD = REGISTRATE
		.item("honeycomb_mold", Item::new)
		.properties(p -> p.stacksTo(64))
		.register();

	public static final ItemEntry<Item> CHOCOLATE_MOLD = REGISTRATE
		.item("chocolate_mold", Item::new)
		.properties(p -> p.stacksTo(64))
		.register();

	public static final ItemEntry<Item> CHOCOLATE_SLAB = REGISTRATE
		.item("chocolate_slab", Item::new)
		.properties(p -> p.stacksTo(64))
		.register();

	public static final ItemEntry<CopperCanItem> COPPER_CAN = REGISTRATE
		.item("copper_can", CopperCanItem::new)
		.properties(p -> p.stacksTo(1).fireResistant())
		.register();

	public static final ItemEntry<BrassBoxItem> BRASS_BOX = REGISTRATE
		.item("brass_box", BrassBoxItem::new)
		.properties(p -> p.stacksTo(1).fireResistant())
		.register();

	public static final ItemEntry<FluidManifestItem> FLUID_MANIFEST = REGISTRATE
		.item("fluid_manifest", FluidManifestItem::new)
		.properties(p -> p.stacksTo(64))
		.register();

	public static final ItemEntry<QuicksandBucketItem> QUICKSAND_BUCKET = REGISTRATE
		.item("quicksand_bucket", QuicksandBucketItem::new)
		.properties(p -> p.stacksTo(1))
		.register();

	public static void register() {
		// Static initialization
	}
}
