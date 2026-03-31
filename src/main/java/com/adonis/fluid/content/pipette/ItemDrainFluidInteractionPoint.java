package com.adonis.fluid.content.pipette;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class ItemDrainFluidInteractionPoint extends FluidInteractionPoint {

    public ItemDrainFluidInteractionPoint(Level level, BlockPos pos, BlockState state) {
        super(level, pos, state);
        // 分液池只能作为输入端（抽取流体）
        this.mode = Mode.TAKE;
        this.face = Direction.DOWN; // 分液池必须使用DOWN方向访问流体
    }

    @Override
    public void cycleMode() {
        // 分液池只能作为输入端，不允许切换模式
        return;
    }

    @Override
    public boolean isValid() {
        if (level == null) return false;

        long gameTime = level.getGameTime();
        if (gameTime == lastKnownValid) return true;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ItemDrainBlockEntity)) return false;

        // 只要是分液池就是有效的
        lastKnownValid = gameTime;
        return true;
    }

    @Override
    public FluidStack extract(int maxAmount, boolean simulate) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ItemDrainBlockEntity)) return FluidStack.EMPTY;

        // 分液池必须使用Direction.DOWN访问流体能力
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.DOWN);
        if (handler == null) return FluidStack.EMPTY;
        
        return handler.drain(maxAmount, simulate ? 
                IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
    }

    @Override
    public FluidStack insert(FluidStack stack, boolean simulate) {
        // 分液池不接受流体输入
        return stack;
    }

    @Override
    public boolean canExtract() {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ItemDrainBlockEntity)) return false;

        // 检查是否有流体可以抽取
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.DOWN);
        if (handler == null) return false;
        
        return !handler.getFluidInTank(0).isEmpty();
    }

    @Override
    public boolean canInsert(FluidStack stack) {
        // 分液池不接受流体输入
        return false;
    }
}