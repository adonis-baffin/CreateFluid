package com.adonis.fluid.config;

import net.createmod.catnip.config.ConfigBase;

public class CFServer extends ConfigBase {
    public final CFKinetics kinetics;

    public CFServer() {
        this.kinetics = nested(0, CFKinetics::new, "Parameters and abilities of Create Fluid's kinetic mechanisms");
    }

    @Override
    public String getName() {
        return "server";
    }
}