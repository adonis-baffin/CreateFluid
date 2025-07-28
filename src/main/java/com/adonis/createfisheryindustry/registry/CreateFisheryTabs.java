package com.adonis.createfisheryindustry.registry;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class CreateFisheryTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateFisheryMod.MODID);

    public static final RegistryObject<CreativeModeTab> FISHERY_TAB = CREATIVE_TABS.register("fishery_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.createfisheryindustry.fishery_tab"))
                    .icon(() -> new ItemStack(CreateFisheryBlocks.MESH_TRAP.get()))
                    .displayItems((params, output) -> {
                        // 添加方块
                        output.accept(CreateFisheryBlocks.FRAME_TRAP.get());
                        output.accept(CreateFisheryBlocks.MESH_TRAP.get());
                        output.accept(CreateFisheryBlocks.TRAP_NOZZLE.get());
                        output.accept(CreateFisheryBlocks.SMART_NOZZLE.get());
                        output.accept(CreateFisheryBlocks.SMART_MESH.get());

                        // 添加物品
                        output.accept(CreateFisheryItems.WORN_HARPOON.get());
                        output.accept(CreateFisheryItems.HARPOON.get());
                        output.accept(CreateFisheryItems.COPPER_DIVING_LEGGINGS.get());
                        output.accept(CreateFisheryItems.NETHERITE_DIVING_LEGGINGS.get());
                        output.accept(CreateFisheryItems.ZINC_SHEET.get());
                    })
                    .build());

    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }
}