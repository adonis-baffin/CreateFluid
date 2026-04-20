package com.adonis.fluid.packet;

import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.joml.Vector3f;

import java.util.Random;

public class ClipboardAddressParticlePacket extends SimplePacketBase {
    private final BlockPos pos;

    public ClipboardAddressParticlePacket(BlockPos pos) {
        this.pos = pos;
    }

    public ClipboardAddressParticlePacket(FriendlyByteBuf buffer) {
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
                spawnParticles(this.pos);
                playSound(this.pos);
            });
        });
        return true;
    }

    @OnlyIn(Dist.CLIENT)
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

    @OnlyIn(Dist.CLIENT)
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
