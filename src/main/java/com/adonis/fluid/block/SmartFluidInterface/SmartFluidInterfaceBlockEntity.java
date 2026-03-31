package com.adonis.fluid.block.SmartFluidInterface;

import com.adonis.fluid.registry.CFBlockEntities;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;
import java.util.List;

public class SmartFluidInterfaceBlockEntity extends SmartBlockEntity {

    public FilteringBehaviour filtering;

    public SmartFluidInterfaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        filtering = new FilteringBehaviour(this, new SmartFluidInterfaceFilterSlot())
                .forFluids();
        behaviours.add(filtering);
    }

    /**
     * 检查是否是铜格栅（所有变种）
     */
    private boolean isCopperGrate(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.COPPER_GRATE ||
                block == Blocks.EXPOSED_COPPER_GRATE ||
                block == Blocks.WEATHERED_COPPER_GRATE ||
                block == Blocks.OXIDIZED_COPPER_GRATE ||
                block == Blocks.WAXED_COPPER_GRATE ||
                block == Blocks.WAXED_EXPOSED_COPPER_GRATE ||
                block == Blocks.WAXED_WEATHERED_COPPER_GRATE ||
                block == Blocks.WAXED_OXIDIZED_COPPER_GRATE;
    }

    @Nullable
    public IFluidHandler getTargetFluidHandler() {
        if (level == null) return null;

        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof SmartFluidInterfaceBlock)) return null;

        Direction attachedDirection = state.getValue(SmartFluidInterfaceBlock.FACING).getOpposite();
        BlockPos targetPos = worldPosition.relative(attachedDirection);

        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos,
                state.getValue(SmartFluidInterfaceBlock.FACING));

        if (handler == null) {
            handler = level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, null);
        }

        return handler;
    }

    @Nullable
    public BlockEntity getTargetBlockEntity() {
        if (level == null) return null;

        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof SmartFluidInterfaceBlock)) return null;

        Direction attachedDirection = state.getValue(SmartFluidInterfaceBlock.FACING).getOpposite();
        BlockPos targetPos = worldPosition.relative(attachedDirection);
        return level.getBlockEntity(targetPos);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                CFBlockEntities.SMART_FLUID_INTERFACE.get(),
                (be, side) -> {
                    IFluidHandler targetHandler = be.getTargetFluidHandler();

                    // 特殊处理含水树叶和含水铜格栅
                    if (targetHandler == null) {
                        BlockState state = be.getBlockState();
                        Direction attachedDirection = state.getValue(SmartFluidInterfaceBlock.FACING).getOpposite();
                        BlockPos targetPos = be.worldPosition.relative(attachedDirection);
                        BlockState targetState = be.level.getBlockState(targetPos);

                        if ((targetState.is(BlockTags.LEAVES) || be.isCopperGrate(targetState)) &&
                                targetState.hasProperty(BlockStateProperties.WATERLOGGED) &&
                                targetState.getValue(BlockStateProperties.WATERLOGGED)) {
                            targetHandler = new WaterloggedBlockFluidHandler();
                        }
                    }

                    if (targetHandler != null && be.filtering != null) {
                        // 返回一个过滤的流体处理器包装器
                        return new FilteredFluidHandler(targetHandler, be.filtering);
                    }
                    return targetHandler;
                }
        );
    }

    // 添加过滤流体处理器包装类
    private static class FilteredFluidHandler implements IFluidHandler {
        private final IFluidHandler wrapped;
        private final FilteringBehaviour filter;

        public FilteredFluidHandler(IFluidHandler wrapped, FilteringBehaviour filter) {
            this.wrapped = wrapped;
            this.filter = filter;
        }

        @Override
        public int getTanks() {
            return wrapped.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            FluidStack stack = wrapped.getFluidInTank(tank);
            return filter.test(stack) ? stack : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return wrapped.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return filter.test(stack) && wrapped.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!filter.test(resource)) return 0;
            return wrapped.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (!filter.test(resource)) return FluidStack.EMPTY;
            return wrapped.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            FluidStack result = wrapped.drain(maxDrain, FluidAction.SIMULATE);
            if (!filter.test(result)) return FluidStack.EMPTY;
            return action.execute() ? wrapped.drain(maxDrain, action) : result;
        }
    }

    /**
     * 内部类：模拟含水方块（树叶/铜格栅）作为无限水源
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
}