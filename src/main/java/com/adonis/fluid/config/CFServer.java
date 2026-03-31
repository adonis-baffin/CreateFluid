package com.adonis.fluid.config;

import net.createmod.catnip.config.ConfigBase;

public class CFServer extends ConfigBase {
    // 与原版 CServer 一致：直接在字段声明时初始化 nested 配置
    public final CFKinetics kinetics = nested(0, CFKinetics::new, Comments.kinetics);

    @Override
    public String getName() {
        return "server";
    }
    
    private static class Comments {
        static String kinetics = "Parameters and abilities of Create Fluid's kinetic mechanisms";
    }
}