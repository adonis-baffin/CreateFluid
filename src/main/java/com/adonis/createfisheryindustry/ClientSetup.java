package com.adonis.createfisheryindustry;

import com.adonis.createfisheryindustry.client.renderer.HarpoonRenderer;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.adonis.createfisheryindustry.registry.CreateFisheryEntityTypes;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
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

            // 注册实体渲染器
            EntityRenderers.register(CreateFisheryEntityTypes.HARPOON.get(), HarpoonRenderer::new);

            // 直接注册鱼叉物品属性，而不是调用HarpoonItem的方法
            ItemProperties.register(CreateFisheryItems.HARPOON.get(),
                    new ResourceLocation("createfisheryindustry", "throwing"),
                    (stack, level, entity, seed) -> {
                        return entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F;
                    });

            ItemProperties.register(CreateFisheryItems.HARPOON.get(),
                    new ResourceLocation("createfisheryindustry", "custom_model_data"),
                    (stack, level, entity, seed) -> {
                        return entity instanceof net.minecraft.world.entity.player.Player player &&
                                stack == player.getMainHandItem() && !player.isUsingItem() ? 1.0F : 0.0F;
                    });
        });
    }
}