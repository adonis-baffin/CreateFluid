// PipetteParticlePacket.java
package com.adonis.fluid.packet;

import com.adonis.fluid.CreateFluid;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PipetteParticlePacket(Vec3 pos, FluidStack fluid) implements CustomPacketPayload {

    public static final Type<PipetteParticlePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFluid.MOD_ID, "pipette_particle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PipetteParticlePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PipetteParticlePacket decode(RegistryFriendlyByteBuf buffer) {
            double x = buffer.readDouble();
            double y = buffer.readDouble();
            double z = buffer.readDouble();
            Vec3 pos = new Vec3(x, y, z);
            FluidStack fluid = FluidStack.OPTIONAL_STREAM_CODEC.decode(buffer);
            return new PipetteParticlePacket(pos, fluid);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, PipetteParticlePacket packet) {
            buffer.writeDouble(packet.pos.x);
            buffer.writeDouble(packet.pos.y);
            buffer.writeDouble(packet.pos.z);
            FluidStack.OPTIONAL_STREAM_CODEC.encode(buffer, packet.fluid);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound() && FMLEnvironment.dist == Dist.CLIENT) {
                ClientPacketHandler.handlePipetteParticle(this.pos, this.fluid);
            }
        });
    }
}