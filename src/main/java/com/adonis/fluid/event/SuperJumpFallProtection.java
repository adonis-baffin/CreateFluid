package com.adonis.fluid.event;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.config.CFCommonConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 处理超级跳跃的摔落保护
 * 只保护标记为超级跳跃的摔落
 * 适配1.20.1版本
 */
@Mod.EventBusSubscriber(modid = CreateFluid.MODID)
public class SuperJumpFallProtection {

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        // 检查配置
        if (!CFCommonConfig.shouldPreventDivingFallDamage()) {
            return;
        }

        LivingEntity entity = event.getEntity();

        // 检查超级跳跃标记
        if (entity.getPersistentData().getBoolean("CFI_SuperJumpActive")) {
            // 取消摔落伤害
            event.setDamageMultiplier(0.0F);
            event.setCanceled(true);

            // 清除标记
            entity.getPersistentData().remove("CFI_SuperJumpActive");
        }
    }
}