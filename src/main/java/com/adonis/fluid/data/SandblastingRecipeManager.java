package com.adonis.fluid.data;

import com.adonis.fluid.CreateFluid;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = CreateFluid.MOD_ID)
public class SandblastingRecipeManager {

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        RecipeManager recipeManager = server.getRecipeManager();

        try {
            List<RecipeHolder<?>> allRecipes = new ArrayList<>(recipeManager.getRecipes());
            int count = SandblastingRuntimeRecipeProvider.buildRecipes(allRecipes);
            if (count > 0) {
                recipeManager.replaceRecipes(allRecipes);
                CreateFluid.LOGGER.info("Added {} runtime sandblasting recipes.", count);
            }
        } catch (Exception e) {
            CreateFluid.LOGGER.error("Failed to add runtime sandblasting recipes", e);
        }
    }
}
