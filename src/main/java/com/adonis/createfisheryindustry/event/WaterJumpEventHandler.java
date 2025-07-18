package com.adonis.createfisheryindustry.event;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.item.CopperDivingLeggingsItem;
import com.adonis.createfisheryindustry.item.NetheriteDivingLeggingsItem;
import com.simibubi.create.content.equipment.armor.DivingBootsItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = CreateFisheryMod.MODID)
public class WaterJumpEventHandler {
    private static boolean jumpHandled = false;

    /**
     * 捕获玩家跳跃事件
     */
    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        // 检查是否装备潜水靴
        if (!DivingBootsItem.isWornBy(player)) return;

        // 检查是否在水中且装备铜质或下界合金潜水护腿
        if (!player.isInWater()) return;
        boolean hasLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem
                || player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof NetheriteDivingLeggingsItem;
        if (!hasLeggings) return;

        // 施加增强的 Y 速度以实现高跳
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, 0.8, motion.z);
        player.getPersistentData().putBoolean("EnhancedJumpActive", true);
        jumpHandled = true;
    }

    /**
     * 在 LivingTickEvent 处理潜泳或漂浮状态的跳跃
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        // 检查是否装备潜水靴
        if (!DivingBootsItem.isWornBy(player)) {
            jumpHandled = false;
            return;
        }

        // 检查是否在水中且装备铜质或下界合金潜水护腿
        if (!player.isInWater()) {
            jumpHandled = false;
            player.getPersistentData().putBoolean("EnhancedJumpActive", false);
            return;
        }
        
        boolean hasLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem
                || player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof NetheriteDivingLeggingsItem;
        if (!hasLeggings) {
            jumpHandled = false;
            return;
        }

        Vec3 motion = player.getDeltaMovement();
        
        // 检测潜泳或漂浮时的跳跃意图
        boolean isSwimmingJump = player.isSwimming() && player.getLookAngle().y > 0.0 && motion.y < 0.5 && !jumpHandled;
        // 检测向上看时的跳跃意图
        boolean isLookingUpJump = !player.isSwimming() && player.getLookAngle().y > 0.3 && motion.y < 0.3 && !jumpHandled;
        
        if (isSwimmingJump || isLookingUpJump) {
            player.setDeltaMovement(motion.x, 0.8, motion.z);
            player.getPersistentData().putBoolean("EnhancedJumpActive", true);
            jumpHandled = true;
        }

        // 重置 jumpHandled，当玩家触地或离开水或向下运动
        if (player.onGround() || !player.isInWater() || motion.y < -0.1) {
            jumpHandled = false;
            if (player.onGround() || !player.isInWater()) {
                player.getPersistentData().putBoolean("EnhancedJumpActive", false);
            }
        }
    }

    /**
     * 使用LOW优先级在潜水靴处理后进行增强
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void enhanceAfterDivingBoots(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        // 检查是否装备潜水靴和护腿
        if (!DivingBootsItem.isWornBy(player)) return;
        if (!player.isInWater()) return;
        
        boolean hasLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem
                || player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof NetheriteDivingLeggingsItem;
        if (!hasLeggings) return;

        // 如果增强跳跃激活且Y速度被潜水靴降低，恢复增强
        if (jumpHandled && player.getPersistentData().getBoolean("EnhancedJumpActive")) {
            Vec3 motion = player.getDeltaMovement();
            if (motion.y < 0.3) {
                player.setDeltaMovement(motion.x, Math.max(motion.y, 0.6), motion.z);
            }
        }
    }
}