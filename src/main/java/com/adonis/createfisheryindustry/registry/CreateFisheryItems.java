package com.adonis.createfisheryindustry.registry;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.item.HarpoonItem;
import com.adonis.createfisheryindustry.item.WornHarpoonItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import static com.adonis.createfisheryindustry.CreateFisheryMod.REGISTRATE;

public class CreateFisheryItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CreateFisheryMod.MODID);

    public static Item.Properties basicItem() {
        return new Item.Properties().stacksTo(64);
    }

    public static final RegistryObject<Item> WORN_HARPOON = ITEMS.register("worn_harpoon",
            () -> new WornHarpoonItem(new Item.Properties()
                    .stacksTo(64))); // 可堆叠的破损鱼叉

    public static final RegistryObject<Item> HARPOON = ITEMS.register("harpoon",
            () -> new HarpoonItem(new Item.Properties()
                    .durability(250))); // 有耐久度的鱼叉，不能设置stacksTo

    public static final ItemEntry<Item> ZINC_SHEET = REGISTRATE.item("zinc_sheet",
                    p -> new Item(basicItem()))
            .register();

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}