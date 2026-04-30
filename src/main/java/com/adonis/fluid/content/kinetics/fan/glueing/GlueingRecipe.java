package com.adonis.fluid.content.kinetics.fan.glueing;

import com.adonis.fluid.registry.CFRecipeTypes;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

public class GlueingRecipe extends StandardProcessingRecipe<SingleRecipeInput> {

	public GlueingRecipe(ProcessingRecipeParams params) {
		super(CFRecipeTypes.GLUEING, params);
	}

	@Override
	protected int getMaxInputCount() {
		return 1;
	}

	@Override
	protected int getMaxOutputCount() {
		return 12;
	}

	@Override
	public boolean matches(SingleRecipeInput input, Level level) {
		return getIngredients().getFirst().test(input.item());
	}

	public static StandardProcessingRecipe.Builder<GlueingRecipe> builder(ResourceLocation id) {
		return new StandardProcessingRecipe.Builder<>(GlueingRecipe::new, id);
	}
}
