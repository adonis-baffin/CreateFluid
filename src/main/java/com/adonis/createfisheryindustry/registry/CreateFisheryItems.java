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

    // 铜质潜水护腿 - 移除durability设置，装甲物品已经有默认耐久度
    public static final ItemEntry<CopperDivingLeggingsItem> COPPER_DIVING_LEGGINGS = REGISTRATE.item("copper_diving_leggings",
                    p -> new CopperDivingLeggingsItem(
                            AllArmorMaterials.COPPER,
                            p, // 不设置durability，使用装甲材料的默认耐久度
                            new ResourceLocation(CreateFisheryMod.MODID, "copper_diving_leggings")
                    ))
            .register();

    // 下界合金潜水护腿 - 移除durability设置，只保留防火属性
    public static final ItemEntry<NetheriteDivingLeggingsItem> NETHERITE_DIVING_LEGGINGS = REGISTRATE.item("netherite_diving_leggings",
                    p -> new NetheriteDivingLeggingsItem(
                            ArmorMaterials.NETHERITE,
                            p.fireResistant(), // 只设置防火，不设置durability
                            new ResourceLocation(CreateFisheryMod.MODID, "netherite_diving_leggings")
                    ))
            .register();

    public static void register(IEventBus modEventBus) {
    }
}