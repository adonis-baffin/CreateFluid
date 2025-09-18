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
    private final BlockPos pos;

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
            if (player == null || !player.mayBuild()) {
                return;
            }

            Level world = player.level();
            if (!world.isLoaded(this.pos)) {
                return;
            }

            BlockState state = world.getBlockState(this.pos);
            if (!(state.getBlock() instanceof RoseQuartzLampBlock)) {
                return;
            }

            // 检查玩家距离（可选的安全检查）
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
                return; // 距离太远，防止作弊
            }

            // 切换POWERING状态
            BlockState newState = state.cycle(RoseQuartzLampBlock.POWERING);
            world.setBlock(this.pos, newState, 3);

            // 更新邻居方块
            world.updateNeighborsAt(this.pos, state.getBlock());
        });
        return true;
    }
}