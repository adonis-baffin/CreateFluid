package com.adonis.fluid.packet;

import com.adonis.fluid.CreateFluid;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MailboxStationDisconnectPacket(BlockPos mailboxPos) implements CustomPacketPayload {
    public static final Type<MailboxStationDisconnectPacket> TYPE = new Type<>(CreateFluid.asResource("mailbox_station_disconnect"));
    public static final StreamCodec<ByteBuf, MailboxStationDisconnectPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MailboxStationDisconnectPacket::mailboxPos,
            MailboxStationDisconnectPacket::new
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
            if (!world.isLoaded(this.mailboxPos)) return;

            if (player.distanceToSqr(this.mailboxPos.getX() + 0.5, this.mailboxPos.getY() + 0.5, this.mailboxPos.getZ() + 0.5) > 64.0) return;

            if (world.getBlockEntity(this.mailboxPos) instanceof PostboxBlockEntity postbox) {
                postbox.target = null;
                postbox.notifyUpdate();
            }
        });
    }
}
