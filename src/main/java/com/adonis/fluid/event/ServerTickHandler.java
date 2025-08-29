package com.adonis.fluid.event;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.content.aqueduct.AqueductNetworkManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateFluid.MODID)  // 添加modid
public class ServerTickHandler {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // 每个世界tick一次网络
            for (ServerLevel level : event.getServer().getAllLevels()) {
                AqueductNetworkManager.getInstance().tick(level);
            }
        }
    }
}