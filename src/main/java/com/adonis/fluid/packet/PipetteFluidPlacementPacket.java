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
                pipette.setInteractionPointTag(pointsTag);
                pipette.setChanged();
                pipette.sendData();
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