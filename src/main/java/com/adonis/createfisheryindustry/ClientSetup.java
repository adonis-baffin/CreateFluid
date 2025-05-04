package com.adonis.createfisheryindustry;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = CreateFisheryMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(CreateFisheryBlocks.FRAME_TRAP.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateFisheryBlocks.MESH_TRAP.get(), RenderType.cutout());
        });
    }
}