package com.adonis.fluid.block.CentrifugalPump;

import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.jetbrains.annotations.Nullable;

public class CentrifugalPumpCTBehaviour extends ConnectedTextureBehaviour.Base {

    @Override
    public @Nullable CTSpriteShiftEntry getShift(BlockState state, Direction direction,
                                                 @Nullable TextureAtlasSprite sprite) {
        if (!state.getValue(CentrifugalPumpBlock.ENCASED)) {
            return null;
        }

        // 获取实际的功能面方向
        Direction actualPrimary = getActualDirection(state, CentrifugalPumpBlock.getPrimaryFluidDirection(state));
        Direction actualSecondary = getActualDirection(state, CentrifugalPumpBlock.getSecondaryFluidDirection(state));
        Direction actualShaft = getActualDirection(state, CentrifugalPumpBlock.getShaftDirection(state));

        if (direction == actualPrimary || direction == actualSecondary || direction == actualShaft) {
            return null;
        }

        return AllSpriteShifts.COPPER_CASING;
    }

    @Override
    public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader,
                              BlockPos pos, BlockPos otherPos, Direction face) {
        if (!state.getValue(CentrifugalPumpBlock.ENCASED)) {
            return false;
        }

        // ==================== 调整点 1: 功能面方向获取 ====================
        // 获取实际的功能面方向
        Direction actualPrimary = getActualDirection(state, CentrifugalPumpBlock.getPrimaryFluidDirection(state));
        Direction actualSecondary = getActualDirection(state, CentrifugalPumpBlock.getSecondaryFluidDirection(state));
        Direction actualShaft = getActualDirection(state, CentrifugalPumpBlock.getShaftDirection(state));

        // 备选方案A: 直接使用模型方向，不进行转换
        // Direction actualPrimary = CentrifugalPumpBlock.getPrimaryFluidDirection(state);
        // Direction actualSecondary = CentrifugalPumpBlock.getSecondaryFluidDirection(state);
        // Direction actualShaft = CentrifugalPumpBlock.getShaftDirection(state);

        if (face == actualPrimary || face == actualSecondary || face == actualShaft) {
            return false;
        }

        // 连接到铜机壳
        if (other.is(com.simibubi.create.AllBlocks.COPPER_CASING.get())) {
            return true;
        }

        // 连接到其他封装的离心泵
        if (other.getBlock() instanceof CentrifugalPumpBlock && other.getValue(CentrifugalPumpBlock.ENCASED)) {
            Direction otherActualPrimary = getActualDirection(other, CentrifugalPumpBlock.getPrimaryFluidDirection(other));
            Direction otherActualSecondary = getActualDirection(other, CentrifugalPumpBlock.getSecondaryFluidDirection(other));
            Direction otherActualShaft = getActualDirection(other, CentrifugalPumpBlock.getShaftDirection(other));

            Direction oppositeFace = face.getOpposite();
            return oppositeFace != otherActualPrimary && oppositeFace != otherActualSecondary && oppositeFace != otherActualShaft;
        }

        return false;
    }

    /**
     * 根据附着面调整方向映射
     * 这里可以手动调整每个状态下的方向映射
     */
    private Direction getActualDirection(BlockState state, Direction modelDirection) {
        AttachFace attachFace = state.getValue(CentrifugalPumpBlock.FACE);
        Direction facing = state.getValue(CentrifugalPumpBlock.FACING);

        // ==================== 调整点 2: FLOOR 状态方向映射 ====================
        if (attachFace == AttachFace.FLOOR) {
            // 方案1: 直接返回模型方向（默认）
            return modelDirection;

            // 方案2: 根据facing旋转
            /*
            switch (facing) {
                case NORTH: return modelDirection;
                case SOUTH:
                    if (modelDirection.getAxis() != Direction.Axis.Y) {
                        return modelDirection.getOpposite();
                    }
                    return modelDirection;
                case EAST:
                    if (modelDirection.getAxis() != Direction.Axis.Y) {
                        return modelDirection.getClockWise();
                    }
                    return modelDirection;
                case WEST:
                    if (modelDirection.getAxis() != Direction.Axis.Y) {
                        return modelDirection.getCounterClockWise();
                    }
                    return modelDirection;
                default: return modelDirection;
            }
            */

            // 方案3: 完全重映射
            /*
            switch (facing) {
                case NORTH:
                    return modelDirection;
                case SOUTH:
                    switch (modelDirection) {
                        case NORTH: return Direction.SOUTH;
                        case SOUTH: return Direction.NORTH;
                        case EAST: return Direction.WEST;
                        case WEST: return Direction.EAST;
                        default: return modelDirection;
                    }
                case EAST:
                    switch (modelDirection) {
                        case NORTH: return Direction.EAST;
                        case SOUTH: return Direction.WEST;
                        case EAST: return Direction.SOUTH;
                        case WEST: return Direction.NORTH;
                        default: return modelDirection;
                    }
                case WEST:
                    switch (modelDirection) {
                        case NORTH: return Direction.WEST;
                        case SOUTH: return Direction.EAST;
                        case EAST: return Direction.NORTH;
                        case WEST: return Direction.SOUTH;
                        default: return modelDirection;
                    }
                default: return modelDirection;
            }
            */
        }

        // ==================== 调整点 3: CEILING 状态方向映射 ====================
        if (attachFace == AttachFace.CEILING) {
            // 方案1: 只翻转上下
            if (modelDirection == Direction.UP) return Direction.DOWN;
            if (modelDirection == Direction.DOWN) return Direction.UP;
            return modelDirection;

            // 方案2: 翻转上下 + 水平180度旋转
            /*
            switch (modelDirection) {
                case UP: return Direction.DOWN;
                case DOWN: return Direction.UP;
                case NORTH: return Direction.SOUTH;
                case SOUTH: return Direction.NORTH;
                case EAST: return Direction.WEST;
                case WEST: return Direction.EAST;
                default: return modelDirection;
            }
            */

            // 方案3: 根据facing调整
            /*
            if (modelDirection == Direction.UP) return Direction.DOWN;
            if (modelDirection == Direction.DOWN) return Direction.UP;

            switch (facing) {
                case NORTH:
                    return modelDirection;
                case SOUTH:
                    if (modelDirection.getAxis() != Direction.Axis.Y) {
                        return modelDirection.getOpposite();
                    }
                    return modelDirection;
                case EAST:
                    if (modelDirection.getAxis() == Direction.Axis.X) {
                        return modelDirection.getOpposite();
                    }
                    if (modelDirection.getAxis() == Direction.Axis.Z) {
                        return modelDirection.getOpposite();
                    }
                    return modelDirection;
                case WEST:
                    if (modelDirection == Direction.NORTH) return Direction.SOUTH;
                    if (modelDirection == Direction.SOUTH) return Direction.NORTH;
                    if (modelDirection == Direction.EAST) return Direction.WEST;
                    if (modelDirection == Direction.WEST) return Direction.EAST;
                    return modelDirection;
                default:
                    return modelDirection;
            }
            */
        }

        // ==================== 调整点 4: WALL 状态方向映射 ====================
        if (attachFace == AttachFace.WALL) {
            // 方案1: 当前的映射方案
            switch (facing) {
                case NORTH:
                    if (modelDirection == Direction.UP) return Direction.SOUTH;
                    if (modelDirection == Direction.DOWN) return Direction.NORTH;
                    if (modelDirection == Direction.NORTH) return Direction.UP;
                    if (modelDirection == Direction.SOUTH) return Direction.DOWN;
                    return modelDirection;

                case SOUTH:
                    if (modelDirection == Direction.UP) return Direction.NORTH;
                    if (modelDirection == Direction.DOWN) return Direction.SOUTH;
                    if (modelDirection == Direction.NORTH) return Direction.DOWN;
                    if (modelDirection == Direction.SOUTH) return Direction.UP;
                    return modelDirection;

                case EAST:
                    if (modelDirection == Direction.UP) return Direction.WEST;
                    if (modelDirection == Direction.DOWN) return Direction.EAST;
                    if (modelDirection == Direction.EAST) return Direction.UP;
                    if (modelDirection == Direction.WEST) return Direction.DOWN;
                    return modelDirection;

                case WEST:
                    if (modelDirection == Direction.UP) return Direction.EAST;
                    if (modelDirection == Direction.DOWN) return Direction.WEST;
                    if (modelDirection == Direction.EAST) return Direction.DOWN;
                    if (modelDirection == Direction.WEST) return Direction.UP;
                    return modelDirection;

                default:
                    return modelDirection;
            }

            // 方案2: 反向映射（如果前后颠倒）
            /*
            switch (facing) {
                case NORTH:
                    if (modelDirection == Direction.UP) return Direction.NORTH;
                    if (modelDirection == Direction.DOWN) return Direction.SOUTH;
                    if (modelDirection == Direction.NORTH) return Direction.DOWN;
                    if (modelDirection == Direction.SOUTH) return Direction.UP;
                    return modelDirection;

                case SOUTH:
                    if (modelDirection == Direction.UP) return Direction.SOUTH;
                    if (modelDirection == Direction.DOWN) return Direction.NORTH;
                    if (modelDirection == Direction.NORTH) return Direction.UP;
                    if (modelDirection == Direction.SOUTH) return Direction.DOWN;
                    return modelDirection;

                case EAST:
                    if (modelDirection == Direction.UP) return Direction.EAST;
                    if (modelDirection == Direction.DOWN) return Direction.WEST;
                    if (modelDirection == Direction.EAST) return Direction.DOWN;
                    if (modelDirection == Direction.WEST) return Direction.UP;
                    return modelDirection;

                case WEST:
                    if (modelDirection == Direction.UP) return Direction.WEST;
                    if (modelDirection == Direction.DOWN) return Direction.EAST;
                    if (modelDirection == Direction.EAST) return Direction.UP;
                    if (modelDirection == Direction.WEST) return Direction.DOWN;
                    return modelDirection;

                default:
                    return modelDirection;
            }
            */

            // 方案3: 只处理垂直轴转换
            /*
            switch (facing) {
                case NORTH:
                    if (modelDirection == Direction.UP) return Direction.SOUTH;
                    if (modelDirection == Direction.DOWN) return Direction.NORTH;
                    if (modelDirection == Direction.SOUTH) return Direction.DOWN;
                    if (modelDirection == Direction.NORTH) return Direction.UP;
                    // 保持东西方向不变
                    return modelDirection;

                case SOUTH:
                    if (modelDirection == Direction.UP) return Direction.NORTH;
                    if (modelDirection == Direction.DOWN) return Direction.SOUTH;
                    if (modelDirection == Direction.NORTH) return Direction.DOWN;
                    if (modelDirection == Direction.SOUTH) return Direction.UP;
                    // 保持东西方向不变
                    return modelDirection;

                case EAST:
                    if (modelDirection == Direction.UP) return Direction.WEST;
                    if (modelDirection == Direction.DOWN) return Direction.EAST;
                    if (modelDirection == Direction.WEST) return Direction.DOWN;
                    if (modelDirection == Direction.EAST) return Direction.UP;
                    // 保持南北方向不变
                    return modelDirection;

                case WEST:
                    if (modelDirection == Direction.UP) return Direction.EAST;
                    if (modelDirection == Direction.DOWN) return Direction.WEST;
                    if (modelDirection == Direction.EAST) return Direction.DOWN;
                    if (modelDirection == Direction.WEST) return Direction.UP;
                    // 保持南北方向不变
                    return modelDirection;

                default:
                    return modelDirection;
            }
            */

            // 方案4: 简化版本 - 只转换必要的轴
            /*
            if (facing.getAxis() == Direction.Axis.Z) { // NORTH or SOUTH
                if (modelDirection == Direction.UP) return facing.getOpposite();
                if (modelDirection == Direction.DOWN) return facing;
                if (modelDirection == facing) return Direction.DOWN;
                if (modelDirection == facing.getOpposite()) return Direction.UP;
                return modelDirection;
            } else { // EAST or WEST
                if (modelDirection == Direction.UP) return facing.getOpposite();
                if (modelDirection == Direction.DOWN) return facing;
                if (modelDirection == facing) return Direction.DOWN;
                if (modelDirection == facing.getOpposite()) return Direction.UP;
                return modelDirection;
            }
            */
        }

        return modelDirection;
    }
}