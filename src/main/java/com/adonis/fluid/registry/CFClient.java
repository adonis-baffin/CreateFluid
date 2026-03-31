package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.item.BatonItemPropertyFunction;
import com.adonis.fluid.ponder.CFPonderPlugin;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = CreateFluid.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CFClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        CFPartialModels.init();

        event.enqueueWork(() -> {
            // 设置渲染层
            ItemBlockRenderTypes.setRenderLayer(CFBlocks.FLUID_INTERFACE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlocks.SMART_FLUID_INTERFACE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlocks.COPPER_TAP.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlocks.PIPETTE.get(), RenderType.cutout());

            // 注册 Ponder 插件
            PonderIndex.addPlugin(new CFPonderPlugin());

            // 注册指挥棒的属性覆盖
            if (CFItems.BATON != null) {
                ItemProperties.register(CFItems.BATON.get(),
                        ResourceLocation.fromNamespaceAndPath(CreateFluid.MOD_ID, "selection_mode"),
                        new BatonItemPropertyFunction());
            }
        });
    }
}