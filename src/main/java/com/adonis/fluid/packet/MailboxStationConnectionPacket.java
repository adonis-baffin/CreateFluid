package com.adonis.fluid.packet;

import com.adonis.fluid.CreateFluid;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget.TrainStationFrogportTarget;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MailboxStationConnectionPacket(BlockPos mailboxPos, BlockPos stationPos) implements CustomPacketPayload {
    public static final Type<MailboxStationConnectionPacket> TYPE = new Type<>(CreateFluid.asResource("mailbox_station_connection"));
    public static final StreamCodec<ByteBuf, MailboxStationConnectionPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MailboxStationConnectionPacket::mailboxPos,
            BlockPos.STREAM_CODEC, MailboxStationConnectionPacket::stationPos,
            MailboxStationConnectionPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.mayBuild()) return;

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
    }
}
