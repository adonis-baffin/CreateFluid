package com.adonis.fluid.mixin;

import com.adonis.fluid.config.CFCommonConfig;
import com.adonis.fluid.item.CopperDivingLeggingsItem;
import com.adonis.fluid.item.NetheriteDivingLeggingsItem;
import com.simibubi.create.content.equipment.armor.DivingBootsItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 检测Create潜水靴跳跃并增强+标记保护
 * 适配1.20.1版本
 */
@Mixin(value = Player.class, priority = 900)
public class SuperJumpMarkerMixin {

    @Unique
    private boolean cfi_wasOnGround = true;

    @Unique
    private boolean cfi_jumpDetected = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void enhanceAndMarkJump(CallbackInfo ci) {
        Player player = (Player) (Object) this;

        // 检查装备
        boolean hasDivingBoots = player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof DivingBootsItem;
        boolean hasLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem
                || player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof NetheriteDivingLeggingsItem;

        // 必须在流体中并装备完整潜水装备
        if (!hasDivingBoots || !hasLeggings || (!player.isInWater() && !player.isInLava())) {
            cfi_wasOnGround = player.onGround();
            return;
        }

        // 检测跳跃：从地面起跳
        if (cfi_wasOnGround && !player.onGround() && player.getDeltaMovement().y > 0) {
            if (!cfi_jumpDetected) {
                cfi_jumpDetected = true;

                // 获取配置的跳跃力度
                double configJumpPower = CFCommonConfig.getDivingBaseJumpPower();
                if (player.isSprinting()) {
                    configJumpPower = CFCommonConfig.getDivingSprintJumpPower();
                }

                // Create默认添加0.5，我们需要额外添加的量
                double extraPower = configJumpPower - 0.5;

                // 只有当配置值大于0.5时才需要额外增强
                if (extraPower > 0) {
                    // 增强跳跃
                    Vec3 motion = player.getDeltaMovement();
                    player.setDeltaMovement(motion.add(0, extraPower, 0));
                }

                // 标记超级跳跃用于摔落保护
                player.getPersistentData().putBoolean("CFI_SuperJumpActive", true);

                // 消耗饥饿度
                if (!player.getAbilities().instabuild) {
                    float hungerCost = (float) CFCommonConfig.getDivingJumpHungerCost();
                    player.causeFoodExhaustion(hungerCost);
                }
            }
        }

        // 重置跳跃检测
        if (player.onGround()) {
            cfi_jumpDetected = false;
        }

        cfi_wasOnGround = player.onGround();
    }
}