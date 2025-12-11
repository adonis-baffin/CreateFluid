package com.adonis.fluid.content.pipette;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.List;

public class BeltFluidInteractionPoint extends FluidInteractionPoint {
    private TransportedItemStack lockedItem = null;

    public BeltFluidInteractionPoint(Level level, BlockPos pos, BlockState state) {
        super(level, pos, state);
        this.mode = Mode.DEPOSIT;
    }

    @Override
    public boolean isValid() {
        if (level == null || !level.isLoaded(pos)) {
            return false;
        }

        BlockState currentState = level.getBlockState(pos);
        if (!com.simibubi.create.AllBlocks.BELT.has(currentState)) {
            return false;
        }

        if (!com.simibubi.create.content.kinetics.belt.BeltBlock.canTransportObjects(currentState)) {
            return false;
        }

        BeltBlockEntity belt = BeltHelper.getControllerBE(level, pos);
        return belt != null;
    }

    public boolean hasItemForFilling(FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) {
            return false;
        }
        return findItemForFilling(fluid) != null;
    }

    @Nullable
    private TransportedItemStack findItemForFilling(FluidStack fluid) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }

        BeltBlockEntity controller = BeltHelper.getControllerBE(level, pos);
        if (controller == null) return null;

        BeltInventory inventory = controller.getInventory();
        if (inventory == null) return null;

        BeltBlockEntity segment = BeltHelper.getSegmentBE(level, pos);
        if (segment == null) return null;

        int segmentIndex = segment.index;
        List<TransportedItemStack> items = inventory.getTransportedItems();

        if (items == null || items.isEmpty()) {
            return null;
        }

        for (TransportedItemStack transported : items) {
            float itemPos = transported.beltPosition;

            if (Math.abs(itemPos - segmentIndex - 0.5f) < 0.75f) {
                if (transported.locked || transported.lockedExternally) {
                    continue;
                }

                if (transported.stack == null || transported.stack.isEmpty()) {
                    continue;
                }

                if (FillingBySpout.canItemBeFilled(level, transported.stack)) {
                    int required = FillingBySpout.getRequiredAmountForItem(
                            level, transported.stack, fluid);

                    if (required > 0 && required <= fluid.getAmount()) {
                        return transported;
                    }
                }
            }
        }

        return null;
    }

    public ItemStack processLockedItem(FluidStack fluid) {
        if (lockedItem == null) return ItemStack.EMPTY;

        ItemStack original = lockedItem.stack.copy();
        int required = FillingBySpout.getRequiredAmountForItem(
                level, original, fluid);

        if (required <= 0 || required > fluid.getAmount()) {
            unlockItem();
            return ItemStack.EMPTY;
        }

        FluidStack fluidForFilling = fluid.copy();
        fluidForFilling.setAmount(required);

        ItemStack result = FillingBySpout.fillItem(
                level, required, original, fluidForFilling);

        if (!result.isEmpty()) {
            lockedItem.stack = result;

            lockedItem.clearFanProcessingData();
            BeltBlockEntity controller = BeltHelper.getControllerBE(level, pos);
            if (controller != null) {
                controller.setChanged();
                controller.sendData();
            }
        }
        unlockItem();
        return result;
    }

    public int getRequiredAmount(FluidStack fluid) {
        if (lockedItem == null) return 0;
        return FillingBySpout.getRequiredAmountForItem(level, lockedItem.stack, fluid);
    }

    public ItemStack getLockedItem() {
        return lockedItem != null ? lockedItem.stack.copy() : ItemStack.EMPTY;
    }

    public void unlockItem() {
        if (lockedItem != null) {
            lockedItem.locked = false;
            BeltBlockEntity controller = BeltHelper.getControllerBE(level, pos);
            if (controller != null) {
                controller.setChanged();
                controller.sendData();
            }

            lockedItem = null;
        }
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
        return hasItemForFilling(stack);
    }

    @Override
    public void cycleMode() {
        return;
    }
}