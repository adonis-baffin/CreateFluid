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
     */
    @Nullable
    private Direction findBestFace(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        
        // 优先尝试上方
        if (be.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).isPresent()) {
            return Direction.UP;
        }
        
        // 然后尝试null（内部存储）
        if (be.getCapability(ForgeCapabilities.FLUID_HANDLER, null).isPresent()) {
            return null;
        }
        
        // 最后尝试其他方向
        for (Direction dir : Direction.values()) {
            if (be.getCapability(ForgeCapabilities.FLUID_HANDLER, dir).isPresent()) {
                return dir;
            }
        }
        
        return null;
    }
    
    @Nullable
    private IFluidHandler getFluidHandler() {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        
        return be.getCapability(ForgeCapabilities.FLUID_HANDLER, preferredFace).orElse(null);
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
        
        return handler.drain(maxAmount, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
    }
    
    @Override
    public FluidStack insert(FluidStack stack, boolean simulate) {
        IFluidHandler handler = getFluidHandler();
        if (handler == null) return stack;
        
        int filled = handler.fill(stack, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        FluidStack remainder = stack.copy();
        remainder.shrink(filled);
        return remainder;
    }
    
    @Override
    public boolean canExtract() {
        IFluidHandler handler = getFluidHandler();
        if (handler == null) return false;
        
        return !handler.drain(1, IFluidHandler.FluidAction.SIMULATE).isEmpty();
    }
    
    @Override
    public boolean canInsert(FluidStack stack) {
        IFluidHandler handler = getFluidHandler();
        if (handler == null) return false;
        
        return handler.fill(stack, IFluidHandler.FluidAction.SIMULATE) > 0;
    }
}