package com.adonis.fluid.packet;

import com.adonis.fluid.CreateFluid;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;
public record PackagerTogglePacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<PackagerTogglePacket> TYPE = new Type<>(CreateFluid.asResource("packager_toggle"));
    public static final StreamCodec<ByteBuf, PackagerTogglePacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PackagerTogglePacket::pos, PackagerTogglePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (!player.mayBuild()) return;

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
            }
        });
    }
}
