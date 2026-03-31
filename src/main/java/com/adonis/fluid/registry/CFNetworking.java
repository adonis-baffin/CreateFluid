package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.packet.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = CreateFluid.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class CFNetworking {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // ========== 客户端接收的数据包 ==========

        // 铜龙头粒子包
        registrar.playToClient(
                CopperTapParticlePacket.TYPE,
                CopperTapParticlePacket.STREAM_CODEC,
                CopperTapParticlePacket::handle
        );

        // 移液器粒子包
        registrar.playToClient(
                PipetteParticlePacket.TYPE,
                PipetteParticlePacket.STREAM_CODEC,
                PipetteParticlePacket::handle
        );

        // 移液器客户端请求包（服务端通知客户端刷新设置）
        registrar.playToClient(
                PipetteFluidPlacementPacket.ClientBoundRequest.TYPE,
                PipetteFluidPlacementPacket.ClientBoundRequest.STREAM_CODEC,
                PipetteFluidPlacementPacket.ClientBoundRequest::handle
        );

        // 动力臂交互点同步包（服务端通知客户端更新交互点）
        registrar.playToClient(
                ArmInteractionPointSyncPacket.TYPE,
                ArmInteractionPointSyncPacket.STREAM_CODEC,
                ArmInteractionPointSyncPacket::handle
        );

        // 移液器交互点同步包（服务端通知客户端更新交互点）
        registrar.playToClient(
                PipetteInteractionPointSyncPacket.TYPE,
                PipetteInteractionPointSyncPacket.STREAM_CODEC,
                PipetteInteractionPointSyncPacket::handle
        );

        // ========== 服务端接收的数据包 ==========

        // 移液器放置配置包（客户端发送交互点配置到服务端）
        registrar.playToServer(
                PipetteFluidPlacementPacket.TYPE,
                PipetteFluidPlacementPacket.STREAM_CODEC,
                PipetteFluidPlacementPacket::handle
        );

        // 石英灯切换包（客户端请求切换石英灯状态）
        registrar.playToServer(
                QuartzLampTogglePacket.TYPE,
                QuartzLampTogglePacket.STREAM_CODEC,
                QuartzLampTogglePacket::handle
        );
    }
}