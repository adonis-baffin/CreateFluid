package com.adonis.fluid.packet;

import com.adonis.fluid.block.Pipette.PipetteBlockEntity;
import com.adonis.fluid.content.pipette.FluidInteractionPoint;
import com.adonis.fluid.handler.PipetteFluidInteractionPointHandler;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public class PipetteFluidPlacementPacket extends SimplePacketBase {
    private ListTag pointsTag;
    private BlockPos pos;

    public PipetteFluidPlacementPacket(Collection<FluidInteractionPoint> points, BlockPos pos) {
        this.pos = pos;
        this.pointsTag = new ListTag();
        points.stream()
                .map(point -> point.serialize(pos))
                .forEach(this.pointsTag::add);
    }

    public PipetteFluidPlacementPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        CompoundTag nbt = buffer.readNbt();
        this.pointsTag = nbt != null ? nbt.getList("Points", 10) : new ListTag();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        CompoundTag nbt = new CompoundTag();
        nbt.put("Points", pointsTag);
        buffer.writeNbt(nbt);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            Level world = player.level();
            if (!world.isLoaded(pos)) return;

            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof PipetteBlockEntity pipette) {
                System.out.println("[SERVER] PipetteFluidPlacementPacket 处理中");
                System.out.println("[SERVER] 目标位置: " + pos);
                System.out.println("[SERVER] 点数: " + pointsTag.size());

                // 清空现有的交互点
                pipette.inputs.clear();
                pipette.outputs.clear();

                // 设置交互点标签
                pipette.setInteractionPointTag(pointsTag);

                // 重置移动状态
                pipette.resetMovementState();

                // 使用强制重新加载方法
                pipette.forceReloadInteractionPoints();

                // 标记更改
                pipette.setChanged();

                // 发送数据
                pipette.sendData();

                // 延迟发送方块更新
                world.getServer().execute(() -> {
                    world.sendBlockUpdated(pos, pipette.getBlockState(), pipette.getBlockState(), 3);
                });
            }
        });
        return true;
    }

    public static class ClientBoundRequest extends SimplePacketBase {
        private BlockPos pos;

        public ClientBoundRequest(BlockPos pos) {
            this.pos = pos;
        }

        public ClientBoundRequest(FriendlyByteBuf buffer) {
            this.pos = buffer.readBlockPos();
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
        }

        @Override
        public boolean handle(NetworkEvent.Context context) {
            context.enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    PipetteFluidInteractionPointHandler.flushSettings(pos);
                });
            });
            return true;
        }
    }
}