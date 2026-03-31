package com.adonis.fluid.content.pipette;

import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

import javax.annotation.Nullable;

public class DepotFluidInteractionPoint extends FluidInteractionPoint {

    public DepotFluidInteractionPoint(Level level, BlockPos pos, BlockState state) {
        super(level, pos, state);
        // 置物台只能作为输出端（接收流体进行加工）
        this.mode = Mode.DEPOSIT;
    }

    @Override
    public boolean isValid() {
        if (level == null || !level.isLoaded(pos)) {
            return false;
        }

        BlockState currentState = level.getBlockState(pos);
        if (!com.simibubi.create.AllBlocks.DEPOT.has(currentState) &&
                !com.simibubi.create.AllBlocks.WEIGHTED_EJECTOR.has(currentState)) {
            return false;
        }

        DepotBehaviour behaviour = BlockEntityBehaviour.get(level, pos, DepotBehaviour.TYPE);
        return behaviour != null;
    }

    @Override
    public void cycleMode() {
        // 置物台不允许切换模式，始终保持为输出端
        return;
    }

    @Nullable
    private DepotBehaviour getDepotBehaviour() {
        return BlockEntityBehaviour.get(level, pos, DepotBehaviour.TYPE);
    }

    public boolean hasItemForFilling() {
        DepotBehaviour behaviour = getDepotBehaviour();
        if (behaviour == null) {
            return false;
        }

        ItemStack heldItem = behaviour.getHeldItemStack();
        if (heldItem.isEmpty()) {
            return false;
        }

        // 检查该物品是否可以被填充
        return com.simibubi.create.content.fluids.spout.FillingBySpout
                .canItemBeFilled(level, heldItem);
    }

    @Nullable
    public ItemStack getHeldItem() {
        DepotBehaviour behaviour = getDepotBehaviour();
        if (behaviour == null) return null;
        return behaviour.getHeldItemStack().copy();
    }

    @Override
    public FluidStack extract(int maxAmount, boolean simulate) {
        // 置物台不提供流体
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack insert(FluidStack stack, boolean simulate) {
        // 置物台不直接接受流体，只能通过注液加工
        return stack;
    }

    @Override
    public boolean canExtract() {
        // 置物台不能抽取流体
        return false;
    }

    @Override
    public boolean canInsert(FluidStack stack) {
        if (!hasItemForFilling()) return false;

        ItemStack heldItem = getHeldItem();
        if (heldItem == null || heldItem.isEmpty()) return false;

        // 检查流体是否可以用于填充该物品
        int required = com.simibubi.create.content.fluids.spout.FillingBySpout
                .getRequiredAmountForItem(level, heldItem, stack);

        return required > 0 && required <= stack.getAmount();
    }
}