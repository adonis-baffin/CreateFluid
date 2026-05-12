package com.adonis.fluid.packet;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.block.LogisticsJunction.LogisticsJunctionBlockEntity;
import com.adonis.fluid.handler.LogisticsJunctionSelectionHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LogisticsJunctionPlacementPacket(BlockPos pos, boolean clear, BlockPos targetPos,
                                               Direction targetFace) implements CustomPacketPayload {

    public static final Type<LogisticsJunctionPlacementPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFluid.MOD_ID, "logistics_junction_placement"));

    public static final StreamCodec<ByteBuf, LogisticsJunctionPlacementPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            LogisticsJunctionPlacementPacket::pos,
            ByteBufCodecs.BOOL,
            LogisticsJunctionPlacementPacket::clear,
            BlockPos.STREAM_CODEC,
            LogisticsJunctionPlacementPacket::targetPos,
            ByteBufCodecs.VAR_INT.map(Direction::from3DDataValue, Direction::get3DDataValue),
            LogisticsJunctionPlacementPacket::targetFace,
            LogisticsJunctionPlacementPacket::new
    );

    public static LogisticsJunctionPlacementPacket clear(BlockPos pos) {
        return new LogisticsJunctionPlacementPacket(pos, true, BlockPos.ZERO, Direction.UP);
    }

    public static LogisticsJunctionPlacementPacket set(BlockPos pos, BlockPos targetPos, Direction targetFace) {
        return new LogisticsJunctionPlacementPacket(pos, false, targetPos, targetFace);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;

            Level level = player.level();
            if (!level.isLoaded(pos))
                return;

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof LogisticsJunctionBlockEntity junction))
                return;

            if (clear) {
                junction.clearFlexibleTarget();
                junction.sendData();
                return;
            }

            if (!LogisticsJunctionBlockEntity.isTargetInRange(pos, targetPos))
                return;
            if (!LogisticsJunctionBlockEntity.isValidTarget(level, targetPos, targetFace))
                return;

            junction.setFlexibleTarget(targetPos, targetFace);
            junction.sendData();
        });
    }

    public record ClientBoundRequest(BlockPos pos) implements CustomPacketPayload {
        public static final Type<ClientBoundRequest> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFluid.MOD_ID, "logistics_junction_request"));

        public static final StreamCodec<ByteBuf, ClientBoundRequest> STREAM_CODEC = BlockPos.STREAM_CODEC.map(
                ClientBoundRequest::new, ClientBoundRequest::pos
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public void handle(IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.flow().isClientbound() && FMLEnvironment.dist == Dist.CLIENT) {
                    LogisticsJunctionSelectionHandler.flushSettings(pos);
                }
            });
        }
    }
}
