package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.packet.*;
import com.adonis.fluid.packet.ClipboardAddressParticlePacket;
import com.adonis.fluid.packet.ClipboardSetAddressPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = CreateFluid.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class CFNetworking {

    /**
     * 发送数据包给追踪指定区块的所有玩家
     */
    public static <T extends CustomPacketPayload> void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos chunkPos, T packet) {
        PacketDistributor.sendToPlayersTrackingChunk(level, chunkPos, packet);
    }

    /**
     * 发送数据包给指定玩家
     */
    public static <T extends CustomPacketPayload> void sendToPlayer(ServerPlayer player, T packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

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

        // 离心泵模式切换包（客户端请求切换离心泵模式）
        registrar.playToServer(
                CentrifugalPumpModeTogglePacket.TYPE,
                CentrifugalPumpModeTogglePacket.STREAM_CODEC,
                CentrifugalPumpModeTogglePacket::handle
        );

        // Packager 状态切换包
        registrar.playToServer(
                CanFillerTogglePacket.TYPE,
                CanFillerTogglePacket.STREAM_CODEC,
                CanFillerTogglePacket::handle
        );

        // Packager 地址清除包
        registrar.playToServer(
                CanFillerClearAddressPacket.TYPE,
                CanFillerClearAddressPacket.STREAM_CODEC,
                CanFillerClearAddressPacket::handle
        );

        // Clipboard 设置地址包（C→S）
        registrar.playToServer(
                ClipboardSetAddressPacket.TYPE,
                ClipboardSetAddressPacket.STREAM_CODEC,
                ClipboardSetAddressPacket::handle
        );

        // Clipboard 地址粒子效果包（S→C）
        registrar.playToClient(
                ClipboardAddressParticlePacket.TYPE,
                ClipboardAddressParticlePacket.STREAM_CODEC,
                ClipboardAddressParticlePacket::handle
        );

        // Frogport 连接包（C→S）
        registrar.playToServer(
                FrogportConnectionPacket.TYPE,
                FrogportConnectionPacket.STREAM_CODEC,
                FrogportConnectionPacket::handle
        );

        // Frogport 连接反馈包（S→C）
        registrar.playToClient(
                FrogportConnectionFeedbackPacket.TYPE,
                FrogportConnectionFeedbackPacket.STREAM_CODEC,
                FrogportConnectionFeedbackPacket::handle
        );

        // Mailbox-Station 连接包（C→S）
        registrar.playToServer(
                MailboxStationConnectionPacket.TYPE,
                MailboxStationConnectionPacket.STREAM_CODEC,
                MailboxStationConnectionPacket::handle
        );

        // Mailbox-Station 断开连接包（C→S）
        registrar.playToServer(
                MailboxStationDisconnectPacket.TYPE,
                MailboxStationDisconnectPacket.STREAM_CODEC,
                MailboxStationDisconnectPacket::handle
        );
    }
}