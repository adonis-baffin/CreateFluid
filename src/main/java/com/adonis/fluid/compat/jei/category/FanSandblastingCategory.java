package com.adonis.fluid.compat.jei.category;

import com.adonis.fluid.compat.jei.CFJeiPlugin;
import com.adonis.fluid.content.kinetics.fan.sandblasting.SandblastingRecipe;
import com.adonis.fluid.registry.CFRecipeTypes;
import com.adonis.fluid.registry.CFBlocks;
import com.adonis.fluid.registry.CFItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.compat.jei.DoubleItemIcon;
import com.simibubi.create.content.equipment.sandPaper.SandPaperPolishingRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import com.simibubi.create.compat.jei.EmptyBackground;
import com.simibubi.create.compat.jei.category.ProcessingViaFanCategory;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

public class FanSandblastingCategory extends ProcessingViaFanCategory.MultiOutput<SandblastingRecipe> {

	public static final mezz.jei.api.recipe.RecipeType<RecipeHolder<SandblastingRecipe>> TYPE =
		mezz.jei.api.recipe.RecipeType.createRecipeHolderType(CFRecipeTypes.SANDBLASTING.getId());

	private FanSandblastingCategory(Info<SandblastingRecipe> info) {
		super(info);
	}

	public static FanSandblastingCategory create() {
		var id = CFRecipeTypes.SANDBLASTING.getId();
		var title = net.minecraft.network.chat.Component.translatable("recipe.fluid.fan_sandblasting");
		var background = new EmptyBackground(178, 72);
		var icon = new DoubleItemIcon(AllItems.PROPELLER::asStack, CFItems.QUICKSAND_BUCKET::asStack);
		var catalyst = AllBlocks.ENCASED_FAN.asStack();
		catalyst.set(DataComponents.CUSTOM_NAME,
			net.minecraft.network.chat.Component.translatable("recipe.fluid.fan_sandblasting.fan")
				.withStyle(style -> style.withItalic(false)));
		var info = new Info<>(TYPE, title, background, icon, FanSandblastingCategory::getAllRecipes, List.of(() -> catalyst));
		return new FanSandblastingCategory(info);
	}

	@Override
	protected void renderAttachedBlock(GuiGraphics graphics) {
		GuiGameElement.of(CFBlocks.QUICKSAND.getDefaultState())
			.scale(SCALE)
			.atLocal(0, 0, 2)
			.lighting(AnimatedKinetics.DEFAULT_LIGHTING)
			.render(graphics);
	}

	private static List<RecipeHolder<SandblastingRecipe>> getAllRecipes() {
		var manager = CFJeiPlugin.getRecipeManager();
		var recipes = new ArrayList<>(manager.getAllRecipesFor(CFRecipeTypes.SANDBLASTING.getType()));
		manager.getAllRecipesFor(AllRecipeTypes.SANDPAPER_POLISHING.<SingleRecipeInput, SandPaperPolishingRecipe>getType())
			.stream()
			.map(SandblastingRecipe::convertSandPaperPolishing)
			.forEach(recipes::add);
		return recipes;
	}
}
