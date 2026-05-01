package com.adonis.fluid.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.fml.ModList;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class TwilightForestHelper {

    private static final String MOD_ID = "twilightforest";
    private static final boolean TWILIGHT_LOADED;
    private static final MethodHandle GET_CLOUD_PRECIP;

    static {
        boolean loaded = ModList.get().isLoaded(MOD_ID);
        MethodHandle methodHandle = null;

        if (loaded) {
            try {
                Class<?> cloudHelper = Class.forName("twilightforest.util.CloudHelper");
                methodHandle = MethodHandles.lookup().findStatic(
                        cloudHelper,
                        "getCloudPrecipitationAt",
                        MethodType.methodType(Pair.class, Level.class, BlockPos.class)
                );
            } catch (Throwable ignored) {
                loaded = false;
            }
        }

        TWILIGHT_LOADED = loaded;
        GET_CLOUD_PRECIP = methodHandle;
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
        } catch (Throwable ignored) {
            return Pair.of(Biome.Precipitation.NONE, 0.0f);
        }
    }
}
