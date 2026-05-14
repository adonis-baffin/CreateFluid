package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.packet.*;
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

    public static <T extends CustomPacketPayload> void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos chunkPos, T packet) {
        PacketDistributor.sendToPlayersTrackingChunk(level, chunkPos, packet);
    }

    public static <T extends CustomPacketPayload> void sendToPlayer(ServerPlayer player, T packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");


        registrar.playToClient(
                CopperTapParticlePacket.TYPE,
                CopperTapParticlePacket.STREAM_CODEC,
                CopperTapParticlePacket::handle
        );

        registrar.playToClient(
                PipetteParticlePacket.TYPE,
                PipetteParticlePacket.STREAM_CODEC,
                PipetteParticlePacket::handle
        );

        registrar.playToClient(
                PipetteFluidPlacementPacket.ClientBoundRequest.TYPE,
                PipetteFluidPlacementPacket.ClientBoundRequest.STREAM_CODEC,
                PipetteFluidPlacementPacket.ClientBoundRequest::handle
        );

        registrar.playToClient(
                ArmInteractionPointSyncPacket.TYPE,
                ArmInteractionPointSyncPacket.STREAM_CODEC,
                ArmInteractionPointSyncPacket::handle
        );

        registrar.playToClient(
                PipetteInteractionPointSyncPacket.TYPE,
                PipetteInteractionPointSyncPacket.STREAM_CODEC,
                PipetteInteractionPointSyncPacket::handle
        );

        registrar.playToClient(
                LogisticsJunctionPlacementPacket.ClientBoundRequest.TYPE,
                LogisticsJunctionPlacementPacket.ClientBoundRequest.STREAM_CODEC,
                LogisticsJunctionPlacementPacket.ClientBoundRequest::handle
        );


        registrar.playToServer(
                PipetteFluidPlacementPacket.TYPE,
                PipetteFluidPlacementPacket.STREAM_CODEC,
                PipetteFluidPlacementPacket::handle
        );

        registrar.playToServer(
                LogisticsJunctionPlacementPacket.TYPE,
                LogisticsJunctionPlacementPacket.STREAM_CODEC,
                LogisticsJunctionPlacementPacket::handle
        );

        registrar.playToServer(
                QuartzLampTogglePacket.TYPE,
                QuartzLampTogglePacket.STREAM_CODEC,
                QuartzLampTogglePacket::handle
        );

        registrar.playToServer(
                CentrifugalPumpModeTogglePacket.TYPE,
                CentrifugalPumpModeTogglePacket.STREAM_CODEC,
                CentrifugalPumpModeTogglePacket::handle
        );

        registrar.playToServer(
                CanFillerTogglePacket.TYPE,
                CanFillerTogglePacket.STREAM_CODEC,
                CanFillerTogglePacket::handle
        );

        registrar.playToServer(
                FrogportConnectionPacket.TYPE,
                FrogportConnectionPacket.STREAM_CODEC,
                FrogportConnectionPacket::handle
        );

        registrar.playToClient(
                FrogportConnectionFeedbackPacket.TYPE,
                FrogportConnectionFeedbackPacket.STREAM_CODEC,
                FrogportConnectionFeedbackPacket::handle
        );

        registrar.playToServer(
                MailboxStationConnectionPacket.TYPE,
                MailboxStationConnectionPacket.STREAM_CODEC,
                MailboxStationConnectionPacket::handle
        );

        registrar.playToServer(
                MailboxStationDisconnectPacket.TYPE,
                MailboxStationDisconnectPacket.STREAM_CODEC,
                MailboxStationDisconnectPacket::handle
        );
    }
}
