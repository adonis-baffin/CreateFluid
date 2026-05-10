package com.adonis.fluid.content.kinetics.fan.glueing;

import com.adonis.fluid.registry.CFRecipeTypes;
import com.adonis.fluid.registry.CFFluids;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.foundation.recipe.RecipeApplier;
import net.createmod.catnip.theme.Color;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GlueingFanProcessingType implements FanProcessingType {

	private static final int PASSIVE_COLOR = 0xFFFFFF;
	private static final int SLIME_COLOR = 0x6EBF62;

	@Override
	public boolean isValidAt(Level level, BlockPos pos) {
		return level.getFluidState(pos).getType() == CFFluids.SLIME_FLUID.getSource();
	}

	@Override
	public int getPriority() {
		return 600;
	}

	@Override
	public boolean canProcess(ItemStack stack, Level level) {
		return level.getRecipeManager()
			.getRecipeFor(CFRecipeTypes.GLUEING.getType(), new SingleRecipeInput(stack), level)
			.isPresent();
	}

	@Override
	public @Nullable List<ItemStack> process(ItemStack stack, Level level) {
		return level.getRecipeManager()
			.getRecipeFor(CFRecipeTypes.GLUEING.getType(), new SingleRecipeInput(stack), level)
			.map(recipe -> RecipeApplier.applyRecipeOn(level, stack, recipe.value(), true))
			.orElse(null);
	}

	@Override
	public void spawnProcessingParticles(Level level, Vec3 pos) {
		if (level.random.nextInt(4) != 0)
			return;
		for (int i = 0; i < 3; i++) {
			double x = pos.x + (level.random.nextFloat() - .5f) * .5f;
			double y = pos.y + .5f + (level.random.nextFloat() - .5f) * .3f;
			double z = pos.z + (level.random.nextFloat() - .5f) * .5f;
			double vx = (level.random.nextFloat() - .5f) * .1f;
			double vz = (level.random.nextFloat() - .5f) * .1f;
			level.addParticle(
				new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SLIME_BLOCK.defaultBlockState()),
				x, y, z,
				vx, 1 / 6f, vz
			);
		}
	}

	@Override
	public void morphAirFlow(AirFlowParticleAccess particleAccess, RandomSource random) {
		int color = Color.mixColors(PASSIVE_COLOR, SLIME_COLOR, random.nextFloat());
		particleAccess.setColor(color);
		particleAccess.setAlpha(1f);
		if (random.nextInt(16) == 0)
			particleAccess.spawnExtraParticle(
				new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SLIME_BLOCK.defaultBlockState()),
				1 / 3f
			);
	}

	@Override
	public void affectEntity(Entity entity, Level level) {
		if (level.isClientSide)
			return;
		// 强烈减缓水平移动速度
		var motion = entity.getDeltaMovement();
		entity.setDeltaMovement(motion.x * 0.15, motion.y, motion.z * 0.15);
		// 给生物附加缓慢效果
		if (entity instanceof LivingEntity living) {
			living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2, false, true));
		}
	}
}
