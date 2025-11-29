package com.adonis.fluid.compat;

import net.minecraftforge.fml.ModList;

/**
 * TFMG 模组兼容检测
 */
public class TFMGCompat {
    
    private static final String TFMG_MODID = "tfmg";
    private static Boolean isTFMGLoaded = null;
    
    /**
     * 检查 TFMG 是否已加载
     */
    public static boolean isLoaded() {
        if (isTFMGLoaded == null) {
            isTFMGLoaded = ModList.get().isLoaded(TFMG_MODID);
        }
        return isTFMGLoaded;
    }
}