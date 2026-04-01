package com.adonis.fluid.packet;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CentrifugalPumpModeTogglePacket(BlockPos pos) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<CentrifugalPumpModeTogglePacket> TYPE = 
            new CustomPacketPayload.Type<>(CreateFluid.asResource("centrifugal_pump_mode_toggle"));
    
    public static final StreamCodec<ByteBuf, CentrifugalPumpModeTogglePacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            CentrifugalPumpModeTogglePacket::pos,
            CentrifugalPumpModeTogglePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (!player.mayBuild()) {
                    return;
                }

                Level world = player.level();
                if (!world.isLoaded(this.pos)) {
                    return;
                }

                if (world.getBlockEntity(this.pos) instanceof CentrifugalPumpBlockEntity pump) {
                    if (pump.pumpMode != null) {
                        // 切换模式
                        int currentMode = pump.pumpMode.getValue();
                        int nextMode = (currentMode + 1) % CentrifugalPumpBlockEntity.PumpMode.values().length;
                        pump.pumpMode.setValue(nextMode);

                        // 触发更新
                        pump.onModeChanged();
                        pump.notifyUpdate();
                        pump.sendData();
                    }
                }
            }
        });
    }
}
