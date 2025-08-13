package com.adonis.fluid.content.aqueduct;

import com.adonis.fluid.block.aqueduct.AbstractAqueductBlock;
import com.adonis.fluid.content.aqueduct.AqueductNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class AqueductPropagator {
    
    public static void onAqueductPlaced(Level level, BlockPos pos) {
        if (!level.isClientSide) {
            // 使网络缓存失效
            AqueductNetworkManager.getInstance().invalidateNetwork(level, pos);
            
            // 标记位置为脏
            AqueductNetworkManager.getInstance().markDirty(level, pos);
            
            // 通知相邻水渠
            notifyNeighbors(level, pos);
        }
    }

    public static void onAqueductRemoved(Level level, BlockPos pos) {
        if (!level.isClientSide) {
            // 使网络缓存失效
            AqueductNetworkManager.getInstance().invalidateNetwork(level, pos);
            
            // 通知相邻水渠
            notifyNeighbors(level, pos);
        }
    }

    public static void notifyNetworkUpdate(Level level, BlockPos pos) {
        if (!level.isClientSide) {
            AqueductNetworkManager.getInstance().markDirty(level, pos);
        }
    }

    private static void notifyNeighbors(Level level, BlockPos pos) {
        // 通知前后的水渠
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(dir);
            if (level.getBlockState(neighborPos).getBlock() instanceof AbstractAqueductBlock) {
                AqueductNetworkManager.getInstance().markDirty(level, neighborPos);
            }
        }
    }
}