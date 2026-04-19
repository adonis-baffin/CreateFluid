package com.adonis.fluid.packet;

import com.adonis.fluid.CreateFluid;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Vector3f;

import java.util.Random;

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
            spawnParticles(this.pos);
            playSound(this.pos);
        });
    }

    public static void spawnParticles(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Random random = new Random();

            for (int i = 0; i < 10; i++) {
                double x = (double) pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
                double y = (double) pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
                double z = (double) pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
                mc.level.addParticle(new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 1.0F), x, y, z, 0.0, 0.0, 0.0);
            }
        }
    }

    public static void playSound(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.level.playLocalSound(
                    (double) pos.getX() + 0.5,
                    (double) pos.getY() + 0.5,
                    (double) pos.getZ() + 0.5,
                    SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.BLOCKS,
                    0.5F,
                    1.0F,
                    false
            );
        }
    }
}
