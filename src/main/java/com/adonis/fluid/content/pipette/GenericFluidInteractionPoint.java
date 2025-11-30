package com.adonis.fluid.content.pipette;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;

/**
 * 通用流体交互点
 * 用于任何暴露了 IFluidHandler capability 的方块
 * 仅用于新增的模组兼容，不替换现有的专用交互点类
 */
public class GenericFluidInteractionPoint extends FluidInteractionPoint {

    @Nullable
    private Direction preferredFace;

    public GenericFluidInteractionPoint(Level level, BlockPos pos, BlockState state) {
        super(level, pos, state);
        this.preferredFace = findBestFace(level, pos);
    }

    /**
     * 带默认模式的构造函数
     */
    public GenericFluidInteractionPoint(Level level, BlockPos pos, BlockState state, Mode defaultMode) {
        super(level, pos, state);
        this.mode = defaultMode;
        this.preferredFace = findBestFace(level, pos);
    }

    /**
     * 查找最佳的流体访问面
     * 注意：某些模组的方块不支持 null 方向，需要安全处理
     */
    @Nullable
    private Direction findBestFace(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return Direction.UP; // 默认返回UP而不是null

        // 优先尝试具体方向，避免 null 导致某些模组崩溃
        // 按优先级尝试：UP > DOWN > 四个水平方向
        Direction[] priorities = {
                Direction.UP,
                Direction.DOWN,
                Direction.NORTH,
                Direction.SOUTH,
                Direction.EAST,
                Direction.WEST
        };

        for (Direction dir : priorities) {
            if (hasFluidCapabilitySafe(be, dir)) {
                return dir;
            }
        }

        // 最后才尝试 null（内部存储），用 try-catch 保护
        if (hasFluidCapabilitySafe(be, null)) {
            return null;
        }

        // 如果都没有，返回 UP 作为默认值
        return Direction.UP;
    }

    /**
     * 安全地检查是否有流体能力
     * 某些模组的 getCapability 实现不支持 null 方向，会抛出 NPE
     */
    private boolean hasFluidCapabilitySafe(BlockEntity be, @Nullable Direction dir) {
        try {
            return be.getCapability(ForgeCapabilities.FLUID_HANDLER, dir).isPresent();
        } catch (NullPointerException e) {
            // 某些模组（如 Create Diesel Generators）不支持 null 方向
            return false;
        } catch (Exception e) {
            // 捕获其他可能的异常
            return false;
        }
    }

    @Nullable
    private IFluidHandler getFluidHandler() {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;

        try {
            return be.getCapability(ForgeCapabilities.FLUID_HANDLER, preferredFace).orElse(null);
        } catch (NullPointerException e) {
            // 如果首选方向失败，尝试其他方向
            for (Direction dir : Direction.values()) {
                try {
                    IFluidHandler handler = be.getCapability(ForgeCapabilities.FLUID_HANDLER, dir).orElse(null);
                    if (handler != null) {
                        this.preferredFace = dir; // 更新首选方向
                        return handler;
                    }
                } catch (Exception ignored) {
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isValid() {
        if (level == null) return false;

        long gameTime = level.getGameTime();
        if (gameTime == lastKnownValid) return true;

        IFluidHandler handler = getFluidHandler();
        if (handler != null) {
            lastKnownValid = gameTime;
            return true;
        }

        return false;
    }

    @Override
    public FluidStack extract(int maxAmount, boolean simulate) {
        IFluidHandler handler = getFluidHandler();
        if (handler == null) return FluidStack.EMPTY;

        try {
            return handler.drain(maxAmount, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        } catch (Exception e) {
            return FluidStack.EMPTY;
        }
    }

    @Override
    public FluidStack insert(FluidStack stack, boolean simulate) {
        IFluidHandler handler = getFluidHandler();
        if (handler == null) return stack;

        try {
            int filled = handler.fill(stack, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            FluidStack remainder = stack.copy();
            remainder.shrink(filled);
            return remainder;
        } catch (Exception e) {
            return stack;
        }
    }

    @Override
    public boolean canExtract() {
        IFluidHandler handler = getFluidHandler();
        if (handler == null) return false;

        try {
            return !handler.drain(1, IFluidHandler.FluidAction.SIMULATE).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean canInsert(FluidStack stack) {
        IFluidHandler handler = getFluidHandler();
        if (handler == null) return false;

        try {
            return handler.fill(stack, IFluidHandler.FluidAction.SIMULATE) > 0;
        } catch (Exception e) {
            return false;
        }
    }
}