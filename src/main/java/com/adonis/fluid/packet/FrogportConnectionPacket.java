package com.adonis.fluid.packet;

import com.adonis.fluid.CreateFluid;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.infrastructure.config.AllConfigs;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record FrogportConnectionPacket(BlockPos frogportPos, BlockPos chainConveyorPos, float chainPosition, BlockPos connection) implements CustomPacketPayload {
    public static final Type<FrogportConnectionPacket> TYPE = new Type<>(CreateFluid.asResource("frogport_connection"));
    public static final StreamCodec<ByteBuf, FrogportConnectionPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                BlockPos.STREAM_CODEC.encode(buffer, packet.frogportPos);
                BlockPos.STREAM_CODEC.encode(buffer, packet.chainConveyorPos);
                buffer.writeFloat(packet.chainPosition);
                if (packet.connection != null) {
                    buffer.writeBoolean(true);
                    BlockPos.STREAM_CODEC.encode(buffer, packet.connection);
                } else {
                    buffer.writeBoolean(false);
                }
            },
            buffer -> {
                BlockPos frogportPos = BlockPos.STREAM_CODEC.decode(buffer);
                BlockPos chainConveyorPos = BlockPos.STREAM_CODEC.decode(buffer);
                float chainPosition = buffer.readFloat();
                BlockPos connection = null;
                if (buffer.readBoolean()) {
                    connection = BlockPos.STREAM_CODEC.decode(buffer);
                }
                return new FrogportConnectionPacket(frogportPos, chainConveyorPos, chainPosition, connection);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                if (!(context.player() instanceof ServerPlayer player)) {
                    return;
                }

                if (!player.mayBuild()) {
                    PacketDistributor.sendToPlayer(player,
                            FrogportConnectionFeedbackPacket.failure(this.frogportPos, "create_fluid.baton.frogport.invalid_chain"));
                    return;
                }

                Level world = player.level();
                if (!world.isLoaded(this.frogportPos) || !world.isLoaded(this.chainConveyorPos)) {
                    PacketDistributor.sendToPlayer(player,
                            FrogportConnectionFeedbackPacket.failure(this.frogportPos, "create_fluid.baton.frogport.invalid_chain"));
                    return;
                }

                if (player.distanceToSqr(
                        (double) this.frogportPos.getX() + 0.5,
                        (double) this.frogportPos.getY() + 0.5,
                        (double) this.frogportPos.getZ() + 0.5) > 64.0) {
                    PacketDistributor.sendToPlayer(player,
                            FrogportConnectionFeedbackPacket.failure(this.frogportPos, "create_fluid.baton.frogport.invalid_chain"));
                    return;
                }

                if (!(world.getBlockEntity(this.frogportPos) instanceof PackagePortBlockEntity frogport)) {
                    PacketDistributor.sendToPlayer(player,
                            FrogportConnectionFeedbackPacket.failure(this.frogportPos, "create_fluid.baton.frogport.invalid_chain"));
                    return;
                }

                if (!(world.getBlockEntity(this.chainConveyorPos) instanceof ChainConveyorBlockEntity)) {
                    PacketDistributor.sendToPlayer(player,
                            FrogportConnectionFeedbackPacket.failure(this.frogportPos, "create_fluid.baton.frogport.invalid_chain"));
                    return;
                }

                BlockPos relativePos = this.chainConveyorPos.subtract(this.frogportPos);
                PackagePortTarget.ChainConveyorFrogportTarget newTarget = new PackagePortTarget.ChainConveyorFrogportTarget(
                        relativePos, this.chainPosition, this.connection, false);

                if (!newTarget.canSupport(frogport)) {
                    PacketDistributor.sendToPlayer(player,
                            FrogportConnectionFeedbackPacket.failure(this.frogportPos, "create_fluid.baton.frogport.invalid_chain"));
                    return;
                }

                Vec3 targetLocation = newTarget.getExactTargetLocation(frogport, world, this.frogportPos);
                if (targetLocation == Vec3.ZERO
                        || !targetLocation.closerThan(
                        Vec3.atBottomCenterOf(this.frogportPos),
                        (double) AllConfigs.server().logistics.packagePortRange.get() + 2)) {
                    PacketDistributor.sendToPlayer(player,
                            FrogportConnectionFeedbackPacket.failure(this.frogportPos, "create_fluid.baton.frogport.invalid_chain"));
                    return;
                }

                if (frogport.target != null) {
                    frogport.target.deregister(frogport, world, this.frogportPos);
                }

                newTarget.setup(frogport, world, this.frogportPos);
                frogport.target = newTarget;
                newTarget.register(frogport, world, this.frogportPos);
                frogport.notifyUpdate();
                frogport.filterChanged();

                PacketDistributor.sendToPlayer(player,
                        FrogportConnectionFeedbackPacket.success(this.frogportPos));
            } catch (Exception e) {
                CreateFluid.LOGGER.error("Error processing FrogportConnectionPacket", e);
                if (context.player() instanceof ServerPlayer sp) {
                    PacketDistributor.sendToPlayer(sp,
                            FrogportConnectionFeedbackPacket.failure(this.frogportPos, "create_fluid.baton.frogport.invalid_chain"));
                }
            }
        });
    }
}
