package com.adonis.createfisheryindustry.registry;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.item.CopperDivingLeggingsItem;
import com.adonis.createfisheryindustry.item.HarpoonItem;
import com.adonis.createfisheryindustry.item.NetheriteDivingLeggingsItem;
import com.adonis.createfisheryindustry.item.WornHarpoonItem;
import com.simibubi.create.content.equipment.armor.AllArmorMaterials;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorMaterials;
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
    }
}