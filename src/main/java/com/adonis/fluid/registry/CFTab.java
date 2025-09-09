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
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateFluid.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.fluid.main"))
                    .icon(() -> new ItemStack(CFBlock.SMART_FLUID_INTERFACE.get()))
                    .displayItems((parameters, output) -> {
                        // 动力设备
                        output.accept(CFBlock.PIPETTE.get());

                        // 流体接口
                        output.accept(CFBlock.FLUID_INTERFACE.get());
                        output.accept(CFBlock.SMART_FLUID_INTERFACE.get());

                        // 陷阱系列
                        output.accept(CFBlock.FRAME_TRAP.get());
                        output.accept(CFBlock.MESH_TRAP.get());
                        output.accept(CFBlock.TRAP_NOZZLE.get());
                        output.accept(CFBlock.SMART_NOZZLE.get());
                        output.accept(CFBlock.SMART_MESH.get());
                    })
                    .build());

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}