package com.adonis.fluid.block.SmartFluidInterface;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SmartFluidInterfaceFilterSlot extends ValueBoxTransform.Sided {

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(SmartFluidInterfaceBlock.FACING);
        Direction side = getSide();

        // 只在面向外的那一面显示过滤槽位
        if (side == facing) {
            // 计算过滤槽位在突出部分外表面的位置
            return switch (facing) {
                case NORTH -> VecHelper.voxelSpace(8, 8, 10.5); // 在最突出的部分外表面中央
                case SOUTH -> VecHelper.voxelSpace(8, 8, 5.5);
                case EAST -> VecHelper.voxelSpace(5.5, 8, 8);
                case WEST -> VecHelper.voxelSpace(10.5, 8, 8);
                default -> VecHelper.voxelSpace(8, 8, 10.5);
            };
        }

        // 默认位置（不应该到达这里，因为isSideActive会过滤掉其他面）
        return VecHelper.voxelSpace(8, 8, 8);
    }

    @Override
    protected boolean isSideActive(BlockState state, Direction direction) {
        Direction facing = state.getValue(SmartFluidInterfaceBlock.FACING);
        // 只在面向外的那一面显示过滤槽位（突出部分的外表面）
        return direction == facing;
    }

    @Override
    protected Vec3 getSouthLocation() {
        return VecHelper.voxelSpace(8, 8, 5.5);
    }
}