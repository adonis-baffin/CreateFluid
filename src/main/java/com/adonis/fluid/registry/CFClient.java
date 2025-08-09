package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.block.SmartNozzle.SmartNozzleRenderer;
import com.adonis.fluid.block.SmartMesh.SmartMeshRenderer;
import com.adonis.fluid.block.Pipette.PipetteRenderer;
import com.adonis.fluid.handler.PipetteInteractionPointHandler;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = CreateFluid.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CFClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        CreateFluid.LOGGER.info("=== CFClient.onClientSetup started ===");

        CreateFluid.LOGGER.info("Initializing CFPartialModels...");
        CFPartialModels.init();

        event.enqueueWork(() -> {
            CreateFluid.LOGGER.info("Setting up render layers...");
            // 设置方块渲染层
            ItemBlockRenderTypes.setRenderLayer(CFBlock.FRAME_TRAP.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.MESH_TRAP.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.TRAP_NOZZLE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.SMART_NOZZLE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.SMART_MESH.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.PIPETTE.get(), RenderType.cutout());
            CreateFluid.LOGGER.info("✓ Render layers set successfully");

            // 注册方块实体渲染器
            CreateFluid.LOGGER.info("Registering block entity renderers...");
            BlockEntityRenderers.register(CFBlockEntity.SMART_NOZZLE.get(), SmartNozzleRenderer::new);
            BlockEntityRenderers.register(CFBlockEntity.SMART_MESH.get(), SmartMeshRenderer::new);

            CreateFluid.LOGGER.info("Registering PipetteRenderer for block entity type: {}", CFBlockEntity.PIPETTE.get());
            BlockEntityRenderers.register(CFBlockEntity.PIPETTE.get(), PipetteRenderer::new);
            CreateFluid.LOGGER.info("✓ PipetteRenderer registered successfully");

            CreateFluid.LOGGER.info("✓ All block entity renderers registered successfully");
        });

        CreateFluid.LOGGER.info("=== CFClient.onClientSetup completed ===");
    }

    // 添加客户端tick事件处理（备用方案）
    @Mod.EventBusSubscriber(modid = CreateFluid.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                PipetteInteractionPointHandler.tick();
            }
        }
    }
}