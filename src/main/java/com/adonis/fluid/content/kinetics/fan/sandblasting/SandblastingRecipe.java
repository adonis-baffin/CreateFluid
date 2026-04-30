package com.adonis.fluid.content.kinetics.fan.sandblasting;

import com.adonis.fluid.registry.CFRecipeTypes;
import com.simibubi.create.content.equipment.sandPaper.SandPaperPolishingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

public class SandblastingRecipe extends StandardProcessingRecipe<SingleRecipeInput> {

	public SandblastingRecipe(ProcessingRecipeParams params) {
		super(CFRecipeTypes.SANDBLASTING, params);
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

	public static StandardProcessingRecipe.Builder<SandblastingRecipe> builder(ResourceLocation id) {
		return new StandardProcessingRecipe.Builder<>(SandblastingRecipe::new, id);
	}

	public static RecipeHolder<SandblastingRecipe> convertSandPaperPolishing(RecipeHolder<SandPaperPolishingRecipe> original) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
			original.id().getNamespace(),
			original.id().getPath() + "_as_sandblasting");
		SandblastingRecipe recipe = builder(id)
			.require(original.value().getIngredients().getFirst())
			.output(original.value().getRollableResults().getFirst())
			.build();
		return new RecipeHolder<>(id, recipe);
	}
}
