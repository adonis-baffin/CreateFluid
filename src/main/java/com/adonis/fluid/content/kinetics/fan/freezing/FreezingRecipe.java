package com.adonis.fluid.content.kinetics.fan.freezing;

import com.adonis.fluid.registry.CFRecipeTypes;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

public class FreezingRecipe extends StandardProcessingRecipe<SingleRecipeInput> {

	public FreezingRecipe(ProcessingRecipeParams params) {
		super(CFRecipeTypes.FREEZING, params);
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

	public static StandardProcessingRecipe.Builder<FreezingRecipe> builder(ResourceLocation id) {
		return new StandardProcessingRecipe.Builder<>(FreezingRecipe::new, id);
	}
}
