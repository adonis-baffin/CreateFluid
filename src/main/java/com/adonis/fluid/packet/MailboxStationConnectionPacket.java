package com.adonis.fluid.packet;

import com.simibubi.create.content.logistics.packagePort.PackagePortTarget.TrainStationFrogportTarget;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

public class MailboxStationConnectionPacket extends SimplePacketBase {
    private final BlockPos mailboxPos;
    private final BlockPos stationPos;

    public MailboxStationConnectionPacket(BlockPos mailboxPos, BlockPos stationPos) {
        this.mailboxPos = mailboxPos;
        this.stationPos = stationPos;
    }

    public MailboxStationConnectionPacket(FriendlyByteBuf buffer) {
        this.mailboxPos = buffer.readBlockPos();
        this.stationPos = buffer.readBlockPos();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(mailboxPos);
        buffer.writeBlockPos(stationPos);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.mayBuild()) return;

            Level world = player.level();
            if (!world.isLoaded(this.mailboxPos) || !world.isLoaded(this.stationPos)) return;

            if (player.distanceToSqr(this.mailboxPos.getX() + 0.5, this.mailboxPos.getY() + 0.5, this.mailboxPos.getZ() + 0.5) > 64.0) return;

            if (world.getBlockEntity(this.mailboxPos) instanceof PostboxBlockEntity postbox) {
                if (world.getBlockEntity(this.stationPos) instanceof StationBlockEntity station) {
                    GlobalStation globalStation = station.getStation();
                    if (globalStation != null) {
                        postbox.target = new TrainStationFrogportTarget(
                                this.stationPos.subtract(this.mailboxPos)
                        );
                        postbox.notifyUpdate();
                    }
                }
            }
        });
        return true;
    }
}
