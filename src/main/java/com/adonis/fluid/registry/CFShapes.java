package com.adonis.fluid.registry;

import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CFShapes {

    // 离心泵形状定义
    public static final VoxelShaper CENTRIFUGAL_PUMP_FLOOR;
    public static final VoxelShaper CENTRIFUGAL_PUMP_CEILING;
    public static final VoxelShaper CENTRIFUGAL_PUMP_WALL;

    static {
        // 水平模式 - FLOOR (放在地上，base朝下)
        // 基于 block.json 模型
        VoxelShape floorShape = Shapes.or(
                Block.box(2, 2, 2, 14, 14, 14),      // PumpCenter
                Block.box(3, 3, 0, 13, 13, 2),       // pipe_front
                Block.box(3, 14, 3, 13, 16, 13),     // pipe_up
                Block.box(3, 3, 13, 13, 13, 15),     // back_for_rotate
                Block.box(0, 4, 4, 2, 12, 12),       // adjust_left
                Block.box(14, 4, 4, 16, 12, 12)      // adjust_right
                // base部分平整，不需要额外的碰撞箱
        );
        CENTRIFUGAL_PUMP_FLOOR = VoxelShaper.forHorizontal(floorShape, Direction.NORTH);

        // 水平模式 - CEILING (贴在天花板，base朝上)
        // 模型翻转180度
        VoxelShape ceilingShape = Shapes.or(
                Block.box(2, 2, 2, 14, 14, 14),      // PumpCenter
                Block.box(3, 3, 0, 13, 13, 2),       // pipe_front
                Block.box(3, 0, 3, 13, 2, 13),       // pipe_down
                Block.box(3, 3, 14, 13, 13, 15),     // back_for_rotate
                Block.box(0, 4, 4, 2, 12, 12),       // adjust_left
                Block.box(14, 4, 4, 16, 12, 12)      // adjust_right
        );
        CENTRIFUGAL_PUMP_CEILING = VoxelShaper.forHorizontal(ceilingShape, Direction.NORTH);

        // 垂直模式 - WALL (贴在墙上)
        // 基于 block_vertical.json 模型
        VoxelShape wallShape = Shapes.or(
                Block.box(2, 2, 2, 14, 14, 14),      // PumpCenter
                Block.box(3, 0, 3, 13, 2, 13),       // pipe_down
                Block.box(3, 3, 0, 13, 13, 2),       // pipe_side
                Block.box(3, 14, 3, 13, 15, 13),     // top_for_stress
                Block.box(0, 4, 4, 2, 12, 12),       // adjust_front
                Block.box(14, 4, 4, 16, 12, 12)      // adjust_back
                // base_front 和 base_back 在背面，不影响功能性碰撞箱
        );
        CENTRIFUGAL_PUMP_WALL = VoxelShaper.forHorizontal(wallShape, Direction.NORTH);
    }

    // 用于创建形状的辅助方法
    private static VoxelShape shape(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Block.box(x1, y1, z1, x2, y2, z2);
    }
}