package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.block.SmartNozzle.SmartNozzleRenderer;
import com.adonis.fluid.block.SmartMesh.SmartMeshRenderer;
import com.adonis.fluid.block.Pipette.PipetteRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = CreateFluid.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CFClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 首先初始化 PartialModels - 这必须在任何使用它们的代码之前完成
        CFPartialModels.init();

        event.enqueueWork(() -> {
            // 设置方块渲染层
            ItemBlockRenderTypes.setRenderLayer(CFBlock.FRAME_TRAP.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.MESH_TRAP.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.TRAP_NOZZLE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.SMART_NOZZLE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.SMART_MESH.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.PIPETTE.get(), RenderType.cutout());

            // 注册方块实体渲染器
            BlockEntityRenderers.register(CFBlockEntity.SMART_NOZZLE.get(), SmartNozzleRenderer::new);
            BlockEntityRenderers.register(CFBlockEntity.SMART_MESH.get(), SmartMeshRenderer::new);
            BlockEntityRenderers.register(CFBlockEntity.PIPETTE.get(), PipetteRenderer::new);

            // 暂时注释掉Flywheel可视化注册，先让基本渲染工作
            // 等基本功能正常后再添加Flywheel支持
            /*
            try {
                // Flywheel可视化注册 - 如果需要的话
                VisualizationManager.get(Minecraft.getInstance().level).addBlockEntityType(
                    CFBlockEntity.PIPETTE.get(), PipetteVisual::new);
            } catch (Exception e) {
                CreateFluid.LOGGER.warn("Failed to register PipetteVisual: " + e.getMessage());
            }
            */
        });
    }
}