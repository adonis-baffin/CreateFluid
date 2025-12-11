package com.adonis.fluid.content.pipette;

import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

public class DepotFluidInteractionPoint extends FluidInteractionPoint {

    public DepotFluidInteractionPoint(Level level, BlockPos pos, BlockState state) {
        super(level, pos, state);
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

        boolean canBeFilled = com.simibubi.create.content.fluids.spout.FillingBySpout
                .canItemBeFilled(level, heldItem);

        return canBeFilled;
    }

    @Nullable
    public ItemStack getHeldItem() {
        DepotBehaviour behaviour = getDepotBehaviour();
        if (behaviour == null) return null;
        return behaviour.getHeldItemStack().copy();
    }

    public void setFilledItem(ItemStack filledItem) {
        DepotBehaviour behaviour = getDepotBehaviour();
        if (behaviour == null) return;

        com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack newStack =
                new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(filledItem);

        newStack.beltPosition = 0.5f;
        newStack.prevBeltPosition = 0.5f;

        behaviour.setHeldItem(newStack);
        behaviour.blockEntity.notifyUpdate();
    }

    @Override
    public FluidStack extract(int maxAmount, boolean simulate) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack insert(FluidStack stack, boolean simulate) {
        return stack;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canInsert(FluidStack stack) {
        if (!hasItemForFilling()) return false;

        ItemStack heldItem = getHeldItem();
        if (heldItem == null || heldItem.isEmpty()) return false;

        int required = com.simibubi.create.content.fluids.spout.FillingBySpout
                .getRequiredAmountForItem(level, heldItem, stack);

        return required > 0 && required <= stack.getAmount();
    }

    public ItemStack getItemForFilling() {
        DepotBehaviour behaviour = BlockEntityBehaviour.get(level, pos, DepotBehaviour.TYPE);
        if (behaviour == null) return ItemStack.EMPTY;

        ItemStack heldItem = behaviour.getHeldItemStack();
        if (heldItem != null && !heldItem.isEmpty()) {
            if (FillingBySpout.canItemBeFilled(level, heldItem)) {
                return heldItem.copy();
            }
        }
        return ItemStack.EMPTY;
    }
}