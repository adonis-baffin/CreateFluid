package com.adonis.fluid.fluid.powdersnow;

import com.simibubi.create.content.fluids.VirtualFluid;
import net.minecraftforge.fluids.ForgeFlowingFluid;

/**
 * 细雪虚拟流体
 * 与机械动力的TEA对齐 - 无OpenPipe效果，无世界交互
 * 虚拟流体只能在管道系统中传输，不能在世界中放置
 */
public class PowderSnowFluid extends VirtualFluid {

    public static PowderSnowFluid createSource(ForgeFlowingFluid.Properties properties) {
        return new PowderSnowFluid(properties, true);
    }

    public static PowderSnowFluid createFlowing(ForgeFlowingFluid.Properties properties) {
        return new PowderSnowFluid(properties, false);
    }

    public PowderSnowFluid(Properties properties, boolean source) {
        super(properties, source);
    }
    
    public boolean isLighterThanAir() {
        return false;
    }
}