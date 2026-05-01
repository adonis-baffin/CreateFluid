package com.adonis.fluid.content.pipette;

import com.adonis.fluid.CreateFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class FluidInteractionPointTypes {

    private static final Map<Block, FluidInteractionPointFactory> BLOCK_REGISTRY = new LinkedHashMap<>();
    private static final Map<ResourceLocation, FluidInteractionPointFactory> DEFERRED_REGISTRY = new LinkedHashMap<>();
    private static final List<ConditionalFactory> CONDITIONAL_REGISTRY = new ArrayList<>();

    private static boolean deferredResolved = false;

    public static void register(Block block, FluidInteractionPointFactory factory) {
        BLOCK_REGISTRY.put(block, factory);
    }

    public static void registerDeferred(ResourceLocation blockId, FluidInteractionPointFactory factory) {
        DEFERRED_REGISTRY.put(blockId, factory);
    }

    public static void registerDeferred(String modId, String blockId, FluidInteractionPointFactory factory) {
        registerDeferred(ResourceLocation.fromNamespaceAndPath(modId, blockId), factory);
    }

    public static void registerConditional(Predicate<BlockState> condition, FluidInteractionPointFactory factory, int priority) {
        CONDITIONAL_REGISTRY.add(new ConditionalFactory(condition, factory, priority));
        CONDITIONAL_REGISTRY.sort(Comparator.comparingInt(cf -> cf.priority));
    }

    public static void resolveDeferredRegistrations() {
        if (deferredResolved) {
            return;
        }

        for (Map.Entry<ResourceLocation, FluidInteractionPointFactory> entry : DEFERRED_REGISTRY.entrySet()) {
            ResourceLocation blockId = entry.getKey();
            Block block = BuiltInRegistries.BLOCK.get(blockId);

            if (block != null && block != Blocks.AIR) {
                BLOCK_REGISTRY.put(block, entry.getValue());
                CreateFluid.LOGGER.info("Registered fluid interaction point for {}", blockId);
            } else {
                CreateFluid.LOGGER.debug("Could not resolve block for fluid interaction point: {}", blockId);
            }
        }

        deferredResolved = true;
    }

    @Nullable
    public static FluidInteractionPoint tryCreate(Level level, BlockPos pos, BlockState state) {
        if (!deferredResolved) {
            resolveDeferredRegistrations();
        }

        FluidInteractionPointFactory factory = BLOCK_REGISTRY.get(state.getBlock());
        if (factory != null) {
            return factory.create(level, pos, state);
        }

        for (ConditionalFactory conditionalFactory : CONDITIONAL_REGISTRY) {
            if (!conditionalFactory.condition.test(state)) {
                continue;
            }

            FluidInteractionPoint point = conditionalFactory.factory.create(level, pos, state);
            if (point != null) {
                return point;
            }
        }

        return null;
    }

    public static boolean isRegistered(BlockState state) {
        if (!deferredResolved) {
            resolveDeferredRegistrations();
        }

        if (BLOCK_REGISTRY.containsKey(state.getBlock())) {
            return true;
        }

        for (ConditionalFactory conditionalFactory : CONDITIONAL_REGISTRY) {
            if (conditionalFactory.condition.test(state)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @FunctionalInterface
    public interface FluidInteractionPointFactory {
        @Nullable
        FluidInteractionPoint create(Level level, BlockPos pos, BlockState state);
    }

    private static class ConditionalFactory {
        final Predicate<BlockState> condition;
        final FluidInteractionPointFactory factory;
        final int priority;

        ConditionalFactory(Predicate<BlockState> condition, FluidInteractionPointFactory factory, int priority) {
            this.condition = condition;
            this.factory = factory;
            this.priority = priority;
        }
    }
}
