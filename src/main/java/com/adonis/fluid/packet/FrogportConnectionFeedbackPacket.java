package com.adonis.fluid.packet;

import com.adonis.fluid.CreateFluid;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
            ClientPacketHandler.handleFrogportFeedback(this.success, this.frogportPos, this.messageKey);
        });
    }
}
