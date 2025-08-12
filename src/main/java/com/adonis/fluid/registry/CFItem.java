package com.adonis.fluid.registry;

import com.adonis.fluid.item.WornHarpoonItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;

import static com.adonis.fluid.CreateFluid.REGISTRATE;

public class CFItem {

    public static final ItemEntry<Item> ZINC_SHEET = REGISTRATE
            .item("zinc_sheet", Item::new)
            .register();

    public static final ItemEntry<WornHarpoonItem> WORN_HARPOON = REGISTRATE
            .item("worn_harpoon", WornHarpoonItem::new)
            .properties(p -> p.stacksTo(64))
            .register();

    public static void register(IEventBus modEventBus) {
        // Items are registered through REGISTRATE
    }
}