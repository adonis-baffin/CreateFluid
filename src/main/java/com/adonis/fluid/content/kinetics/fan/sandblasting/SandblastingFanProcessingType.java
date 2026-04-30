package com.adonis.fluid.content.kinetics.fan.sandblasting;

import com.adonis.fluid.registry.CFBlocks;
import com.adonis.fluid.registry.CFRecipeTypes;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.equipment.sandPaper.SandPaperPolishingRecipe;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.foundation.recipe.RecipeApplier;
import net.createmod.catnip.theme.Color;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SandblastingFanProcessingType implements FanProcessingType {

	private static final int PASSIVE_COLOR = 0xFFFFFF;
	private static final int SAND_COLOR = 0xD6CFA6;

	@Override
	public boolean isValidAt(Level level, BlockPos pos) {
		if (ModList.get().isLoaded("create_dragons_plus"))
			return false;
		return level.getBlockState(pos).is(CFBlocks.QUICKSAND.get());
	}

	@Override
	public int getPriority() {
		return 600;
	}

	@Override
	public boolean canProcess(ItemStack stack, Level level) {
		var input = new SingleRecipeInput(stack);
		var recipeManager = level.getRecipeManager();
		return recipeManager.getRecipeFor(CFRecipeTypes.SANDBLASTING.getType(), input, level).isPresent()
			|| recipeManager.getRecipeFor(AllRecipeTypes.SANDPAPER_POLISHING.getType(), input, level).isPresent();
	}

	@Override
	public @Nullable List<ItemStack> process(ItemStack stack, Level level) {
		var input = new SingleRecipeInput(stack);
		var recipeManager = level.getRecipeManager();
		return recipeManager.getRecipeFor(CFRecipeTypes.SANDBLASTING.getType(), input, level)
			.map(recipe -> RecipeApplier.applyRecipeOn(level, stack, recipe))
			.or(() -> recipeManager.getRecipeFor(AllRecipeTypes.SANDPAPER_POLISHING.getType(), input, level)
				.map(recipe -> RecipeApplier.applyRecipeOn(level, stack, recipe)))
			.orElse(null);
	}

	@Override
	public void spawnProcessingParticles(Level level, Vec3 pos) {
		if (level.random.nextInt(8) != 0)
			return;
		double x = pos.x + (level.random.nextFloat() - .5f) * .5f;
		double y = pos.y + .5f;
		double z = pos.z + (level.random.nextFloat() - .5f) * .5f;
		var state = CFBlocks.QUICKSAND.getDefaultState();
		// 主粒子：流沙尘土
		level.addParticle(
			new BlockParticleOption(ParticleTypes.FALLING_DUST, state),
			x, y, z,
			0, 1 / 3f, 0
		);
		// 辅助粒子：带沙色的尘土云
		level.addParticle(
			new DustParticleOptions(new org.joml.Vector3f(0.84f, 0.81f, 0.65f), 1.0f),
			x + (level.random.nextFloat() - .5f) * .3f,
			y + (level.random.nextFloat() - .5f) * .3f,
			z + (level.random.nextFloat() - .5f) * .3f,
			0, 1 / 3f, 0
		);
	}

	@Override
	public void morphAirFlow(AirFlowParticleAccess particleAccess, RandomSource random) {
		int color = Color.mixColors(PASSIVE_COLOR, SAND_COLOR, random.nextFloat());
		particleAccess.setColor(color);
		particleAccess.setAlpha(1f);
		if (random.nextInt(32) == 0)
			particleAccess.spawnExtraParticle(
				new BlockParticleOption(ParticleTypes.FALLING_DUST, CFBlocks.QUICKSAND.getDefaultState()),
				1 / 3f
			);
	}

	@Override
	public void affectEntity(Entity entity, Level level) {
		// 喷砂对实体没有特殊效果
	}
}
