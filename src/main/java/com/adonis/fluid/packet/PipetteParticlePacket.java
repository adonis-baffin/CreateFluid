package com.adonis.fluid.packet;

import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.NetworkEvent;

public class PipetteParticlePacket extends SimplePacketBase {
    private final Vec3 pos;
    private final FluidStack fluid;

    public PipetteParticlePacket(Vec3 pos, FluidStack fluid) {
        this.pos = pos;
        this.fluid = fluid;
    }

    public PipetteParticlePacket(FriendlyByteBuf buffer) {
        this.pos = new Vec3(
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
        );
        this.fluid = FluidStack.readFromPacket(buffer);
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeDouble(pos.x);
        buffer.writeDouble(pos.y);
        buffer.writeDouble(pos.z);
        fluid.writeToPacket(buffer);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    ClientHandler.handleParticle(pos, fluid)
            );
        });
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        private static void handleParticle(Vec3 pos, FluidStack fluid) {
            // 现在这里可以安全地导入客户端类
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            net.minecraft.world.level.Level level = mc.level;
            if (level == null || fluid.isEmpty()) return;

            net.minecraft.core.particles.ParticleOptions particle =
                    com.simibubi.create.content.fluids.FluidFX.getFluidParticle(fluid);

            for (int i = 0; i < 20; i++) {
                Vec3 motion = net.createmod.catnip.math.VecHelper.offsetRandomly(
                        Vec3.ZERO, level.random, 0.125F);
                motion = new Vec3(motion.x, -Math.abs(motion.y) * 0.5 - 0.1, motion.z);
                level.addParticle(particle,
                        pos.x, pos.y, pos.z,
                        motion.x, motion.y, motion.z
                );
            }
        }
    }
}