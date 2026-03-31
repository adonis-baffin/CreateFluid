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

public record CopperTapParticlePacket(Vec3 startPos, Vec3 endPos, FluidStack fluid) implements CustomPacketPayload {

    public static final Type<CopperTapParticlePacket> TYPE = new Type<>(CreateFluid.asResource("copper_tap_particle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CopperTapParticlePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> packet.encode(buf),  // Encoder
                    CopperTapParticlePacket::decode       // Decoder
            );

    public static CopperTapParticlePacket decode(RegistryFriendlyByteBuf buffer) {
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
        return new CopperTapParticlePacket(startPos, endPos, fluid);
    }

    public void encode(RegistryFriendlyByteBuf buffer) {
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
                ClientHandler.spawnParticles(startPos, endPos, fluid);
            }
        });
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        private static void spawnParticles(Vec3 startPos, Vec3 endPos, FluidStack fluid) {
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

        private static Vec3 offsetRandomly(Vec3 vec, net.minecraft.util.RandomSource random, float maxOffset) {
            return new Vec3(
                    vec.x + (random.nextFloat() - 0.5) * 2 * maxOffset,
                    vec.y + (random.nextFloat() - 0.5) * 2 * maxOffset,
                    vec.z + (random.nextFloat() - 0.5) * 2 * maxOffset
            );
        }
    }
}