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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public class CopperFaucetParticlePacket extends SimplePacketBase {
    private Vec3 startPos;
    private Vec3 endPos;
    private FluidStack fluid;
    
    public CopperFaucetParticlePacket(Vec3 startPos, Vec3 endPos, FluidStack fluid) {
        this.startPos = startPos;
        this.endPos = endPos;
        this.fluid = fluid;
    }
    
    public CopperFaucetParticlePacket(FriendlyByteBuf buffer) {
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
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> this::spawnParticles);
        });
        return true;
    }
    
    private void spawnParticles() {
        Level level = Minecraft.getInstance().level;
        if (level == null || fluid.isEmpty()) return;
        
        ParticleOptions particle = FluidFX.getFluidParticle(fluid);
        
        // 创建流体流效果 - 从龙头到目标
        Vec3 flowDirection = endPos.subtract(startPos);
        double distance = flowDirection.length();
        Vec3 normalizedFlow = flowDirection.normalize();
        
        // 生成连续的流体流粒子
        int particleCount = (int) (distance * 8);
        for (int i = 0; i < particleCount; i++) {
            float progress = i / (float) particleCount;
            Vec3 particlePos = startPos.add(normalizedFlow.scale(distance * progress));
            
            // 添加一些随机偏移使流体看起来更自然
            Vec3 offset = VecHelper.offsetRandomly(Vec3.ZERO, level.random, 0.02F);
            particlePos = particlePos.add(offset.x, 0, offset.z);
            
            // 粒子向下的速度
            level.addParticle(particle,
                    particlePos.x, particlePos.y, particlePos.z,
                    offset.x * 0.1, -0.05, offset.z * 0.1
            );
        }
        
        // 在目标位置生成飞溅效果
        for (int i = 0; i < 10; i++) {
            Vec3 splash = VecHelper.offsetRandomly(Vec3.ZERO, level.random, 0.15F);
            splash = new Vec3(splash.x, Math.abs(splash.y) * 0.3, splash.z);
            
            level.addParticle(particle,
                    endPos.x, endPos.y, endPos.z,
                    splash.x, splash.y, splash.z
            );
        }
    }
}