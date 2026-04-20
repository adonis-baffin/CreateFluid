package com.adonis.fluid.packet;

import com.adonis.fluid.CreateFluid;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

public class FrogportConnectionPacket extends SimplePacketBase {
    private final BlockPos frogportPos;
    private final BlockPos chainConveyorPos;
    private final float chainPosition;
    private final BlockPos connection;

    public FrogportConnectionPacket(BlockPos frogportPos, BlockPos chainConveyorPos, float chainPosition, BlockPos connection) {
        this.frogportPos = frogportPos;
        this.chainConveyorPos = chainConveyorPos;
        this.chainPosition = chainPosition;
        this.connection = connection;
    }

    public FrogportConnectionPacket(FriendlyByteBuf buffer) {
        this.frogportPos = buffer.readBlockPos();
        this.chainConveyorPos = buffer.readBlockPos();
        this.chainPosition = buffer.readFloat();
        if (buffer.readBoolean()) {
            this.connection = buffer.readBlockPos();
        } else {
            this.connection = null;
        }
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(frogportPos);
        buffer.writeBlockPos(chainConveyorPos);
        buffer.writeFloat(chainPosition);
        if (connection != null) {
            buffer.writeBoolean(true);
            buffer.writeBlockPos(connection);
        } else {
            buffer.writeBoolean(false);
        }
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            try {
                ServerPlayer player = context.getSender();
                if (player == null) {
                    return;
                }

                if (!player.mayBuild()) {
                    com.simibubi.create.AllPackets.getChannel().send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                            FrogportConnectionFeedbackPacket.failure(this.frogportPos, "create_fluid.baton.frogport.invalid_chain")
                    );
                    return;
                }

                Level world = player.level();
                if (!world.isLoaded(this.frogportPos) || !world.isLoaded(this.chainConveyorPos)) {
                    com.simibubi.create.AllPackets.getChannel().send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                            FrogportConnectionFeedbackPacket.failure(this.frogportPos, "create_fluid.baton.frogport.invalid_chain")
                    );
                    return;
                }

                if (player.distanceToSqr(
                        (double) this.frogportPos.getX() + 0.5,
                        (double) this.frogportPos.getY() + 0.5,
                        (double) this.frogportPos.getZ() + 0.5) > 64.0) {
                    com.simibubi.create.AllPackets.getChannel().send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                            FrogportConnectionFeedbackPacket.failure(this.frogportPos, "create_fluid.baton.frogport.invalid_chain")
                    );
                    return;
                }

                if (!(world.getBlockEntity(this.frogportPos) instanceof PackagePortBlockEntity frogport)) {
                    com.simibubi.create.AllPackets.getChannel().send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                            FrogportConnectionFeedbackPacket.failure(this.frogportPos, "create_fluid.baton.frogport.invalid_chain")
                    );
                    return;
                }

                if (!(world.getBlockEntity(this.chainConveyorPos) instanceof ChainConveyorBlockEntity)) {
                    com.simibubi.create.AllPackets.getChannel().send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                            FrogportConnectionFeedbackPacket.failure(this.frogportPos, "create_fluid.baton.frogport.invalid_chain")
                    );
                    return;
                }

                BlockPos relativePos = this.chainConveyorPos.subtract(this.frogportPos);
                PackagePortTarget.ChainConveyorFrogportTarget newTarget = new PackagePortTarget.ChainConveyorFrogportTarget(
                        relativePos, this.chainPosition, this.connection);

                if (!newTarget.canSupport(frogport)) {
                    com.simibubi.create.AllPackets.getChannel().send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                            FrogportConnectionFeedbackPacket.failure(this.frogportPos, "create_fluid.baton.frogport.invalid_chain")
                    );
                    return;
                }

                Vec3 targetLocation = newTarget.getExactTargetLocation(frogport, world, this.frogportPos);
                if (targetLocation == Vec3.ZERO
                        || !targetLocation.closerThan(
                        Vec3.atBottomCenterOf(this.frogportPos),
                        (double) AllConfigs.server().logistics.packagePortRange.get() + 2)) {
                    com.simibubi.create.AllPackets.getChannel().send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                            FrogportConnectionFeedbackPacket.failure(this.frogportPos, "create_fluid.baton.frogport.invalid_chain")
                    );
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

                com.simibubi.create.AllPackets.getChannel().send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                        FrogportConnectionFeedbackPacket.success(this.frogportPos)
                );
            } catch (Exception e) {
                CreateFluid.LOGGER.error("Error processing FrogportConnectionPacket", e);
                ServerPlayer sp = context.getSender();
                if (sp != null) {
                    com.simibubi.create.AllPackets.getChannel().send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                            FrogportConnectionFeedbackPacket.failure(this.frogportPos, "create_fluid.baton.frogport.invalid_chain")
                    );
                }
            }
        });
        return true;
    }
}
