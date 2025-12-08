package com.adonis.fluid.block.GutterOutlet;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 智能集水器过滤器槽位定位
 * 只在宽面显示（垂直于 FACING 的方向）
 */
public class SmartGutterOutletFilterSlot extends ValueBoxTransform.Sided {

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction side = getSide();
        float horizontalAngle = AngleHelper.horizontalAngle(side);
        Vec3 southLocation = VecHelper.voxelSpace(8, 11, 15.5f);
        return VecHelper.rotateCentered(southLocation, horizontalAngle, Axis.Y);
    }

    @Override
    protected boolean isSideActive(BlockState state, Direction direction) {
        if (!direction.getAxis().isHorizontal()) return false;

        Direction facing = state.getValue(SmartGutterOutletBlock.FACING);

        // 宽面是平行于 FACING 的方向（即 direction 的轴与 facing 的轴相同）
        // FACING=NORTH/SOUTH 时，宽面在 NORTH/SOUTH
        // FACING=EAST/WEST 时，宽面在 EAST/WEST
        return direction.getAxis() == facing.getAxis();  // 改为 ==
    }

    @Override
    protected Vec3 getSouthLocation() {
        return Vec3.ZERO;
    }
}