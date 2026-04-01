package com.adonis.fluid.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.adonis.fluid.CreateFluid;

public record CopperTapParticlePacket(ParticleType particleType, Vec3 startPos, Vec3 endPos, FluidStack fluid) implements CustomPacketPayload {

    public enum ParticleType {
        STREAM,  // 流体流（用于加工）
        DRIP     // 滴水效果
    }

    public static final Type<CopperTapParticlePacket> TYPE = new Type<>(CreateFluid.asResource("copper_tap_particle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CopperTapParticlePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> packet.encode(buf),  // Encoder
                    CopperTapParticlePacket::decode       // Decoder
            );

    // 兼容旧代码的构造函数
    public CopperTapParticlePacket(Vec3 startPos, Vec3 endPos, FluidStack fluid) {
        this(ParticleType.STREAM, startPos, endPos, fluid);
    }

    public static CopperTapParticlePacket decode(RegistryFriendlyByteBuf buffer) {
        ParticleType type = buffer.readEnum(ParticleType.class);
        Vec3 startPos = new Vec3(
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
        );
        Vec3 endPos = new Vec3(
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
        );
        FluidStack fluid = FluidStack.OPTIONAL_STREAM_CODEC.decode(buffer);
        return new CopperTapParticlePacket(type, startPos, endPos, fluid);
    }

    public void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeEnum(particleType);
        buffer.writeDouble(startPos.x);
        buffer.writeDouble(startPos.y);
        buffer.writeDouble(startPos.z);
        buffer.writeDouble(endPos.x);
        buffer.writeDouble(endPos.y);
        buffer.writeDouble(endPos.z);
        FluidStack.OPTIONAL_STREAM_CODEC.encode(buffer, fluid);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // CopperTapParticlePacket.java
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientHandler.spawnParticles(particleType, startPos, endPos, fluid);
            }
        });
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        private static void spawnParticles(ParticleType type, Vec3 startPos, Vec3 endPos, FluidStack fluid) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            net.minecraft.world.level.Level level = mc.level;
            if (level == null || fluid.isEmpty()) return;

            net.minecraft.core.particles.ParticleOptions particle =
                    com.simibubi.create.content.fluids.FluidFX.getFluidParticle(fluid);

            switch (type) {
                case STREAM -> spawnStreamParticles(level, particle, startPos, endPos);
                case DRIP -> spawnDripParticles(level, particle, startPos);
            }
        }

        private static void spawnStreamParticles(net.minecraft.world.level.Level level, 
                                                  net.minecraft.core.particles.ParticleOptions particle,
                                                  Vec3 startPos, Vec3 endPos) {
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

        private static void spawnDripParticles(net.minecraft.world.level.Level level,
                                                net.minecraft.core.particles.ParticleOptions particle,
                                                Vec3 spoutPos) {
            // 阶段1：悬挂在出水口的水滴
            for (int i = 0; i < 2; i++) {
                double yOffset = -0.05 * i;
                level.addParticle(particle,
                        spoutPos.x, spoutPos.y + yOffset, spoutPos.z,
                        0.005, 0.0, 0.005
                );
            }

            // 阶段2：脱离出水口的水滴
            level.addParticle(particle,
                    spoutPos.x, spoutPos.y - 0.15, spoutPos.z,
                    0.01, -0.15, 0.01
            );

            // 阶段3：落下途中的水滴
            level.addParticle(particle,
                    spoutPos.x, spoutPos.y - 0.3, spoutPos.z,
                    0.015, -0.25, 0.015
            );
        }

        private static Vec3 offsetRandomly(Vec3 vec, net.minecraft.util.RandomSource random, float maxOffset) {
            return new Vec3(
                    vec.x + (random.nextFloat() - 0.5) * 2 * maxOffset,
                    vec.y + (random.nextFloat() - 0.5) * 2 * maxOffset,
                    vec.z + (random.nextFloat() - 0.5) * 2 * maxOffset
            );
        }
    }
}