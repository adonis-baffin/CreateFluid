package com.adonis.fluid.datacomponent;

import java.util.List;

import com.adonis.fluid.logistics.data.ContentRoute;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.fluids.FluidStack;

public record BrassBoxFluidContent(List<FluidEntry> fluids) {
	public static final BrassBoxFluidContent EMPTY = new BrassBoxFluidContent(List.of());

	public static final Codec<BrassBoxFluidContent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		FluidEntry.CODEC.listOf().fieldOf("fluids").forGetter(BrassBoxFluidContent::fluids)
	).apply(instance, BrassBoxFluidContent::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, BrassBoxFluidContent> STREAM_CODEC = StreamCodec.composite(
		CatnipStreamCodecBuilders.list(FluidEntry.STREAM_CODEC), BrassBoxFluidContent::fluids,
		BrassBoxFluidContent::new
	);

	public boolean isEmpty() {
		return fluids.isEmpty();
	}

	public record FluidEntry(FluidStack fluid, ContentRoute route) {
		public static final Codec<FluidEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			FluidStack.CODEC.fieldOf("fluid").forGetter(FluidEntry::fluid),
			ContentRoute.CODEC.fieldOf("route").forGetter(FluidEntry::route)
		).apply(instance, FluidEntry::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, FluidEntry> STREAM_CODEC = StreamCodec.composite(
			FluidStack.STREAM_CODEC, FluidEntry::fluid,
			ContentRoute.STREAM_CODEC, FluidEntry::route,
			FluidEntry::new
		);

		public FluidEntry copy() {
			return new FluidEntry(fluid.copy(), route);
		}

		public boolean isEmpty() {
			return fluid.isEmpty() || fluid.getAmount() <= 0;
		}
	}
}
