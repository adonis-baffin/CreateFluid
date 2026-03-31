package com.adonis.fluid.registry;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * 方块碰撞箱形状定义
 */
public class CFShapes {

    // 离心泵的各种朝向形状
    public static final Map<Direction, VoxelShape> CENTRIFUGAL_PUMP_FLOOR = new EnumMap<>(Direction.class);
    public static final Map<Direction, VoxelShape> CENTRIFUGAL_PUMP_CEILING = new EnumMap<>(Direction.class);
    public static final Map<Direction, VoxelShape> CENTRIFUGAL_PUMP_WALL = new EnumMap<>(Direction.class);

    static {
        // 地板模式 - 基础形状 12x12x12 中心放置
        VoxelShape floorBase = Block.box(2, 2, 2, 14, 14, 14);
        
        // 天花板模式 - 与地板相同但位置可能需要调整
        VoxelShape ceilingBase = Block.box(2, 2, 2, 14, 14, 14);
        
        // 墙壁模式
        VoxelShape wallBase = Block.box(2, 2, 2, 14, 14, 14);

        // 为每个方向填充形状（可以后续添加更精确的形状）
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            CENTRIFUGAL_PUMP_FLOOR.put(dir, floorBase);
            CENTRIFUGAL_PUMP_CEILING.put(dir, ceilingBase);
            CENTRIFUGAL_PUMP_WALL.put(dir, wallBase);
        }
    }
}
