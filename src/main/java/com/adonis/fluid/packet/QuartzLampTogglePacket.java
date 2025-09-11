package com.adonis.fluid.packet;

import com.simibubi.create.content.redstone.RoseQuartzLampBlock;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;

public class QuartzLampTogglePacket extends SimplePacketBase {
    private BlockPos pos;

    public QuartzLampTogglePacket(BlockPos pos) {
        this.pos = pos;
    }

    public QuartzLampTogglePacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.mayBuild()) {
                Level world = player.level();
                if (world != null && world.isLoaded(this.pos)) {
                    BlockState state = world.getBlockState(this.pos);

                    if (state.getBlock() instanceof RoseQuartzLampBlock) {
                        // 切换POWERING状态
                        BlockState newState = state.cycle(RoseQuartzLampBlock.POWERING);
                        world.setBlock(this.pos, newState, 3);

                        // 更新邻居
                        world.updateNeighborsAt(this.pos, state.getBlock());
                    }
                }
            }
        });
        return true;
    }
}