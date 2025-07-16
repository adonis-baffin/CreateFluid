package com.adonis.createfisheryindustry.item;

import com.adonis.createfisheryindustry.entity.HarpoonEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

import java.util.function.Consumer;

public class HarpoonItem extends Item {
    public static final int THROW_THRESHOLD_TIME = 10;
    public static final float BASE_DAMAGE = 6.0F;
    public static final float SHOOT_POWER = 2.5F;

    public HarpoonItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state, Level level, net.minecraft.core.BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entityLiving, int timeLeft) {
        if (entityLiving instanceof Player player) {
            int i = this.getUseDuration(stack) - timeLeft;
            if (i >= THROW_THRESHOLD_TIME) {
                // 1.20.1中可能没有getTridentSpinAttackStrength方法，使用简单的检查
                boolean hasRiptide = EnchantmentHelper.getRiptide(stack) > 0;
                if ((!hasRiptide || player.isInWaterOrRain()) && !isTooDamagedToUse(stack)) {
                    SoundEvent soundEvent = SoundEvents.TRIDENT_THROW;
                    if (!level.isClientSide) {
                        stack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(entityLiving.getUsedItemHand()));
                        if (!hasRiptide) {
                            HarpoonEntity harpoon = new HarpoonEntity(level, player, stack);
                            harpoon.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, SHOOT_POWER, 1.0F);
                            if (player.getAbilities().instabuild) {
                                harpoon.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.CREATIVE_ONLY;
                            }
                            level.addFreshEntity(harpoon);
                            level.playSound(null, harpoon, soundEvent, SoundSource.PLAYERS, 1.0F, 1.0F);
                            if (!player.getAbilities().instabuild) {
                                player.getInventory().removeItem(stack);
                            }
                        }
                    }
                    player.awardStat(Stats.ITEM_USED.get(this));
                    if (hasRiptide) {
                        float f7 = player.getYRot();
                        float f1 = player.getXRot();
                        float f2 = -Mth.sin(f7 * 0.017453292F) * Mth.cos(f1 * 0.017453292F);
                        float f3 = -Mth.sin(f1 * 0.017453292F);
                        float f4 = Mth.cos(f7 * 0.017453292F) * Mth.cos(f1 * 0.017453292F);
                        float f5 = Mth.sqrt(f2 * f2 + f3 * f3 + f4 * f4);
                        float riptideLevel = EnchantmentHelper.getRiptide(stack);
                        f2 *= riptideLevel / f5;
                        f3 *= riptideLevel / f5;
                        f4 *= riptideLevel / f5;
                        player.push(f2, f3, f4);
                        player.startAutoSpinAttack(20);
                        if (player.onGround()) {
                            player.move(MoverType.SELF, new Vec3(0.0, 1.1999999284744263, 0.0));
                        }
                        level.playSound(null, player, soundEvent, SoundSource.PLAYERS, 1.0F, 1.0F);
                    }
                }
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (isTooDamagedToUse(itemstack)) {
            return InteractionResultHolder.fail(itemstack);
        } else if (EnchantmentHelper.getRiptide(itemstack) > 0 && !player.isInWaterOrRain()) {
            return InteractionResultHolder.fail(itemstack);
        } else {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(itemstack);
        }
    }

    private static boolean isTooDamagedToUse(ItemStack stack) {
        return stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, (entity) -> entity.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        return true;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos, LivingEntity miningEntity) {
        if (state.getDestroySpeed(level, pos) != 0.0F) {
            stack.hurtAndBreak(2, miningEntity, (entity) -> entity.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 1;
    }

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<T> onBroken) {
        return amount;
    }
}