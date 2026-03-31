package com.adonis.fluid.packet;

import com.adonis.fluid.CreateFluid;
import com.simibubi.create.content.redstone.RoseQuartzLampBlock;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record QuartzLampTogglePacket(BlockPos pos) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<QuartzLampTogglePacket> TYPE = 
            new CustomPacketPayload.Type<>(CreateFluid.asResource("quartz_lamp_toggle"));
    
    public static final StreamCodec<ByteBuf, QuartzLampTogglePacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            QuartzLampTogglePacket::pos,
            QuartzLampTogglePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {  // 改为实例方法
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (!player.mayBuild()) {
                    return;
                }

                Level world = player.level();
                if (!world.isLoaded(this.pos)) {  // 使用 this.pos
                    return;
                }

                BlockState state = world.getBlockState(this.pos);
                if (!(state.getBlock() instanceof RoseQuartzLampBlock)) {
                    return;
                }

                if (player.distanceToSqr(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) > 64.0) {
                    return;
                }

                BlockState newState = state.cycle(RoseQuartzLampBlock.POWERING);
                world.setBlock(this.pos, newState, 3);
                world.updateNeighborsAt(this.pos, state.getBlock());
            }
        });
    }
}