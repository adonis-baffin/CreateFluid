package com.adonis.createfisheryindustry.registry;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.item.HarpoonItem;
import com.adonis.createfisheryindustry.item.WornHarpoonItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;

import static com.adonis.createfisheryindustry.CreateFisheryMod.REGISTRATE;

public class CreateFisheryItems {

    // 使用Registrate注册物品（推荐方式）
    public static final ItemEntry<Item> ZINC_SHEET = REGISTRATE.item("zinc_sheet", Item::new)
            .register();

    public static final ItemEntry<WornHarpoonItem> WORN_HARPOON = REGISTRATE.item("worn_harpoon", WornHarpoonItem::new)
            .properties(p -> p.stacksTo(64))
            .register();

    public static final ItemEntry<HarpoonItem> HARPOON = REGISTRATE.item("harpoon", HarpoonItem::new)
            .properties(p -> p.durability(250))
            .register();

    public static void register(IEventBus modEventBus) {
        // Registrate物品不需要手动注册到事件总线
        // 它们会自动通过REGISTRATE.registerEventListeners(modEventBus)注册
    }
}