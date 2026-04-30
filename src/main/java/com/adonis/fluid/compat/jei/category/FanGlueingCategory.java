package com.adonis.fluid.compat.jei.category;

import com.adonis.fluid.compat.jei.CFJeiPlugin;
import com.adonis.fluid.content.kinetics.fan.glueing.GlueingRecipe;
import com.adonis.fluid.registry.CFRecipeTypes;
import com.simibubi.create.AllItems;
import com.simibubi.create.compat.jei.DoubleItemIcon;
import com.simibubi.create.compat.jei.EmptyBackground;
import com.simibubi.create.compat.jei.category.ProcessingViaFanCategory;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import com.adonis.fluid.registry.CFFluids;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

public class FanGlueingCategory extends ProcessingViaFanCategory.MultiOutput<GlueingRecipe> {

	public static final mezz.jei.api.recipe.RecipeType<RecipeHolder<GlueingRecipe>> TYPE =
		mezz.jei.api.recipe.RecipeType.createRecipeHolderType(CFRecipeTypes.GLUEING.getId());

	private FanGlueingCategory(Info<GlueingRecipe> info) {
		super(info);
	}

	public static FanGlueingCategory create() {
		var id = CFRecipeTypes.GLUEING.getId();
		var title = net.minecraft.network.chat.Component.translatable("recipe.fluid.fan_glueing");
		var background = new EmptyBackground(178, 72);
		var icon = new DoubleItemIcon(AllItems.PROPELLER::asStack,
			() -> new ItemStack(CFFluids.SLIME_FLUID.getBucket().orElseThrow()));
		var catalyst = com.simibubi.create.AllBlocks.ENCASED_FAN.asStack();
		catalyst.set(DataComponents.CUSTOM_NAME,
			net.minecraft.network.chat.Component.translatable("recipe.fluid.fan_glueing.fan")
				.withStyle(style -> style.withItalic(false)));
		var info = new Info<>(TYPE, title, background, icon, FanGlueingCategory::getAllRecipes, List.of(() -> catalyst));
		return new FanGlueingCategory(info);
	}

	@Override
	protected void renderAttachedBlock(GuiGraphics graphics) {
		GuiGameElement.of(CFFluids.SLIME_FLUID.getSource().defaultFluidState().createLegacyBlock())
			.scale(SCALE)
			.atLocal(0, 0, 2)
			.lighting(AnimatedKinetics.DEFAULT_LIGHTING)
			.render(graphics);
	}

	private static List<RecipeHolder<GlueingRecipe>> getAllRecipes() {
		return new ArrayList<>(CFJeiPlugin.getRecipeManager().getAllRecipesFor(CFRecipeTypes.GLUEING.getType()));
	}
}
