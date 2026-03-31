package com.adonis.fluid.config;

import net.createmod.catnip.config.ConfigBase;

public class CFKinetics extends ConfigBase {
    // 离心泵配置 - 使用 ConfigInt 以在游戏内配置界面显示（与原版 mechanicalPumpRange 格式一致）
    public final ConfigInt centrifugalPumpRange = i(20, 1, "centrifugalPumpRange", Comments.blocks, Comments.centrifugalPumpRange);
    
    // 应力值配置 - 使用 nested 嵌套（必须在字段声明时初始化，不能在构造函数中）
    // 应力值配置 - 使用 nested 嵌套（CFStress使用静态默认值存储）
    public final CFStress stressValues = nested(1, CFStress::new, Comments.stress);

    /**
     * 获取离心泵最大运输距离
     */
    public int getCentrifugalPumpRange() {
        try {
            return centrifugalPumpRange.get();
        } catch (IllegalStateException e) {
            return 20; // 默认值
        }
    }

    @Override
    public String getName() {
        return "kinetics";
    }
    
    private static class Comments {
        static String blocks = "[in Blocks]";
        static String centrifugalPumpRange = "Maximum transport distance in blocks for the Centrifugal Pump.";
        static String stress = "Fine tune the kinetic stats of individual components";
    }
}