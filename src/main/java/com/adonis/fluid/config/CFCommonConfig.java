package com.adonis.fluid.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class CFCommonConfig {
    public static final ForgeConfigSpec CONFIG_SPEC;

    // GutterOutlet 配置
    public static final ForgeConfigSpec.BooleanValue GUTTER_COLLECT_RAIN;
    public static final ForgeConfigSpec.BooleanValue GUTTER_COLLECT_SNOW;
    public static final ForgeConfigSpec.BooleanValue GUTTER_COLLECT_DRIPSTONE;
    public static final ForgeConfigSpec.BooleanValue GUTTER_COLLECT_WORLD_FLUID;

    private static boolean isConfigLoaded = false;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Gutter Outlet settings")
                .push("gutter_outlet");

        GUTTER_COLLECT_RAIN = builder
                .comment("Whether gutter outlet can collect rain water")
                .define("collectRain", true);

        GUTTER_COLLECT_SNOW = builder
                .comment("Whether gutter outlet can collect snow as powder snow fluid")
                .define("collectSnow", true);

        GUTTER_COLLECT_DRIPSTONE = builder
                .comment("Whether gutter outlet can collect fluid from dripstone (lava/water)")
                .define("collectDripstone", true);

        GUTTER_COLLECT_WORLD_FLUID = builder
                .comment("Whether gutter outlet can collect world fluid sources from above")
                .define("collectWorldFluid", true);

        builder.pop();

        CONFIG_SPEC = builder.build();
    }

    public static void onLoad() {
        isConfigLoaded = true;
    }

    public static void onReload() {
        isConfigLoaded = true;
    }

    // GutterOutlet 配置获取方法
    public static boolean canGutterCollectRain() {
        if (!isConfigLoaded) {
            return true;
        }
        try {
            return GUTTER_COLLECT_RAIN.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean canGutterCollectSnow() {
        if (!isConfigLoaded) {
            return true;
        }
        try {
            return GUTTER_COLLECT_SNOW.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean canGutterCollectDripstone() {
        if (!isConfigLoaded) {
            return true;
        }
        try {
            return GUTTER_COLLECT_DRIPSTONE.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean canGutterCollectWorldFluid() {
        if (!isConfigLoaded) {
            return true;
        }
        try {
            return GUTTER_COLLECT_WORLD_FLUID.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }
}