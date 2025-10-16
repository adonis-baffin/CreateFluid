package com.adonis.fluid.packet;

import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.NetworkEvent;

public class CopperTapParticlePacket extends SimplePacketBase {
    private final ParticleType particleType;
    private final Vec3 startPos;
    private final Vec3 endPos;
    private final FluidStack fluid;

    public CopperTapParticlePacket(ParticleType particleType, Vec3 startPos, Vec3 endPos, FluidStack fluid) {
        this.particleType = particleType;
        this.startPos = startPos;
        this.endPos = endPos;
        this.fluid = fluid;
    }

    public CopperTapParticlePacket(FriendlyByteBuf buffer) {
        this.particleType = buffer.readEnum(ParticleType.class);
        this.startPos = new Vec3(
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
        );
        this.endPos = new Vec3(
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
        );
        this.fluid = FluidStack.readFromPacket(buffer);
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeEnum(particleType);
        buffer.writeDouble(startPos.x);
        buffer.writeDouble(startPos.y);
        buffer.writeDouble(startPos.z);
        buffer.writeDouble(endPos.x);
        buffer.writeDouble(endPos.y);
        buffer.writeDouble(endPos.z);
        fluid.writeToPacket(buffer);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    ClientHandler.handle(particleType, startPos, endPos, fluid)
            );
        });
        return true;
    }

    public enum ParticleType {
        STREAM, DRIP
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        private static void handle(ParticleType type, Vec3 startPos, Vec3 endPos, FluidStack fluid) {
            switch (type) {
                case STREAM -> spawnStreamParticles(startPos, endPos, fluid);
                case DRIP -> spawnDripEffect(startPos, fluid);
            }
        }

        private static void spawnStreamParticles(Vec3 startPos, Vec3 endPos, FluidStack fluid) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            net.minecraft.world.level.Level level = mc.level;
            if (level == null || fluid.isEmpty()) return;

            net.minecraft.core.particles.ParticleOptions particle =
                    com.simibubi.create.content.fluids.FluidFX.getFluidParticle(fluid);

            // Create fluid stream effect
            Vec3 flowDirection = endPos.subtract(startPos);
            double distance = flowDirection.length();
            Vec3 normalizedFlow = flowDirection.normalize();

            int particleCount = (int) (distance * 8);
            for (int i = 0; i < particleCount; i++) {
                float progress = i / (float) particleCount;
                Vec3 particlePos = startPos.add(normalizedFlow.scale(distance * progress));

                Vec3 offset = offsetRandomly(Vec3.ZERO, level.random, 0.02F);
                particlePos = particlePos.add(offset.x, 0, offset.z);

                level.addParticle(particle,
                        particlePos.x, particlePos.y, particlePos.z,
                        offset.x * 0.1, -0.05, offset.z * 0.1
                );
            }

            // Splash effect
            for (int i = 0; i < 10; i++) {
                Vec3 splash = offsetRandomly(Vec3.ZERO, level.random, 0.15F);
                splash = new Vec3(splash.x, Math.abs(splash.y) * 0.3, splash.z);

                level.addParticle(particle,
                        endPos.x, endPos.y, endPos.z,
                        splash.x, splash.y, splash.z
                );
            }
        }

        private static void spawnDripEffect(Vec3 spoutPos, FluidStack fluid) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            net.minecraft.world.level.Level level = mc.level;
            if (level == null || fluid.isEmpty()) return;

            net.minecraft.core.particles.ParticleOptions fluidParticle = com.simibubi.create.content.fluids.FluidFX.getFluidParticle(fluid);

            // Add a particle hanging from the tap
            level.addParticle(fluidParticle,
                    spoutPos.x, spoutPos.y, spoutPos.z,
                    0, -0.05, 0);

            // Add a particle falling down
            Vec3 fallMotion = offsetRandomly(Vec3.ZERO, level.random, 0.02F);
            level.addParticle(fluidParticle,
                    spoutPos.x, spoutPos.y, spoutPos.z,
                    fallMotion.x, -0.2, fallMotion.z);
        }

        private static Vec3 offsetRandomly(Vec3 vec, RandomSource random, float maxOffset) {
            return new Vec3(
                    vec.x + (random.nextFloat() - 0.5) * 2 * maxOffset,
                    vec.y + (random.nextFloat() - 0.5) * 2 * maxOffset,
                    vec.z + (random.nextFloat() - 0.5) * 2 * maxOffset
            );
        }
    }
}