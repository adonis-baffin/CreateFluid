package com.adonis.fluid.mixin;

import com.adonis.fluid.item.CopperCanItem;
import com.adonis.fluid.util.ICopperCanFeedback;
import com.simibubi.create.AllDamageTypes;
import com.simibubi.create.content.logistics.box.PackageEntity;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PackageEntity.class)
public abstract class PackageEntityMixin implements ICopperCanFeedback {

	private static final double fluid$PACKAGE_HEALTH = 5.0D;
	private static final double fluid$COPPER_CAN_HEALTH = 10.0D;

	@Shadow public ItemStack box;
	@Unique
	private int fluid$hurtShakeTime;

	@Inject(method = "setBox", at = @At("TAIL"))
	private void fluid$adjustCopperCanDurability(ItemStack box, CallbackInfo ci) {
		PackageEntity self = (PackageEntity) (Object) this;
		AttributeInstance attribute = self.getAttribute(Attributes.MAX_HEALTH);
		if (attribute == null)
			return;

		double targetHealth = CopperCanItem.isCopperCan(box) ? fluid$COPPER_CAN_HEALTH : fluid$PACKAGE_HEALTH;
		if (attribute.getBaseValue() != targetHealth)
			attribute.setBaseValue(targetHealth);

		if (self.getHealth() > targetHealth || self.getHealth() <= 0)
			self.setHealth((float) targetHealth);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void fluid$tickCopperCanFeedback(CallbackInfo ci) {
		if (fluid$hurtShakeTime > 0)
			fluid$hurtShakeTime--;
	}

	@Inject(method = "onInsideBlock", at = @At("HEAD"), cancellable = true)
	private void fluid$ignoreWaterForCopperCans(BlockState state, CallbackInfo ci) {
		if (!CopperCanItem.isCopperCan(box))
			return;
		if (state.getBlock() == Blocks.WATER || state.getFluidState().isSourceOfType(Blocks.WATER.defaultBlockState().getFluidState().getType())
			|| state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
			ci.cancel();
		}
	}

	@Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
	private void fluid$makeCopperCanHardier(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (!CopperCanItem.isCopperCan(box))
			return;

		PackageEntity self = (PackageEntity) (Object) this;
		if (self.level().isClientSide || !self.isAlive()) {
			cir.setReturnValue(false);
			return;
		}

		if (!box.getItem().canBeHurtBy(box, source)) {
			cir.setReturnValue(false);
			return;
		}

		if (self.isInvulnerableTo(source) || source.is(DamageTypeTags.IS_FALL)) {
			cir.setReturnValue(false);
			return;
		}

		if (source.getEntity() instanceof Player player && !player.getAbilities().mayBuild) {
			cir.setReturnValue(false);
			return;
		}

		if (source.getEntity() instanceof Player player && player.getAbilities().instabuild) {
			self.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
			cir.setReturnValue(true);
			return;
		}

		if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)
			|| source.is(AllDamageTypes.CRUSH) || source.is(AllDamageTypes.DRILL)
			|| source.is(AllDamageTypes.ROLLER) || source.is(AllDamageTypes.SAW)
			|| source.is(AllDamageTypes.RUN_OVER) || source.is(AllDamageTypes.FAN_FIRE)
			|| source.is(AllDamageTypes.FAN_LAVA)) {
			cir.setReturnValue(false);
			return;
		}

		float remaining = self.getHealth() - Math.max(1.0F, amount);
		self.animateHurt(amount);
		self.hurtMarked = true;
		if (remaining <= 0.5F) {
			if (self.level() instanceof ServerLevel serverLevel) {
				Vec3 center = self.getBoundingBox().getCenter();
				ItemParticleOption particle = new ItemParticleOption(ParticleTypes.ITEM, box.copy());
				for (int i = 0; i < 20; i++) {
					Vec3 motion = VecHelper.offsetRandomly(Vec3.ZERO, serverLevel.random, .125f);
					Vec3 pos = center.add(motion.scale(4));
					serverLevel.sendParticles(particle, pos.x, pos.y, pos.z, 1, motion.x, motion.y, motion.z, 0);
				}
			}
			self.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
		} else {
			self.setHealth(remaining);
			Vec3 motion = self.getDeltaMovement();
			self.setDeltaMovement(motion.x * -0.28, Math.max(0.08, motion.y + 0.08), motion.z * -0.28);
		}

		cir.setReturnValue(false);
	}

	@Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
	private void fluid$dropNoContentsWhenCopperCanBreaks(CallbackInfo ci) {
		if (CopperCanItem.isCopperCan(box))
			ci.cancel();
	}

	@Override
	public int fluid$getHurtShakeTime() {
		return fluid$hurtShakeTime;
	}

	@Override
	public void fluid$setHurtShakeTime(int ticks) {
		if (fluid$hurtShakeTime > 0)
			fluid$hurtShakeTime = Math.min(40, fluid$hurtShakeTime + Math.max(6, ticks / 2));
		else
			fluid$hurtShakeTime = ticks;
	}
}
