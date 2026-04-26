package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.content.kinetics.fan.freezing.FreezingRecipe;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class CFRecipeTypes {

	private static final DeferredRegister<RecipeType<?>> TYPES =
		DeferredRegister.create(BuiltInRegistries.RECIPE_TYPE, CreateFluid.MOD_ID);
	private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
		DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, CreateFluid.MOD_ID);

	public static final RecipeTypeInfo<FreezingRecipe> FREEZING =
		register("freezing", () -> new StandardProcessingRecipe.Serializer<>(FreezingRecipe::new));

	public static void register(IEventBus modBus) {
		TYPES.register(modBus);
		SERIALIZERS.register(modBus);
	}

	private static <R extends Recipe<?>> RecipeTypeInfo<R> register(String name, Supplier<? extends RecipeSerializer<R>> serializer) {
		return new RecipeTypeInfo<>(name, serializer, SERIALIZERS, TYPES);
	}

	@SuppressWarnings("unchecked")
	public static class RecipeTypeInfo<R extends Recipe<?>> implements IRecipeTypeInfo {
		private final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<R>> serializer;
		private final DeferredHolder<RecipeType<?>, RecipeType<R>> type;

		public RecipeTypeInfo(String name, Supplier<? extends RecipeSerializer<R>> serializer,
							  DeferredRegister<RecipeSerializer<?>> serializerRegister,
							  DeferredRegister<RecipeType<?>> typeRegister) {
			this.serializer = serializerRegister.register(name, serializer);
			this.type = typeRegister.register(name, RecipeType::simple);
		}

		@Override
		public ResourceLocation getId() {
			return serializer.getId();
		}

		@Override
		public RecipeSerializer<R> getSerializer() {
			return serializer.get();
		}

		@Override
		public RecipeType<R> getType() {
			return type.get();
		}
	}
}
