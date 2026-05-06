package com.adonis.fluid.datacomponent;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.adonis.fluid.logistics.data.ContentRoute;
import com.adonis.fluid.logistics.data.FluidRequestKey;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public record BrassBoxRoutingData(List<ItemRoute> itemRoutes, List<FluidRoute> fluidRoutes) {
	public static final BrassBoxRoutingData EMPTY = new BrassBoxRoutingData(List.of(), List.of());

	public static final Codec<BrassBoxRoutingData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		ItemRoute.CODEC.listOf().fieldOf("item_routes").forGetter(BrassBoxRoutingData::itemRoutes),
		FluidRoute.CODEC.listOf().fieldOf("fluid_routes").forGetter(BrassBoxRoutingData::fluidRoutes)
	).apply(instance, BrassBoxRoutingData::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, BrassBoxRoutingData> STREAM_CODEC = StreamCodec.composite(
		CatnipStreamCodecBuilders.list(ItemRoute.STREAM_CODEC), BrassBoxRoutingData::itemRoutes,
		CatnipStreamCodecBuilders.list(FluidRoute.STREAM_CODEC), BrassBoxRoutingData::fluidRoutes,
		BrassBoxRoutingData::new
	);

	public boolean isEmpty() {
		return itemRoutes.isEmpty() && fluidRoutes.isEmpty();
	}

	public ContentRoute routeForItem(ItemStack stack) {
		for (ItemRoute route : itemRoutes) {
			if (ItemStack.isSameItemSameComponents(route.prototype(), stack))
				return route.route();
		}
		return ContentRoute.NONE;
	}

	public ContentRoute routeForFluid(FluidStack stack) {
		if (stack.isEmpty())
			return ContentRoute.NONE;
		ResourceLocation id = BuiltInRegistries.FLUID.getKey(stack.getFluid());
		for (FluidRoute route : fluidRoutes) {
			if (route.fluidId().equals(id))
				return route.route();
		}
		return ContentRoute.NONE;
	}

	@Nullable
	public FluidRoute findFluidRoute(FluidRequestKey key) {
		for (FluidRoute route : fluidRoutes) {
			if (route.fluidId().equals(key.fluidId()))
				return route;
		}
		return null;
	}

	public record ItemRoute(ItemStack prototype, ContentRoute route) {
		public static final Codec<ItemRoute> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ItemStack.SINGLE_ITEM_CODEC.fieldOf("prototype").forGetter(ItemRoute::prototype),
			ContentRoute.CODEC.fieldOf("route").forGetter(ItemRoute::route)
		).apply(instance, ItemRoute::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, ItemRoute> STREAM_CODEC = StreamCodec.composite(
			ItemStack.STREAM_CODEC, ItemRoute::prototype,
			ContentRoute.STREAM_CODEC, ItemRoute::route,
			ItemRoute::new
		);
	}

	public record FluidRoute(ResourceLocation fluidId, ContentRoute route) {
		public static final Codec<FluidRoute> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ResourceLocation.CODEC.fieldOf("fluid_id").forGetter(FluidRoute::fluidId),
			ContentRoute.CODEC.fieldOf("route").forGetter(FluidRoute::route)
		).apply(instance, FluidRoute::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, FluidRoute> STREAM_CODEC = StreamCodec.composite(
			ResourceLocation.STREAM_CODEC, FluidRoute::fluidId,
			ContentRoute.STREAM_CODEC, FluidRoute::route,
			FluidRoute::new
		);
	}
}
