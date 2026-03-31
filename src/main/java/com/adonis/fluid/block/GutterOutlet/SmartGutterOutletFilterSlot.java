package com.adonis.fluid.block.GutterOutlet;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform.Sided;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 智能集水器过滤器槽位的位置变换
 */
public class SmartGutterOutletFilterSlot extends Sided {

    @Override
    protected boolean isSideActive(BlockState state, Direction side) {
        // 只在宽面显示过滤器槽位
        return SmartGutterOutletBlock.isWideSide(state, side);
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        // 过滤器显示在宽面中间偏上的位置
        Direction facing = state.getValue(SmartGutterOutletBlock.FACING);
        Direction side = getSide();
        
        if (side == null) {
            side = facing;
        }

        // 在侧面中央位置
        return VecHelper.voxelSpace(8, 12, 15.5f);
    }

    @Override
    protected Vec3 getSouthLocation() {
        return VecHelper.voxelSpace(8, 12, 15.5f);
    }

    @Override
    public float getScale() {
        return 0.5f; // 稍微小一点的过滤器图标
    }
}
