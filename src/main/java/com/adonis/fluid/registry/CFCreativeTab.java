package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CFCreativeTab {
    private static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateFluid.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = REGISTER.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.fluid.main"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> CFItems.BATON.asStack())
                    .displayItems((parameters, output) -> {
                        // 手动添加方块
                        output.accept(CFItems.BATON);
                        output.accept(CFBlocks.PIPETTE);
                        output.accept(CFBlocks.COPPER_TAP);
                        output.accept(CFBlocks.FLUID_INTERFACE);
                        output.accept(CFBlocks.SMART_FLUID_INTERFACE);
                        output.accept(CFBlocks.CENTRIFUGAL_PUMP);
                        output.accept(CFBlocks.GUTTER_OUTLET);
                        output.accept(CFBlocks.SMART_GUTTER_OUTLET);
                        output.accept(CFBlocks.COPPER_SINK);
                        output.accept(CFBlocks.REDSTONE_VALVE);
                        output.accept(CFBlocks.REDSTONE_TRIPLE_VALVE);

                        // 手动添加物品
                        output.accept(CFItems.HONEYCOMB_MOLD);

                        // 细雪相关
                        output.accept(Items.POWDER_SNOW_BUCKET);
                    })
                    .build());

    public static void register(IEventBus modEventBus) {
        REGISTER.register(modEventBus);
    }
}