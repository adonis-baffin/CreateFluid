package com.adonis.fluid.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class CFCommonConfig {
    public static final ForgeConfigSpec CONFIG_SPEC;

    // Aqueduct 配置
    public static final ForgeConfigSpec.IntValue AQUEDUCT_TRANSFER_RATE;
    public static final ForgeConfigSpec.IntValue WATER_SOURCE_FILL_RATE;
    public static final ForgeConfigSpec.IntValue MAX_AQUEDUCT_LENGTH;
    public static final ForgeConfigSpec.BooleanValue PRIORITIZE_AQUEDUCT;

    private static boolean isConfigLoaded = false;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Aqueduct system settings")
                .push("aqueduct");

        AQUEDUCT_TRANSFER_RATE = builder
                .comment("Transfer rate for aqueduct system in mB per tick")
                .defineInRange("transferRate", 50, 1, 1000);

        WATER_SOURCE_FILL_RATE = builder
                .comment("Fill rate from water source blocks in mB per tick")
                .defineInRange("waterFillRate", 50, 1, 1000);

        MAX_AQUEDUCT_LENGTH = builder
                .comment("Maximum length of a single aqueduct network")
                .defineInRange("maxLength", 256, 16, 1024);

        PRIORITIZE_AQUEDUCT = builder
                .comment("Whether aqueduct transfer takes priority over pipe transfer")
                .define("prioritizeAqueduct", true);

        builder.pop();

        CONFIG_SPEC = builder.build();
    }

    public static void onLoad() {
        isConfigLoaded = true;
    }

    public static void onReload() {
        isConfigLoaded = true;
    }

    // Aqueduct 系统配置获取方法
    public static int getAqueductTransferRate() {
        if (!isConfigLoaded) {
            return 50;
        }
        try {
            return AQUEDUCT_TRANSFER_RATE.get();
        } catch (IllegalStateException e) {
            return 50;
        }
    }

    public static int getWaterSourceFillRate() {
        if (!isConfigLoaded) {
            return 50;
        }
        try {
            return WATER_SOURCE_FILL_RATE.get();
        } catch (IllegalStateException e) {
            return 50;
        }
    }

    public static int getMaxAqueductLength() {
        if (!isConfigLoaded) {
            return 256;
        }
        try {
            return MAX_AQUEDUCT_LENGTH.get();
        } catch (IllegalStateException e) {
            return 256;
        }
    }

    public static boolean isPrioritizeAqueduct() {
        if (!isConfigLoaded) {
            return true;
        }
        try {
            return PRIORITIZE_AQUEDUCT.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }
}