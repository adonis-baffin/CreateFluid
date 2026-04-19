package com.adonis.fluid.packet;

import com.adonis.fluid.CreateFluid;
import com.simibubi.create.foundation.utility.CreateLang;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record FrogportConnectionFeedbackPacket(boolean success, BlockPos frogportPos, String messageKey) implements CustomPacketPayload {
    public static final Type<FrogportConnectionFeedbackPacket> TYPE = new Type<>(CreateFluid.asResource("frogport_connection_feedback"));
    public static final StreamCodec<ByteBuf, FrogportConnectionFeedbackPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, FrogportConnectionFeedbackPacket::success,
            BlockPos.STREAM_CODEC, FrogportConnectionFeedbackPacket::frogportPos,
            ByteBufCodecs.STRING_UTF8, FrogportConnectionFeedbackPacket::messageKey,
            FrogportConnectionFeedbackPacket::new
    );

    public static FrogportConnectionFeedbackPacket success(BlockPos frogportPos) {
        return new FrogportConnectionFeedbackPacket(true, frogportPos, "create_fluid.baton.frogport.connection_set");
    }

    public static FrogportConnectionFeedbackPacket failure(BlockPos frogportPos, String messageKey) {
        return new FrogportConnectionFeedbackPacket(false, frogportPos, messageKey);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null) {
                LangBuilder builder = CreateLang.builder().translate(this.messageKey);
                if (this.success) {
                    builder.color(10416499).sendStatus(mc.player);
                    mc.level.playLocalSound(
                            (double) this.frogportPos.getX() + 0.5,
                            (double) this.frogportPos.getY() + 0.5,
                            (double) this.frogportPos.getZ() + 0.5,
                            SoundEvents.NOTE_BLOCK_CHIME.value(),
                            SoundSource.BLOCKS,
                            0.8F,
                            1.0F,
                            false
                    );
                } else {
                    builder.color(16736625).sendStatus(mc.player);
                }
            }
        });
    }
}
