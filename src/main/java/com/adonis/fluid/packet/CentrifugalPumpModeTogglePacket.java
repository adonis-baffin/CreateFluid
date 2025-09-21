package com.adonis.fluid.packet;

import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlockEntity;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

public class CentrifugalPumpModeTogglePacket extends SimplePacketBase {
    private final BlockPos pos;

    public CentrifugalPumpModeTogglePacket(BlockPos pos) {
        this.pos = pos;
    }

    public CentrifugalPumpModeTogglePacket(FriendlyByteBuf buffer) {
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
            if (player != null) {
                Level level = player.level();
                BlockEntity be = level.getBlockEntity(pos);

                if (be instanceof CentrifugalPumpBlockEntity pump) {
                    if (pump.pumpMode != null) {
                        // 使用正确的方法来切换模式
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
        return true;
    }
}