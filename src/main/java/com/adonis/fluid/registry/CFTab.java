package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class CFTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateFluid.MODID);

    public static final RegistryObject<CreativeModeTab> FLUID_TAB = CREATIVE_TABS.register("fluid_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.fluid.fluid_tab"))
                    .icon(() -> new ItemStack(CFBlock.MESH_TRAP.get()))
                    .displayItems((params, output) -> {
                        // 添加方块
                        output.accept(CFBlock.FRAME_TRAP.get());
                        output.accept(CFBlock.MESH_TRAP.get());
                        output.accept(CFBlock.TRAP_NOZZLE.get());
                        output.accept(CFBlock.SMART_NOZZLE.get());
                        output.accept(CFBlock.SMART_MESH.get());
                        output.accept(CFBlock.PIPETTE.get());


                        // 添加物品
                        output.accept(CFItem.WORN_HARPOON.get());
                        output.accept(CFItem.COPPER_DIVING_LEGGINGS.get());
                        output.accept(CFItem.NETHERITE_DIVING_LEGGINGS.get());
                        output.accept(CFItem.ZINC_SHEET.get());
                    })
                    .build());

    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }
}