package com.adonis.fluid.block.FluidInterface;

import com.adonis.fluid.registry.CFBlockEntities;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
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

public class FluidInterfaceBlockEntity extends SmartBlockEntity {

    public FluidInterfaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // 流体接口不需要额外的行为
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

    /**
     * 获取背后依附的容器的流体处理器
     */
    @Nullable
    public IFluidHandler getTargetFluidHandler() {
        if (level == null) return null;

        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof FluidInterfaceBlock)) return null;

        Direction attachedDirection = state.getValue(FluidInterfaceBlock.FACING).getOpposite();
        BlockPos targetPos = worldPosition.relative(attachedDirection);

        // 首先尝试从流体接口面向的方向获取
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos,
                state.getValue(FluidInterfaceBlock.FACING));

        // 如果没有，尝试获取默认的
        if (handler == null) {
            handler = level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, null);
        }

        return handler;
    }

    /**
     * 获取背后的目标方块实体
     */
    @Nullable
    public BlockEntity getTargetBlockEntity() {
        if (level == null) return null;

        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof FluidInterfaceBlock)) return null;

        Direction attachedDirection = state.getValue(FluidInterfaceBlock.FACING).getOpposite();
        BlockPos targetPos = worldPosition.relative(attachedDirection);
        return level.getBlockEntity(targetPos);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        // 在这里添加你的自定义 NBT 保存逻辑
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        // 在这里添加你的自定义 NBT 读取逻辑
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                CFBlockEntities.FLUID_INTERFACE.get(),
                (be, side) -> {
                    IFluidHandler targetHandler = be.getTargetFluidHandler();
                    if (targetHandler != null) {
                        return targetHandler;
                    }

                    // 特殊处理含水树叶和含水铜格栅
                    BlockState state = be.getBlockState();
                    Direction attachedDirection = state.getValue(FluidInterfaceBlock.FACING).getOpposite();
                    BlockPos targetPos = be.worldPosition.relative(attachedDirection);
                    BlockState targetState = be.level.getBlockState(targetPos);

                    if ((targetState.is(BlockTags.LEAVES) || be.isCopperGrate(targetState)) &&
                            targetState.hasProperty(BlockStateProperties.WATERLOGGED) &&
                            targetState.getValue(BlockStateProperties.WATERLOGGED)) {
                        return new WaterloggedBlockFluidHandler();
                    }

                    return null;
                }
        );
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