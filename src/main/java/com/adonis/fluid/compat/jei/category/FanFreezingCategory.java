package com.adonis.fluid.compat.jei.category;

import com.adonis.fluid.compat.jei.CFJeiPlugin;
import com.adonis.fluid.content.kinetics.fan.freezing.FreezingRecipe;
import com.adonis.fluid.registry.CFRecipeTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.compat.jei.DoubleItemIcon;
import com.simibubi.create.compat.jei.EmptyBackground;
import com.simibubi.create.compat.jei.category.ProcessingViaFanCategory;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

public class FanFreezingCategory extends ProcessingViaFanCategory.MultiOutput<FreezingRecipe> {

	public static final mezz.jei.api.recipe.RecipeType<RecipeHolder<FreezingRecipe>> TYPE =
		mezz.jei.api.recipe.RecipeType.createRecipeHolderType(CFRecipeTypes.FREEZING.getId());

	private FanFreezingCategory(Info<FreezingRecipe> info) {
		super(info);
	}

	public static FanFreezingCategory create() {
		var id = CFRecipeTypes.FREEZING.getId();
		var title = net.minecraft.network.chat.Component.translatable("recipe.fluid.fan_freezing");
		var background = new EmptyBackground(178, 72);
		var icon = new DoubleItemIcon(AllItems.PROPELLER::asStack, Items.POWDER_SNOW_BUCKET::getDefaultInstance);
		var catalyst = AllBlocks.ENCASED_FAN.asStack();
		catalyst.set(DataComponents.CUSTOM_NAME,
			net.minecraft.network.chat.Component.translatable("recipe.fluid.fan_freezing.fan")
				.withStyle(style -> style.withItalic(false)));
		var info = new Info<>(TYPE, title, background, icon, FanFreezingCategory::getAllRecipes, List.of(() -> catalyst));
		return new FanFreezingCategory(info);
	}

	@Override
	protected void renderAttachedBlock(GuiGraphics graphics) {
		GuiGameElement.of(Blocks.POWDER_SNOW.defaultBlockState())
			.scale(SCALE)
			.atLocal(0, 0, 2)
			.lighting(AnimatedKinetics.DEFAULT_LIGHTING)
			.render(graphics);
	}

	private static List<RecipeHolder<FreezingRecipe>> getAllRecipes() {
		return new ArrayList<>(CFJeiPlugin.getRecipeManager().getAllRecipesFor(CFRecipeTypes.FREEZING.getType()));
	}
}
