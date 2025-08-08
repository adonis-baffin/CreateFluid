package com.adonis.fluid.mixin;

import com.adonis.fluid.item.CopperDivingLeggingsItem;
import com.adonis.fluid.item.NetheriteDivingLeggingsItem;
import com.simibubi.create.content.equipment.armor.DivingBootsItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 简化的潜水系统主Mixin
 * 只处理基础功能，不包含超级跳跃
 * 适配1.20.1版本
 */
@Mixin(value = Player.class, priority = 1100)
public abstract class DivingSystemMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void cfi_maintainCreateCompatibility(CallbackInfo ci) {
        Player player = (Player) (Object) this;

        // 检查是否装备了完整潜水套装
        boolean hasDivingBoots = player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof DivingBootsItem;
        boolean hasDivingLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem
                || player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof NetheriteDivingLeggingsItem;

        if (hasDivingBoots && hasDivingLeggings && (player.isInWater() || player.isInLava())) {
            // 基础潜水功能由Create模组的潜水靴处理
            // 这里可以添加额外的潜水护腿特定功能
        }
    }
}