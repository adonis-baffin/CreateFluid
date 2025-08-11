package com.adonis.fluid.block.FluidInterface;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class FluidInterfaceBlockEntity extends SmartBlockEntity {

    private LazyOptional<IFluidHandler> fluidCapability;

    public FluidInterfaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.fluidCapability = LazyOptional.of(() -> new FluidInterfaceHandler());
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // 流体接口不需要额外的行为
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCapability.invalidate();
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    /**
     * 获取背后依附的容器的流体处理器
     */
    @Nullable
    private IFluidHandler getTargetFluidHandler() {
        if (level == null) return null;

        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof FluidInterfaceBlock)) return null;

        // 获取接口面向的方向的相反方向（背后的方块）
        Direction attachedDirection = state.getValue(FluidInterfaceBlock.FACING).getOpposite();
        BlockPos targetPos = worldPosition.relative(attachedDirection);
        BlockEntity targetBlockEntity = level.getBlockEntity(targetPos);

        if (targetBlockEntity == null) return null;

        // 首先尝试从流体接口面向的方向获取流体能力
        IFluidHandler handler = targetBlockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER,
                state.getValue(FluidInterfaceBlock.FACING)).orElse(null);

        // 如果没有，尝试获取默认的流体能力
        if (handler == null) {
            handler = targetBlockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
        }

        return handler;
    }

    /**
     * 流体接口的流体处理器实现 - 代理到背后的容器
     */
    private class FluidInterfaceHandler implements IFluidHandler {

        @Override
        public int getTanks() {
            IFluidHandler target = getTargetFluidHandler();
            return target != null ? target.getTanks() : 0;
        }

        @Nonnull
        @Override
        public FluidStack getFluidInTank(int tank) {
            IFluidHandler target = getTargetFluidHandler();
            return target != null ? target.getFluidInTank(tank) : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            IFluidHandler target = getTargetFluidHandler();
            return target != null ? target.getTankCapacity(tank) : 0;
        }

        @Override
        public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
            IFluidHandler target = getTargetFluidHandler();
            return target != null && target.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            IFluidHandler target = getTargetFluidHandler();
            if (target == null) return 0;

            int filled = target.fill(resource, action);
            if (filled > 0 && action.execute()) {
                // 标记目标方块实体已更改
                BlockEntity targetBE = getTargetBlockEntity();
                if (targetBE != null) {
                    targetBE.setChanged();
                }
                // 标记自己也已更改以触发更新
                setChanged();
            }
            return filled;
        }

        @Nonnull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            IFluidHandler target = getTargetFluidHandler();
            if (target == null) return FluidStack.EMPTY;

            FluidStack drained = target.drain(resource, action);
            if (!drained.isEmpty() && action.execute()) {
                // 标记目标方块实体已更改
                BlockEntity targetBE = getTargetBlockEntity();
                if (targetBE != null) {
                    targetBE.setChanged();
                }
                // 标记自己也已更改以触发更新
                setChanged();
            }
            return drained;
        }

        @Nonnull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            IFluidHandler target = getTargetFluidHandler();
            if (target == null) return FluidStack.EMPTY;

            FluidStack drained = target.drain(maxDrain, action);
            if (!drained.isEmpty() && action.execute()) {
                // 标记目标方块实体已更改
                BlockEntity targetBE = getTargetBlockEntity();
                if (targetBE != null) {
                    targetBE.setChanged();
                }
                // 标记自己也已更改以触发更新
                setChanged();
            }
            return drained;
        }
    }

    /**
     * 获取背后的目标方块实体
     */
    @Nullable
    private BlockEntity getTargetBlockEntity() {
        if (level == null) return null;

        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof FluidInterfaceBlock)) return null;

        Direction attachedDirection = state.getValue(FluidInterfaceBlock.FACING).getOpposite();
        BlockPos targetPos = worldPosition.relative(attachedDirection);
        return level.getBlockEntity(targetPos);
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
    }
}