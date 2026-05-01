package com.adonis.fluid.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.ModList;
import org.apache.commons.lang3.tuple.Pair;

public class TwilightForestHelper {

    private static final String MOD_ID = "twilightforest";
    private static final int CLOUD_PRECIPITATION_DISTANCE = 32;
    private static final ResourceLocation FLUFFY_CLOUD_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "fluffy_cloud");
    private static final ResourceLocation RAINY_CLOUD_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "rainy_cloud");
    private static final ResourceLocation SNOWY_CLOUD_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "snowy_cloud");

    public static boolean isTwilightForestLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static Pair<Biome.Precipitation, Float> getCloudPrecipitationAt(Level level, BlockPos pos) {
        if (!isTwilightForestLoaded()) {
            return Pair.of(Biome.Precipitation.NONE, 0.0f);
        }

        for (int y = pos.getY(); y < pos.getY() + CLOUD_PRECIPITATION_DISTANCE; y++) {
            BlockPos cloudPos = pos.atY(y);
            BlockState state = level.getBlockState(cloudPos);

            Pair<Biome.Precipitation, Float> precipitation = getPrecipitationFromCloud(level, cloudPos, state);
            if (precipitation.getLeft() != Biome.Precipitation.NONE) {
                return precipitation;
            }

            if (Heightmap.Types.MOTION_BLOCKING.isOpaque().test(state)) {
                break;
            }
        }

        return Pair.of(Biome.Precipitation.NONE, 0.0f);
    }

    private static Pair<Biome.Precipitation, Float> getPrecipitationFromCloud(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        if (blockId == null) {
            return Pair.of(Biome.Precipitation.NONE, 0.0f);
        }

        if (RAINY_CLOUD_ID.equals(blockId)) {
            return Pair.of(Biome.Precipitation.RAIN, 1.0f);
        }

        if (SNOWY_CLOUD_ID.equals(blockId)) {
            return Pair.of(Biome.Precipitation.SNOW, 1.0f);
        }

        if (FLUFFY_CLOUD_ID.equals(blockId) && level.getRainLevel(1.0f) > 0.0f) {
            return Pair.of(level.getBiome(pos).value().getPrecipitationAt(pos), level.getRainLevel(1.0f));
        }

        return Pair.of(Biome.Precipitation.NONE, 0.0f);
    }
}
