package com.adonis.fluid.packet;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.block.Pipette.PipetteBlockEntity;
import com.adonis.fluid.content.pipette.FluidInteractionPoint;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Collection;

public record PipetteFluidPlacementPacket(ListTag pointsTag, BlockPos pos) implements CustomPacketPayload {

    public static final Type<PipetteFluidPlacementPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFluid.MOD_ID, "pipette_placement"));

    public static final StreamCodec<ByteBuf, PipetteFluidPlacementPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG.map(
                    tag -> tag.getList("Points", 10),
                    points -> {
                        CompoundTag tag = new CompoundTag();
                        tag.put("Points", points);
                        return tag;
                    }
            ),
            PipetteFluidPlacementPacket::pointsTag,
            BlockPos.STREAM_CODEC,
            PipetteFluidPlacementPacket::pos,
            PipetteFluidPlacementPacket::new
    );

    public PipetteFluidPlacementPacket(Collection<FluidInteractionPoint> points, BlockPos pos) {
        this(createPointsTag(points, pos), pos);
    }

    private static ListTag createPointsTag(Collection<FluidInteractionPoint> points, BlockPos pos) {
        ListTag tag = new ListTag();
        points.stream()
                .map(point -> point.serialize(pos))
                .forEach(tag::add);
        return tag;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            Level world = player.level();
            if (!world.isLoaded(this.pos)) return;

            BlockEntity be = world.getBlockEntity(this.pos);
            if (be instanceof PipetteBlockEntity pipette) {
                pipette.inputs.clear();
                pipette.outputs.clear();

                pipette.setInteractionPointTag(this.pointsTag);
                pipette.setUpdateInteractionPoints(true);
                pipette.resetMovementState();

                // 立即初始化
                pipette.forceInitInteractionPoints();

                pipette.setChanged();
                pipette.sendData();

                // 发送客户端同步包
                if (world instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    PipetteInteractionPointSyncPacket syncPacket =
                            new PipetteInteractionPointSyncPacket(this.pos, this.pointsTag);

                    try {
                        PacketDistributor.sendToPlayersTrackingChunk(
                                serverLevel,
                                new net.minecraft.world.level.ChunkPos(this.pos),
                                syncPacket
                        );
                    } catch (Exception e) {
                        // 静默处理
                    }
                }
            }
        });
    }

    // ========== ClientBoundRequest：服务端请求客户端刷新设置 ==========
    public record ClientBoundRequest(BlockPos pos) implements CustomPacketPayload {

        public static final Type<ClientBoundRequest> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFluid.MOD_ID, "pipette_request"));

        public static final StreamCodec<ByteBuf, ClientBoundRequest> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC,
                ClientBoundRequest::pos,
                ClientBoundRequest::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        // PipetteFluidPlacementPacket.java 中的 ClientBoundRequest
        public void handle(IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.flow().isClientbound() && FMLEnvironment.dist == Dist.CLIENT) {
                    ClientPacketHandler.handlePipetteRequest(this.pos);
                }
            });
        }

        private void handleClient() {
            // 调用客户端处理器刷新设置
            com.adonis.fluid.handler.PipetteFluidInteractionPointHandler.flushSettings(this.pos);
        }
    }
}