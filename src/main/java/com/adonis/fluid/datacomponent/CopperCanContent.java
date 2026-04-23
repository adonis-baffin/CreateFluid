package com.adonis.fluid.datacomponent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.fluids.FluidStack;

public record CopperCanContent(FluidStack fluid, int capacity) {
	public static final int DEFAULT_CAPACITY = 10000;

	public static final Codec<CopperCanContent> CODEC = RecordCodecBuilder.create(instance ->
		instance.group(
			FluidStack.CODEC.fieldOf("fluid").forGetter(CopperCanContent::fluid),
			Codec.INT.fieldOf("capacity").forGetter(CopperCanContent::capacity)
		).apply(instance, CopperCanContent::new)
	);

	public static final StreamCodec<RegistryFriendlyByteBuf, CopperCanContent> STREAM_CODEC = StreamCodec.composite(
		FluidStack.STREAM_CODEC,
		CopperCanContent::fluid,
		ByteBufCodecs.VAR_INT,
		CopperCanContent::capacity,
		CopperCanContent::new
	);

	public boolean isEmpty() {
		return fluid == null || fluid.isEmpty();
	}

	public int amount() {
		return isEmpty() ? 0 : fluid.getAmount();
	}
}
