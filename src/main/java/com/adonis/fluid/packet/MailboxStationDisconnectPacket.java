package com.adonis.fluid.packet;

import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

public class MailboxStationDisconnectPacket extends SimplePacketBase {
    private final BlockPos mailboxPos;

    public MailboxStationDisconnectPacket(BlockPos mailboxPos) {
        this.mailboxPos = mailboxPos;
    }

    public MailboxStationDisconnectPacket(FriendlyByteBuf buffer) {
        this.mailboxPos = buffer.readBlockPos();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(mailboxPos);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.mayBuild()) return;

            Level world = player.level();
            if (!world.isLoaded(this.mailboxPos)) return;

            if (player.distanceToSqr(this.mailboxPos.getX() + 0.5, this.mailboxPos.getY() + 0.5, this.mailboxPos.getZ() + 0.5) > 64.0) return;

            if (world.getBlockEntity(this.mailboxPos) instanceof PostboxBlockEntity postbox) {
                postbox.target = null;
                postbox.notifyUpdate();
            }
        });
        return true;
    }
}
