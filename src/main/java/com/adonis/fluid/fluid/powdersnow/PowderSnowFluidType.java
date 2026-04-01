package com.adonis.fluid.fluid.powdersnow;

import net.neoforged.neoforge.fluids.FluidType;

/**
 * 细雪流体类型
 */
public class PowderSnowFluidType {

    public static final FluidType.Properties PROPERTIES = FluidType.Properties.create()
            .density(500)
            .temperature(250)
            .viscosity(2000)
            .canSwim(false)
            .canDrown(true);

    public static FluidType create() {
        return new FluidType(PROPERTIES);
    }
}
