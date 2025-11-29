package com.adonis.fluid.content.pipette;

import com.adonis.fluid.CreateFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

/**
 * 流体交互点类型注册表
 * 用于注册和管理可被移液器交互的方块类型
 */
public class FluidInteractionPointTypes {
    
    // 基于Block的注册表
    private static final Map<Block, FluidInteractionPointFactory> BLOCK_REGISTRY = new LinkedHashMap<>();
    
    // 基于ResourceLocation的延迟注册表（用于跨模组兼容）
    private static final Map<ResourceLocation, FluidInteractionPointFactory> DEFERRED_REGISTRY = new LinkedHashMap<>();
    
    // 基于条件的注册表（用于更灵活的匹配）
    private static final List<ConditionalFactory> CONDITIONAL_REGISTRY = new ArrayList<>();
    
    // 是否已初始化延迟注册
    private static boolean deferredResolved = false;
    
    /**
     * 注册一个方块的交互点工厂
     */
    public static void register(Block block, FluidInteractionPointFactory factory) {
        BLOCK_REGISTRY.put(block, factory);
    }
    
    /**
     * 延迟注册 - 通过ResourceLocation注册（用于可能未加载的模组方块）
     */
    public static void registerDeferred(ResourceLocation blockId, FluidInteractionPointFactory factory) {
        DEFERRED_REGISTRY.put(blockId, factory);
    }
    
    /**
     * 延迟注册的便捷方法
     */
    public static void registerDeferred(String modId, String blockId, FluidInteractionPointFactory factory) {
        registerDeferred(new ResourceLocation(modId, blockId), factory);
    }
    
    /**
     * 条件注册 - 通过条件匹配（用于匹配一类方块，如所有蜂巢）
     */
    public static void registerConditional(Predicate<BlockState> condition, FluidInteractionPointFactory factory, int priority) {
        CONDITIONAL_REGISTRY.add(new ConditionalFactory(condition, factory, priority));
        CONDITIONAL_REGISTRY.sort(Comparator.comparingInt(cf -> cf.priority));
    }
    
    /**
     * 解析延迟注册的方块
     * 应在游戏加载完成后调用
     */
    public static void resolveDeferredRegistrations() {
        if (deferredResolved) return;
        
        for (Map.Entry<ResourceLocation, FluidInteractionPointFactory> entry : DEFERRED_REGISTRY.entrySet()) {
            ResourceLocation blockId = entry.getKey();
            Block block = ForgeRegistries.BLOCKS.getValue(blockId);
            
            if (block != null && block != net.minecraft.world.level.block.Blocks.AIR) {
                BLOCK_REGISTRY.put(block, entry.getValue());
                CreateFluid.LOGGER.info("Registered fluid interaction point for: {}", blockId);
            } else {
                CreateFluid.LOGGER.debug("Could not resolve block for fluid interaction point: {} (mod may not be loaded)", blockId);
            }
        }
        
        deferredResolved = true;
    }
    
    /**
     * 尝试为给定位置创建交互点
     * @return 交互点，如果没有匹配的注册则返回null
     */
    @Nullable
    public static FluidInteractionPoint tryCreate(Level level, BlockPos pos, BlockState state) {
        // 确保延迟注册已解析
        if (!deferredResolved) {
            resolveDeferredRegistrations();
        }
        
        // 1. 首先检查直接注册的方块
        FluidInteractionPointFactory factory = BLOCK_REGISTRY.get(state.getBlock());
        if (factory != null) {
            return factory.create(level, pos, state);
        }
        
        // 2. 检查条件注册
        for (ConditionalFactory cf : CONDITIONAL_REGISTRY) {
            if (cf.condition.test(state)) {
                FluidInteractionPoint point = cf.factory.create(level, pos, state);
                if (point != null) {
                    return point;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 检查方块是否已注册
     */
    public static boolean isRegistered(BlockState state) {
        if (!deferredResolved) {
            resolveDeferredRegistrations();
        }
        
        if (BLOCK_REGISTRY.containsKey(state.getBlock())) {
            return true;
        }
        
        for (ConditionalFactory cf : CONDITIONAL_REGISTRY) {
            if (cf.condition.test(state)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 检查模组是否已加载
     */
    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
    
    /**
     * 交互点工厂接口
     */
    @FunctionalInterface
    public interface FluidInteractionPointFactory {
        @Nullable
        FluidInteractionPoint create(Level level, BlockPos pos, BlockState state);
    }
    
    /**
     * 条件工厂包装类
     */
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