package com.adonis.fluid.registry;

import com.adonis.fluid.item.BatonItem;
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

    public static void register() {
        // Static initialization
    }
}