package com.adonis.fluid.packet;

import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.NetworkEvent;

public class CopperTapParticlePacket extends SimplePacketBase {
    private final Vec3 startPos;
    private final Vec3 endPos;
    private final FluidStack fluid;

    public CopperTapParticlePacket(Vec3 startPos, Vec3 endPos, FluidStack fluid) {
        this.startPos = startPos;
        this.endPos = endPos;
        this.fluid = fluid;
    }

    public CopperTapParticlePacket(FriendlyByteBuf buffer) {
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
                    ClientHandler.spawnParticles(startPos, endPos, fluid)
            );
        });
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        private static void spawnParticles(Vec3 startPos, Vec3 endPos, FluidStack fluid) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            net.minecraft.world.level.Level level = mc.level;
            if (level == null || fluid.isEmpty()) return;

            net.minecraft.core.particles.ParticleOptions particle =
                    com.simibubi.create.content.fluids.FluidFX.getFluidParticle(fluid);

            // 创建流体流效果
            Vec3 flowDirection = endPos.subtract(startPos);
            double distance = flowDirection.length();
            Vec3 normalizedFlow = flowDirection.normalize();

            int particleCount = (int) (distance * 8);
            for (int i = 0; i < particleCount; i++) {
                float progress = i / (float) particleCount;
                Vec3 particlePos = startPos.add(normalizedFlow.scale(distance * progress));

                Vec3 offset = net.createmod.catnip.math.VecHelper.offsetRandomly(
                        Vec3.ZERO, level.random, 0.02F);
                particlePos = particlePos.add(offset.x, 0, offset.z);

                level.addParticle(particle,
                        particlePos.x, particlePos.y, particlePos.z,
                        offset.x * 0.1, -0.05, offset.z * 0.1
                );
            }

            // 飞溅效果
            for (int i = 0; i < 10; i++) {
                Vec3 splash = net.createmod.catnip.math.VecHelper.offsetRandomly(
                        Vec3.ZERO, level.random, 0.15F);
                splash = new Vec3(splash.x, Math.abs(splash.y) * 0.3, splash.z);

                level.addParticle(particle,
                        endPos.x, endPos.y, endPos.z,
                        splash.x, splash.y, splash.z
                );
            }
        }
    }
}