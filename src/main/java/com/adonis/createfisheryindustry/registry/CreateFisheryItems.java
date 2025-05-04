package com.adonis.createfisheryindustry.registry;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.google.common.collect.Sets;
import java.util.LinkedHashSet;
import java.util.function.Supplier;

import com.simibubi.create.foundation.item.ItemDescription;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import static com.adonis.createfisheryindustry.CreateFisheryMod.REGISTRATE;

public class CreateFisheryItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CreateFisheryMod.MODID);
    public static LinkedHashSet<Supplier<Item>> CREATIVE_TAB_ITEMS = Sets.newLinkedHashSet();

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    public static RegistryObject<Item> registerWithTab(final String name, final Supplier<Item> supplier) {
        RegistryObject<Item> item = ITEMS.register(name, supplier);
        CREATIVE_TAB_ITEMS.add(item::get);
        return item;
    }

    public static Item.Properties basicItem() {
        return new Item.Properties().stacksTo(64);
    }

    public static final RegistryObject<Item> ZINC_SHEET = registerWithTab("zinc_sheet",
            () -> new Item(basicItem()));

    public static final ItemEntry<Item> WORN_HARPOON = REGISTRATE.item("worn_harpoon",
                    p -> new Item(basicItem()))
            .onRegister(item -> {
                ItemDescription.useKey(item, "item.createfisheryindustry.worn_harpoon");
                CREATIVE_TAB_ITEMS.add(REGISTRATE.get("worn_harpoon", net.minecraft.core.registries.Registries.ITEM));
            })
            .register();

}