package com.adonis.fluid.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class CFCommonConfig {
    public static final ModConfigSpec CONFIG_SPEC;

    // GutterOutlet 配置
    public static final ModConfigSpec.BooleanValue GUTTER_COLLECT_RAIN;
    public static final ModConfigSpec.BooleanValue GUTTER_COLLECT_SNOW;
    public static final ModConfigSpec.BooleanValue GUTTER_COLLECT_DRIPSTONE;
    public static final ModConfigSpec.BooleanValue GUTTER_COLLECT_WORLD_FLUID;
    
    // CopperSink 配置
    public static final ModConfigSpec.BooleanValue COPPER_SINK_INFINITE;

    // Centrifugal Pump 配置
    public static final ModConfigSpec.IntValue CENTRIFUGAL_PUMP_RANGE;

    // Copper Tap Experience Release 配置
    public static final ModConfigSpec.BooleanValue COPPER_TAP_EXPERIENCE_ENABLED;
    public static final ModConfigSpec.IntValue COPPER_TAP_EXPERIENCE_RATE;
    public static final ModConfigSpec.IntValue COPPER_TAP_EXPERIENCE_INTERVAL;

    private static boolean isConfigLoaded = false;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

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
        
        builder.comment("Copper Sink settings")
                .push("copper_sink");

        COPPER_SINK_INFINITE = builder
                .comment("Whether the Copper Sink provides infinite water (if false, it's just a normal 2000mB tank)")
                .define("infiniteWater", true);

        builder.pop();

        builder.comment("Centrifugal Pump settings")
                .push("centrifugal_pump");

        CENTRIFUGAL_PUMP_RANGE = builder
                .comment("Maximum transport distance in blocks for the Centrifugal Pump.")
                .defineInRange("centrifugalPumpRange", 20, 1, Integer.MAX_VALUE);

        builder.pop();

        builder.comment("Copper Tap Experience Release settings")
                .push("copper_tap_experience");

        COPPER_TAP_EXPERIENCE_ENABLED = builder
                .comment("Whether the Copper Tap can release experience orbs when open and no valid target is present.")
                .define("enabled", true);

        COPPER_TAP_EXPERIENCE_RATE = builder
                .comment("How many mB of experience fluid are consumed per release.")
                .defineInRange("rate", 10, 1, 1000);

        COPPER_TAP_EXPERIENCE_INTERVAL = builder
                .comment("Tick interval between each experience orb release.")
                .defineInRange("interval", 1, 1, 100);

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

    // CopperSink 配置获取方法
    public static boolean isCopperSinkInfinite() {
        if (!isConfigLoaded) {
            return true;
        }
        try {
            return COPPER_SINK_INFINITE.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    // Centrifugal Pump 配置获取方法
    public static int getCentrifugalPumpRange() {
        if (!isConfigLoaded) {
            return 20;
        }
        try {
            return CENTRIFUGAL_PUMP_RANGE.get();
        } catch (IllegalStateException e) {
            return 20;
        }
    }

    // Copper Tap Experience Release 配置获取方法
    public static boolean isCopperTapExperienceEnabled() {
        if (!isConfigLoaded) return true;
        try {
            return COPPER_TAP_EXPERIENCE_ENABLED.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static int getCopperTapExperienceRate() {
        if (!isConfigLoaded) return 10;
        try {
            return COPPER_TAP_EXPERIENCE_RATE.get();
        } catch (IllegalStateException e) {
            return 10;
        }
    }

    public static int getCopperTapExperienceInterval() {
        if (!isConfigLoaded) return 5;
        try {
            return COPPER_TAP_EXPERIENCE_INTERVAL.get();
        } catch (IllegalStateException e) {
            return 5;
        }
    }
}
