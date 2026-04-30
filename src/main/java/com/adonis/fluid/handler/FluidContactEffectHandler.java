package com.adonis.fluid.handler;

import com.adonis.fluid.registry.CFFluids;
import com.adonis.fluid.CreateFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = CreateFluid.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class FluidContactEffectHandler {

	@SubscribeEvent
	public static void onEntityTick(EntityTickEvent.Pre event) {
		Entity entity = event.getEntity();
		Level level = entity.level();
		if (level.isClientSide || !entity.isAlive())
			return;

		ContactFluid contact = fluid$getContactFluid(level, entity.getBoundingBox());
		if (contact == ContactFluid.NONE)
			return;

		switch (contact) {
			case SLIME -> fluid$applySlimeEffects(entity);
			case HAUNTING -> {
				fluid$applyViscosity(entity, 0.42, 0.66);
				fluid$applyHauntingEffects(entity);
				fluid$applyHauntingHorseProgress(entity, level);
			}
			case SMOKING -> fluid$applyViscosity(entity, 0.48, 0.7);
			default -> {
			}
		}
	}

	private static void fluid$applySlimeEffects(Entity entity) {
		fluid$applyViscosity(entity, 0.16, 0.3);
		entity.makeStuckInBlock(Blocks.SLIME_BLOCK.defaultBlockState(), new Vec3(0.055, 0.012, 0.055));
		Vec3 motion = entity.getDeltaMovement();
		if (motion.y < 0)
			entity.setDeltaMovement(motion.x * 0.22, motion.y * 0.08, motion.z * 0.22);
		entity.fallDistance = 0;
	}

	private static void fluid$applyHauntingEffects(Entity entity) {
		if (!(entity instanceof LivingEntity living))
			return;
		living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false));
		living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, false));
	}

	private static void fluid$applyViscosity(Entity entity, double horizontalScale, double verticalScale) {
		entity.makeStuckInBlock(Blocks.HONEY_BLOCK.defaultBlockState(), new Vec3(horizontalScale, verticalScale, horizontalScale));
		Vec3 motion = entity.getDeltaMovement();
		entity.setDeltaMovement(motion.x * horizontalScale, motion.y * verticalScale, motion.z * horizontalScale);
		entity.fallDistance = 0;
	}

	private static void fluid$applyHauntingHorseProgress(Entity entity, Level level) {
		if (!(entity instanceof Horse horse))
			return;
		int progress = horse.getPersistentData()
			.getInt("CreateHaunting");
		if (progress < 100) {
			if (progress % 10 == 0) {
				level.playSound(null, entity.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.NEUTRAL,
					1f, 1.5f * progress / 100f);
			}
			horse.getPersistentData()
				.putInt("CreateHaunting", progress + 1);
			return;
		}

		level.playSound(null, entity.blockPosition(), SoundEvents.GENERIC_EXTINGUISH_FIRE,
			SoundSource.NEUTRAL, 1.25f, 0.65f);

		SkeletonHorse skeletonHorse = EntityType.SKELETON_HORSE.create(level);
		CompoundTag serializeNBT = horse.saveWithoutId(new CompoundTag());
		serializeNBT.remove("UUID");
		if (!horse.getBodyArmorItem()
			.isEmpty())
			horse.spawnAtLocation(horse.getBodyArmorItem());

		skeletonHorse.load(serializeNBT);
		skeletonHorse.setPos(horse.getPosition(0));
		level.addFreshEntity(skeletonHorse);
		horse.discard();
	}

	private static ContactFluid fluid$getContactFluid(Level level, AABB bounds) {
		int minX = (int) Math.floor(bounds.minX);
		int maxX = (int) Math.floor(bounds.maxX);
		int minY = (int) Math.floor(bounds.minY);
		int maxY = (int) Math.floor(bounds.maxY);
		int minZ = (int) Math.floor(bounds.minZ);
		int maxZ = (int) Math.floor(bounds.maxZ);

		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					BlockPos pos = new BlockPos(x, y, z);
					FluidState state = level.getFluidState(pos);
					if (state.isEmpty())
						continue;
					double surface = y + state.getHeight(level, pos);
					if (surface <= bounds.minY + 0.01D)
						continue;
					Fluid fluid = state.getType();
					if (fluid == CFFluids.SLIME_FLUID.get() || fluid == CFFluids.SLIME_FLUID.getSource())
						return ContactFluid.SLIME;
					if (fluid == CFFluids.HAUNTING_FLUID.get() || fluid == CFFluids.HAUNTING_FLUID.getSource())
						return ContactFluid.HAUNTING;
					if (fluid == CFFluids.SMOKING_FLUID.get() || fluid == CFFluids.SMOKING_FLUID.getSource())
						return ContactFluid.SMOKING;
				}
			}
		}

		return ContactFluid.NONE;
	}

	private enum ContactFluid {
		NONE,
		HAUNTING,
		SMOKING,
		SLIME
	}
}
