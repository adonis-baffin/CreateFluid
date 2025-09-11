package com.adonis.fluid.registry;

import com.adonis.fluid.item.BatonItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraftforge.eventbus.api.IEventBus;

import static com.adonis.fluid.CreateFluid.REGISTRATE;

public class CFItem {

    public static final ItemEntry<BatonItem> BATON = REGISTRATE
            .item("baton", BatonItem::new)
            .properties(p -> p.stacksTo(1))
            .register();

    public static final ItemEntry<BatonItem> HONEYCOMB_MOLD = REGISTRATE
            .item("honeycomb_mold", BatonItem::new)
            .properties(p -> p.stacksTo(64))
            .register();

    public static void register(IEventBus modEventBus) {
    }
}