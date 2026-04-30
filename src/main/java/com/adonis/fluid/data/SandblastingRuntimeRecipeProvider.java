package com.adonis.fluid.data;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.content.kinetics.fan.sandblasting.SandblastingRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class SandblastingRuntimeRecipeProvider {

    public static int buildRecipes(List<RecipeHolder<?>> recipes) {
        return buildPolishedBlockRecipes(recipes);
    }

    private static int buildPolishedBlockRecipes(List<RecipeHolder<?>> recipes) {
        AtomicInteger count = new AtomicInteger();
        BuiltInRegistries.BLOCK.holders()
            .filter(holder -> holder.key().location().getPath().contains("polished_"))
            .forEach(holder -> {
                var polishedId = holder.key().location();
                var baseId = polishedId.withPath(name -> name.replace("polished_", ""));
                if (!BuiltInRegistries.BLOCK.containsKey(baseId))
                    return;
                var polishedItem = holder.value().asItem();
                var baseBlock = BuiltInRegistries.BLOCK.getHolder(baseId);
                if (baseBlock.isEmpty())
                    return;
                var baseItem = baseBlock.get().value().asItem();
                if (polishedItem == Items.AIR || baseItem == Items.AIR)
                    return;
                var recipeId = CreateFluid.asResource(baseId.toString().replace(':', '/'));

                boolean exists = recipes.stream().anyMatch(h -> h.id().equals(recipeId));
                if (exists)
                    return;

                SandblastingRecipe recipe = SandblastingRecipe.builder(recipeId)
                    .require(Ingredient.of(baseItem))
                    .output(new ProcessingOutput(new net.minecraft.world.item.ItemStack(polishedItem), 1))
                    .build();
                recipes.add(new RecipeHolder<>(recipeId, recipe));
                count.incrementAndGet();
            });
        return count.get();
    }
}
