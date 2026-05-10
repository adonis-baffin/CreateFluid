package com.adonis.fluid.content.kinetics.fan.freezing;

import com.adonis.fluid.registry.CFRecipeTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.foundation.recipe.RecipeApplier;
import net.createmod.catnip.theme.Color;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FreezingFanProcessingType implements FanProcessingType {

	private static final int PASSIVE_COLOR = 0xFFFFFF;
	private static final int FROZEN_COLOR = 0x8ADCE8;

	@Override
	public boolean isValidAt(Level level, BlockPos pos) {
		if (ModList.get().isLoaded("create_dragons_plus"))
			return false;
		return level.getBlockState(pos).is(Blocks.POWDER_SNOW);
	}

	@Override
	public int getPriority() {
		return 600;
	}

	@Override
	public boolean canProcess(ItemStack stack, Level level) {
		return level.getRecipeManager()
			.getRecipeFor(CFRecipeTypes.FREEZING.getType(), new SingleRecipeInput(stack), level)
			.isPresent();
	}

	@Override
	public @Nullable List<ItemStack> process(ItemStack stack, Level level) {
		return level.getRecipeManager()
			.getRecipeFor(CFRecipeTypes.FREEZING.getType(), new SingleRecipeInput(stack), level)
			.map(recipe -> RecipeApplier.applyRecipeOn(level, stack, recipe.value(), true))
			.orElse(null);
	}

	@Override
	public void spawnProcessingParticles(Level level, Vec3 pos) {
		if (level.random.nextInt(8) == 0) {
			level.addParticle(
				ParticleTypes.SNOWFLAKE,
				pos.x + (level.random.nextFloat() - .5f) * .5f,
				pos.y + .5f,
				pos.z + (level.random.nextFloat() - .5f) * .5f,
				0, 1 / 8f, 0
			);
		}
	}

	@Override
	public void morphAirFlow(AirFlowParticleAccess particleAccess, RandomSource random) {
		int color = Color.mixColors(PASSIVE_COLOR, FROZEN_COLOR, random.nextFloat());
		particleAccess.setColor(color);
		particleAccess.setAlpha(1f);
		if (random.nextInt(32) == 0)
			particleAccess.spawnExtraParticle(ParticleTypes.SNOWFLAKE, 1 / 8f);
	}

	@Override
	public void affectEntity(Entity entity, Level level) {
		if (level.isClientSide)
			return;
		if (entity.canFreeze())
			entity.setTicksFrozen(Math.min(entity.getTicksRequiredToFreeze(), entity.getTicksFrozen()) + 3);
		entity.extinguishFire();
	}
}
