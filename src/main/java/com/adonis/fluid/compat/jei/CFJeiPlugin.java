package com.adonis.fluid.compat.jei;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.compat.jei.category.FanFreezingCategory;
import com.adonis.fluid.compat.jei.category.FanGlueingCategory;
import com.adonis.fluid.compat.jei.category.FanSandblastingCategory;
import com.adonis.fluid.registry.CFBlocks;
import com.google.common.base.Preconditions;
import com.simibubi.create.Create;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public class CFJeiPlugin implements IModPlugin {

	public static final ResourceLocation ID = CreateFluid.asResource("jei_plugin");
	private final List<CreateRecipeCategory<?>> categories = new ArrayList<>();

	@Override
	public ResourceLocation getPluginUid() {
		return ID;
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		categories.clear();
		if (!ModList.get().isLoaded("create_dragons_plus")) {
			categories.add(FanFreezingCategory.create());
			categories.add(FanSandblastingCategory.create());
		}
		categories.add(FanGlueingCategory.create());
		registration.addRecipeCategories(categories.toArray(IRecipeCategory[]::new));
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		categories.forEach(category -> category.registerRecipes(registration));
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		categories.forEach(category -> category.registerCatalysts(registration));
		registration.addRecipeCatalysts(fanType(Create.asResource("spout_filling")), CFBlocks.COPPER_TAP.get());
		registration.addRecipeCatalysts(fanType(Create.asResource("spout_filling")), CFBlocks.PIPETTE.get());
		registration.addRecipeCatalysts(fanType(Create.asResource("fan_washing")), CFBlocks.FLUID_ATOMIZER.get());
		registration.addRecipeCatalysts(fanType(Create.asResource("fan_smoking")), CFBlocks.FLUID_ATOMIZER.get());
		registration.addRecipeCatalysts(fanType(Create.asResource("fan_blasting")), CFBlocks.FLUID_ATOMIZER.get());
		registration.addRecipeCatalysts(fanType(Create.asResource("fan_haunting")), CFBlocks.FLUID_ATOMIZER.get());
		if (ModList.get().isLoaded("create_dragons_plus")) {
			registration.addRecipeCatalysts(fanType(ResourceLocation.fromNamespaceAndPath("create_dragons_plus", "coloring")),
				CFBlocks.FLUID_ATOMIZER.get());
			registration.addRecipeCatalysts(fanType(ResourceLocation.fromNamespaceAndPath("create_dragons_plus", "freezing")),
				CFBlocks.FLUID_ATOMIZER.get());
			registration.addRecipeCatalysts(fanType(ResourceLocation.fromNamespaceAndPath("create_dragons_plus", "ending")),
				CFBlocks.FLUID_ATOMIZER.get());
			registration.addRecipeCatalysts(fanType(ResourceLocation.fromNamespaceAndPath("create_dragons_plus", "sanding")),
				CFBlocks.FLUID_ATOMIZER.get());
		}
		if (ModList.get().isLoaded("create_shimmer"))
			registration.addRecipeCatalysts(fanType(ResourceLocation.fromNamespaceAndPath("create_shimmer", "transmutation")),
				CFBlocks.FLUID_ATOMIZER.get());
		if (!ModList.get().isLoaded("create_dragons_plus")) {
			registration.addRecipeCatalysts(FanFreezingCategory.TYPE, CFBlocks.FLUID_ATOMIZER.get());
			registration.addRecipeCatalysts(FanSandblastingCategory.TYPE, CFBlocks.FLUID_ATOMIZER.get());
		}
		registration.addRecipeCatalysts(FanGlueingCategory.TYPE, CFBlocks.FLUID_ATOMIZER.get());
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static mezz.jei.api.recipe.RecipeType<RecipeHolder<?>> fanType(ResourceLocation id) {
		return (mezz.jei.api.recipe.RecipeType) mezz.jei.api.recipe.RecipeType.createRecipeHolderType(id);
	}

	public static Level getLevel() {
		if (FMLLoader.getDist() != Dist.CLIENT)
			throw new IllegalStateException("Retrieving client level is only supported on the client");
		Minecraft minecraft = Minecraft.getInstance();
		Preconditions.checkNotNull(minecraft, "minecraft");
		Level level = minecraft.level;
		Preconditions.checkNotNull(level, "level");
		return level;
	}

	public static RecipeManager getRecipeManager() {
		return getLevel().getRecipeManager();
	}
}
