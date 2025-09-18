package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.block.Pipette.PipetteRenderer;
import com.adonis.fluid.block.FluidInterface.FluidInterfaceRenderer;
import com.adonis.fluid.block.SmartFluidInterface.SmartFluidInterfaceRenderer;
import com.adonis.fluid.block.Aqueduct.AqueductRenderer;
import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpRenderer;
import com.adonis.fluid.block.CopperFaucet.CopperFaucetRenderer;
import com.adonis.fluid.handler.PipetteFluidInteractionPointHandler;
import com.adonis.fluid.item.BatonItemPropertyFunction;
import com.adonis.fluid.ponder.CFPonderPlugin;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = CreateFluid.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CFClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        CFPartialModels.init();

        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(CFBlock.PIPETTE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.FLUID_INTERFACE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.SMART_FLUID_INTERFACE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.AQUEDUCT.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.CENTRIFUGAL_PUMP.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.COPPER_FAUCET.get(), RenderType.cutout());

            // 注册方块实体渲染器
            BlockEntityRenderers.register(CFBlockEntity.PIPETTE.get(), PipetteRenderer::new);
            BlockEntityRenderers.register(CFBlockEntity.FLUID_INTERFACE.get(), FluidInterfaceRenderer::new);
            BlockEntityRenderers.register(CFBlockEntity.SMART_FLUID_INTERFACE.get(), SmartFluidInterfaceRenderer::new);
            BlockEntityRenderers.register(CFBlockEntity.AQUEDUCT.get(), AqueductRenderer::new);
            BlockEntityRenderers.register(CFBlockEntity.CENTRIFUGAL_PUMP.get(), CentrifugalPumpRenderer::new); // 添加这行
            BlockEntityRenderers.register(CFBlockEntity.COPPER_FAUCET.get(), CopperFaucetRenderer::new);

            PonderIndex.addPlugin(new CFPonderPlugin());

            // 注册指挥棒的属性覆盖
            ItemProperties.register(CFItem.BATON.get(),
                    new ResourceLocation(CreateFluid.MODID, "selection_mode"),
                    new BatonItemPropertyFunction());
        });
    }

    // 客户端tick事件处理
    @Mod.EventBusSubscriber(modid = CreateFluid.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                PipetteFluidInteractionPointHandler.tick();
            }
        }
    }
}