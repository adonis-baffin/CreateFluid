package com.adonis.fluid.fluid.powdersnow;

import com.adonis.fluid.registry.CFFluid;
import com.simibubi.create.api.behaviour.spouting.CauldronSpoutingBehavior;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;

/**
 * 注册细雪炼药锅的注液器支持
 * 使用机械动力原版的 CauldronSpoutingBehavior 系统
 */
public class PowderSnowCauldronSpoutingRegistry {

    /**
     * 注册细雪炼药锅到注液器系统
     * 应该在模组初始化时调用（FMLCommonSetupEvent）
     */
    public static void register() {
        // 使用机械动力的 CAULDRON_INFO 注册表注册细雪
        // 1000mB 一次性填满细雪炼药锅（3层）
        CauldronSpoutingBehavior.CAULDRON_INFO.register(
                CFFluid.POWDER_SNOW.get(),  // 注册细雪流体源
                new CauldronSpoutingBehavior.CauldronInfo(
                        1000,  // 需要1000mB
                        Blocks.POWDER_SNOW_CAULDRON.defaultBlockState()
                                .setValue(LayeredCauldronBlock.LEVEL, 3)  // 直接填满（3层）
                )
        );

        // 也为流动形态注册（虽然虚拟流体通常只用源形态）
        CauldronSpoutingBehavior.CAULDRON_INFO.register(
                CFFluid.POWDER_SNOW.get().getFlowing(),
                new CauldronSpoutingBehavior.CauldronInfo(
                        1000,
                        Blocks.POWDER_SNOW_CAULDRON.defaultBlockState()
                                .setValue(LayeredCauldronBlock.LEVEL, 3)
                )
        );
    }
}