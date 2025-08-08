package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.item.CopperDivingLeggingsItem;
import com.adonis.fluid.item.NetheriteDivingLeggingsItem;
import com.adonis.fluid.item.WornHarpoonItem;
import com.simibubi.create.content.equipment.armor.AllArmorMaterials;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorMaterials;
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

    public static final ItemEntry<CopperDivingLeggingsItem> COPPER_DIVING_LEGGINGS = REGISTRATE
            .item("copper_diving_leggings", p -> new CopperDivingLeggingsItem(
                    AllArmorMaterials.COPPER, p,
                    new ResourceLocation(CreateFluid.MODID, "copper_diving_leggings")))
            .register();

    public static final ItemEntry<NetheriteDivingLeggingsItem> NETHERITE_DIVING_LEGGINGS = REGISTRATE
            .item("netherite_diving_leggings", p -> new NetheriteDivingLeggingsItem(
                    ArmorMaterials.NETHERITE, p.fireResistant(),
                    new ResourceLocation(CreateFluid.MODID, "netherite_diving_leggings")))
            .register();

    public static void register(IEventBus modEventBus) {
        // Items are registered through REGISTRATE
    }
}