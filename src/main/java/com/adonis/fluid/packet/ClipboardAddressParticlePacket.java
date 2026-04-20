package com.adonis.fluid.packet;

import com.adonis.fluid.CreateFluid;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClipboardAddressParticlePacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<ClipboardAddressParticlePacket> TYPE = new Type<>(CreateFluid.asResource("clipboard_address_particle"));
    public static final StreamCodec<ByteBuf, ClipboardAddressParticlePacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ClipboardAddressParticlePacket::pos, ClipboardAddressParticlePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandler.handleClipboardParticle(this.pos);
        });
    }
}
