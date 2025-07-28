package com.adonis.createfisheryindustry;

import com.adonis.createfisheryindustry.block.SmartNozzle.SmartNozzleRenderer;
import com.adonis.createfisheryindustry.block.SmartMesh.SmartMeshRenderer;
import com.adonis.createfisheryindustry.client.renderer.HarpoonRenderer;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.adonis.createfisheryindustry.registry.CreateFisheryEntityTypes;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = CreateFisheryMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // 设置方块渲染层
            ItemBlockRenderTypes.setRenderLayer(CreateFisheryBlocks.FRAME_TRAP.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateFisheryBlocks.MESH_TRAP.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateFisheryBlocks.TRAP_NOZZLE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateFisheryBlocks.SMART_NOZZLE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateFisheryBlocks.SMART_MESH.get(), RenderType.cutout());

            // 注册方块实体渲染器
            BlockEntityRenderers.register(CreateFisheryBlockEntities.SMART_NOZZLE.get(), SmartNozzleRenderer::new);
            BlockEntityRenderers.register(CreateFisheryBlockEntities.SMART_MESH.get(), SmartMeshRenderer::new);

            // 注册实体渲染器
            EntityRenderers.register(CreateFisheryEntityTypes.HARPOON.get(), HarpoonRenderer::new);

            // 注册鱼叉物品属性
            registerHarpoonProperties();

            CreateFisheryMod.LOGGER.info("Client setup completed: Entity renderers, block entity renderers and item properties registered");
        });
    }

    private static void registerHarpoonProperties() {
        // 注册投掷属性 - 检测玩家是否正在使用鱼叉
        ItemProperties.register(
                CreateFisheryItems.HARPOON.get(),
                new ResourceLocation("createfisheryindustry", "throwing"),
                (stack, level, entity, seed) -> {
                    if (entity != null && entity.isUsingItem() && entity.getUseItem() == stack) {
                        return 1.0F;
                    }
                    return 0.0F;
                }
        );

        CreateFisheryMod.LOGGER.info("Harpoon item properties registered successfully");
    }
}