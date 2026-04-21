package com.adonis.fluid.datacomponent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.fluids.FluidStack;

public record FluidManifestContent(FluidStack fluid) {
	public static final Codec<FluidManifestContent> CODEC = RecordCodecBuilder.create(instance ->
		instance.group(
			FluidStack.CODEC.fieldOf("fluid").forGetter(FluidManifestContent::fluid)
		).apply(instance, FluidManifestContent::new)
	);

	public static final StreamCodec<RegistryFriendlyByteBuf, FluidManifestContent> STREAM_CODEC = StreamCodec.composite(
		FluidStack.STREAM_CODEC,
		FluidManifestContent::fluid,
		FluidManifestContent::new
	);
}
