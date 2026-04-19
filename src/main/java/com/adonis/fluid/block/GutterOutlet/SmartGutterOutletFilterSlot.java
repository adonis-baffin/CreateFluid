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
    protected Vec3 getSouthLocation() {
        return VecHelper.voxelSpace(8, 11, 15.5f);
    }

    @Override
    public float getScale() {
        return 0.5f; // 稍微小一点的过滤器图标
    }
}
