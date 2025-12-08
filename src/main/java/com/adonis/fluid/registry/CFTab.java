package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class CFTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateFluid.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.fluid.main"))
                    .icon(() -> new ItemStack(CFItem.BATON.get()))
                    .displayItems((parameters, output) -> {

                        // 物品
                        output.accept(CFItem.BATON.get());

                        // 动力设备
                        output.accept(CFBlock.PIPETTE.get());
                        output.accept(CFBlock.CENTRIFUGAL_PUMP.get());
                        output.accept(CFBlock.COPPER_TAP.get());
                        output.accept(CFBlock.GUTTER_OUTLET.get());
                        output.accept(CFBlock.SMART_GUTTER_OUTLET.get());

                        // 流体接口
                        output.accept(CFBlock.FLUID_INTERFACE.get());
                        output.accept(CFBlock.SMART_FLUID_INTERFACE.get());

                        //其他
                        output.accept(CFItem.HONEYCOMB_MOLD.get());
                        output.accept(Items.POWDER_SNOW_BUCKET);

                    })
                    .build());

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}