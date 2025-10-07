package com.adonis.fluid.block.SmartFluidInterface;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class SmartFluidInterfaceBlockEntity extends SmartBlockEntity {

    private LazyOptional<IFluidHandler> fluidCapability;
    protected FilteringBehaviour filtering;

    public SmartFluidInterfaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.fluidCapability = LazyOptional.of(() -> new SmartFluidInterfaceHandler());
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        filtering = new FilteringBehaviour(this, new SmartFluidInterfaceFilterSlot())
                .forFluids(); // 标记为流体过滤器
        behaviours.add(filtering);
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
        if (!(state.getBlock() instanceof SmartFluidInterfaceBlock)) return null;

        // 获取接口面向的方向的相反方向（背后的方块）
        Direction attachedDirection = state.getValue(SmartFluidInterfaceBlock.FACING).getOpposite();
        BlockPos targetPos = worldPosition.relative(attachedDirection);

        // 特殊处理含水树叶
        BlockState targetState = level.getBlockState(targetPos);
        if (targetState.is(BlockTags.LEAVES) &&
                targetState.hasProperty(BlockStateProperties.WATERLOGGED) &&
                targetState.getValue(BlockStateProperties.WATERLOGGED)) {
            return new WaterloggedBlockFluidHandler();
        }

        BlockEntity targetBlockEntity = level.getBlockEntity(targetPos);
        if (targetBlockEntity == null) return null;

        // 首先尝试从流体接口面向的方向获取流体能力
        IFluidHandler handler = targetBlockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER,
                state.getValue(SmartFluidInterfaceBlock.FACING)).orElse(null);

        // 如果没有，尝试获取默认的流体能力
        if (handler == null) {
            handler = targetBlockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
        }

        return handler;
    }

    /**
     * 智能流体接口的流体处理器实现 - 带过滤的代理到背后的容器
     */
    private class SmartFluidInterfaceHandler implements IFluidHandler {

        @Override
        public int getTanks() {
            IFluidHandler target = getTargetFluidHandler();
            return target != null ? target.getTanks() : 0;
        }

        @Nonnull
        @Override
        public FluidStack getFluidInTank(int tank) {
            IFluidHandler target = getTargetFluidHandler();
            if (target == null) return FluidStack.EMPTY;

            FluidStack fluid = target.getFluidInTank(tank);
            // 对于获取流体信息，不应用过滤器，让调用者能看到所有流体
            return fluid;
        }

        @Override
        public int getTankCapacity(int tank) {
            IFluidHandler target = getTargetFluidHandler();
            return target != null ? target.getTankCapacity(tank) : 0;
        }

        @Override
        public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
            IFluidHandler target = getTargetFluidHandler();
            if (target == null) return false;

            // 检查过滤器
            if (filtering != null && !filtering.test(stack)) {
                return false;
            }

            return target.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            IFluidHandler target = getTargetFluidHandler();
            if (target == null) return 0;

            // 填充时检查过滤器 - 只允许通过过滤器的流体填入
            if (filtering != null && !filtering.test(resource)) {
                return 0;
            }

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

            // 抽取时检查过滤器 - 只允许抽取通过过滤器的流体
            if (filtering != null && !filtering.test(resource)) {
                return FluidStack.EMPTY;
            }

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

            // 对于任意抽取，需要找到第一个通过过滤器的流体
            for (int i = 0; i < target.getTanks(); i++) {
                FluidStack fluid = target.getFluidInTank(i);
                if (!fluid.isEmpty() && (filtering == null || filtering.test(fluid))) {
                    // 创建一个抽取请求，数量为min(maxDrain, 可用数量)
                    FluidStack toDrain = fluid.copy();
                    toDrain.setAmount(Math.min(maxDrain, fluid.getAmount()));

                    FluidStack drained = target.drain(toDrain, action);
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

            return FluidStack.EMPTY;
        }
    }

    /**
     * 获取背后的目标方块实体
     */
    @Nullable
    private BlockEntity getTargetBlockEntity() {
        if (level == null) return null;

        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof SmartFluidInterfaceBlock)) return null;

        Direction attachedDirection = state.getValue(SmartFluidInterfaceBlock.FACING).getOpposite();
        BlockPos targetPos = worldPosition.relative(attachedDirection);
        return level.getBlockEntity(targetPos);
    }

    /**
     * 内部类：模拟含水方块（树叶）作为无限水源
     */
    private static class WaterloggedBlockFluidHandler implements IFluidHandler {
        private static final FluidStack WATER = new FluidStack(Fluids.WATER, 1000);

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return WATER.copy();
        }

        @Override
        public int getTankCapacity(int tank) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return false; // 不能往含水方块里填充流体
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0; // 不能往含水方块里填充流体
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.getFluid() == Fluids.WATER) {
                return new FluidStack(Fluids.WATER, Math.min(resource.getAmount(), 1000));
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return new FluidStack(Fluids.WATER, Math.min(maxDrain, 1000));
        }
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