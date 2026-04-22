package com.adonis.fluid.datacomponent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record FluidManifestContent(ResourceLocation fluidId, int amount) {
	public static final Codec<FluidManifestContent> CODEC = RecordCodecBuilder.create(instance ->
		instance.group(
			ResourceLocation.CODEC.fieldOf("fluid_id").forGetter(FluidManifestContent::fluidId),
			Codec.INT.fieldOf("amount").forGetter(FluidManifestContent::amount)
		).apply(instance, FluidManifestContent::new)
	);

	public static final StreamCodec<RegistryFriendlyByteBuf, FluidManifestContent> STREAM_CODEC = StreamCodec.composite(
		ResourceLocation.STREAM_CODEC,
		FluidManifestContent::fluidId,
		StreamCodec.of(RegistryFriendlyByteBuf::writeInt, RegistryFriendlyByteBuf::readInt),
		FluidManifestContent::amount,
		FluidManifestContent::new
	);
}
