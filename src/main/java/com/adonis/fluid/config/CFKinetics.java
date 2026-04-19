package com.adonis.fluid.config;

import net.createmod.catnip.config.ConfigBase;

public class CFKinetics extends ConfigBase {
    // 应力值配置 - 使用 nested 嵌套（必须在字段声明时初始化，不能在构造函数中）
    // 应力值配置 - 使用 nested 嵌套（CFStress使用静态默认值存储）
    public final CFStress stressValues = nested(1, CFStress::new, Comments.stress);

    @Override
    public String getName() {
        return "kinetics";
    }

    private static class Comments {
        static String stress = "Fine tune the kinetic stats of individual components";
    }
}