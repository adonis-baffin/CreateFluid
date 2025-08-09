package com.adonis.fluid.config;

import com.adonis.fluid.CreateFluid;
import net.createmod.catnip.config.ConfigBase;

public class CFKinetics extends ConfigBase {
    public final CFStress stressValues;

    public CFKinetics() {
        this.stressValues = nested(
                0,
                () -> new CFStress(CreateFluid.MODID), // 使用lambda表达式传递参数
                "Fine tune the kinetic stats of individual components"
        );
    }

    @Override
    public String getName() {
        return "kinetics";
    }
}