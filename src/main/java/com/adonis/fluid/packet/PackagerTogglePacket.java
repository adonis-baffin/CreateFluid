package com.adonis.fluid.packet;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;

public class PackagerTogglePacket extends SimplePacketBase {
    private final BlockPos pos;

    public PackagerTogglePacket(BlockPos pos) {
        this.pos = pos;
    }

    public PackagerTogglePacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.mayBuild()) return;

            Level world = player.level();
            if (!world.isLoaded(this.pos)) return;

            BlockState state = world.getBlockState(this.pos);
            if (!AllBlocks.PACKAGER.has(state) && !AllBlocks.REPACKAGER.has(state)) return;

            if (player.distanceToSqr(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) > 64.0) return;

            boolean isPowered = state.getValue(PackagerBlock.POWERED);
            BlockState newState = state.cycle(PackagerBlock.POWERED);
            world.setBlock(this.pos, newState, 2);
            world.updateNeighborsAt(this.pos, newState.getBlock());
            if (world.getBlockEntity(this.pos) instanceof PackagerBlockEntity packager) {
                packager.redstonePowered = !isPowered;
                packager.setChanged();
                if (!isPowered) {
                    packager.activate();
                }
            }
        });
        return true;
    }
}
