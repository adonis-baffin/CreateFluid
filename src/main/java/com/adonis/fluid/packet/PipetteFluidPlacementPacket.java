package com.adonis.fluid.packet;

import com.adonis.fluid.block.Pipette.PipetteBlockEntity;
import com.adonis.fluid.content.pipette.FluidInteractionPoint;
import com.adonis.fluid.handler.PipetteFluidInteractionPointHandler;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;
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
    private Collection<FluidInteractionPoint> points;
    private ListTag receivedTag;
    private BlockPos pos;

    public PipetteFluidPlacementPacket(Collection<FluidInteractionPoint> points, BlockPos pos) {
        this.points = points;
        this.pos = pos;
    }

    public PipetteFluidPlacementPacket(FriendlyByteBuf buffer) {
        CompoundTag nbt = buffer.readNbt();
        if (nbt != null) {
            this.receivedTag = nbt.getList("Points", 10);
        }
        this.pos = buffer.readBlockPos();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        CompoundTag nbt = new CompoundTag();
        ListTag pointsNBT = new ListTag();
        Stream<CompoundTag> stream = this.points.stream().map(fip -> fip.serialize(this.pos));
        Objects.requireNonNull(pointsNBT);
        stream.forEach(pointsNBT::add);
        nbt.put("Points", pointsNBT);
        buffer.writeNbt(nbt);
        buffer.writeBlockPos(this.pos);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                Level world = player.level();
                if (world != null && world.isLoaded(this.pos)) {
                    BlockEntity blockEntity = world.getBlockEntity(this.pos);
                    if (blockEntity instanceof PipetteBlockEntity) {
                        PipetteBlockEntity pipette = (PipetteBlockEntity)blockEntity;
                        pipette.setInteractionPointTag(this.receivedTag);
                        pipette.setChanged();
                        pipette.sendData();
                    }
                }
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
            buffer.writeBlockPos(this.pos);
        }

        @Override
        public boolean handle(NetworkEvent.Context context) {
            context.enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    PipetteFluidInteractionPointHandler.flushSettings(this.pos);
                });
            });
            return true;
        }
    }
}