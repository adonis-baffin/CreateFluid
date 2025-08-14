package com.adonis.fluid.packet;

import com.simibubi.create.content.fluids.FluidFX;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.NetworkEvent;

public class PipetteParticlePacket extends SimplePacketBase {
    private Vec3 pos;
    private FluidStack fluid;

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
            // 修复：正确的客户端执行方式
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                spawnParticles();
            });
        });
        return true;
    }

    private void spawnParticles() {
        Level level = Minecraft.getInstance().level;
        if (level == null || fluid.isEmpty()) return;

        ParticleOptions particle = FluidFX.getFluidParticle(fluid);
        // 增加粒子数量和调整位置，模拟注液效果
        for (int i = 0; i < 20; i++) {
            Vec3 motion = VecHelper.offsetRandomly(Vec3.ZERO, level.random, 0.125F);
            motion = new Vec3(motion.x, -Math.abs(motion.y) * 0.5 - 0.1, motion.z);
            level.addParticle(particle,
                    pos.x, pos.y, pos.z,
                    motion.x, motion.y, motion.z
            );
        }
    }
}