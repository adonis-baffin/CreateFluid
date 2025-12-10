package com.adonis.fluid.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class TwilightForestHelper {

    private static final boolean TWILIGHT_LOADED;
    private static final MethodHandle GET_CLOUD_PRECIP;

    static {
        boolean loaded = false;
        MethodHandle mh = null;
        try {
            Class<?> cloudHelper = Class.forName("twilightforest.util.CloudHelper");
            mh = MethodHandles.lookup().findStatic(
                cloudHelper,
                "getCloudPrecipitationAt",
                MethodType.methodType(Pair.class, Level.class, BlockPos.class)
            );
            loaded = true;
        } catch (Throwable t) {
            // Twilight Forest 没装，或者版本不对，直接吃掉异常
        }
        TWILIGHT_LOADED = loaded;
        GET_CLOUD_PRECIP = mh;
    }

    public static boolean isTwilightForestLoaded() {
        return TWILIGHT_LOADED;
    }

    @SuppressWarnings("unchecked")
    public static Pair<Biome.Precipitation, Float> getCloudPrecipitationAt(Level level, BlockPos pos) {
        if (!TWILIGHT_LOADED || GET_CLOUD_PRECIP == null) {
            return Pair.of(Biome.Precipitation.NONE, 0.0f);
        }
        try {
            return (Pair<Biome.Precipitation, Float>) GET_CLOUD_PRECIP.invoke(level, pos);
        } catch (Throwable t) {
            return Pair.of(Biome.Precipitation.NONE, 0.0f);
        }
    }
}