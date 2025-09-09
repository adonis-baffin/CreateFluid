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

    @Nullable
    private TransportedItemStack findItemForFilling(FluidStack fluid) {
        BeltBlockEntity controller = BeltHelper.getControllerBE(level, pos);
        if (controller == null) return null;

        BeltInventory inventory = controller.getInventory();
        if (inventory == null) return null;

        BeltBlockEntity segment = BeltHelper.getSegmentBE(level, pos);
        if (segment == null) return null;

        int segmentIndex = segment.index;
        List<TransportedItemStack> items = inventory.getTransportedItems();

        for (TransportedItemStack transported : items) {
            float itemPos = transported.beltPosition;

            // 检查物品是否在当前段附近
            if (Math.abs(itemPos - segmentIndex - 0.5f) < 0.75f) {
                // 跳过已锁定的物品
                if (transported.locked || transported.lockedExternally) {
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

    public boolean hasItemForFilling(FluidStack fluid) {
        return findItemForFilling(fluid) != null;
    }

    public boolean lockItemForProcessing(FluidStack fluid) {
        if (lockedItem != null) return false;

        TransportedItemStack item = findItemForFilling(fluid);
        if (item == null) return false;

        // 使用 locked 而不是 lockedExternally 来停止物品
        item.locked = true;
        lockedItem = item;

        // 立即通知传送带更新
        BeltBlockEntity controller = BeltHelper.getControllerBE(level, pos);
        if (controller != null) {
            controller.setChanged();
            controller.sendData();
        }

        return true;
    }

    /**
     * 执行注液处理（不消耗流体，只返回结果）
     */
    public ItemStack processLockedItem(FluidStack fluid) {
        if (lockedItem == null) return ItemStack.EMPTY;

        ItemStack original = lockedItem.stack.copy();
        int required = FillingBySpout.getRequiredAmountForItem(
                level, original, fluid);

        if (required <= 0 || required > fluid.getAmount()) {
            unlockItem();
            return ItemStack.EMPTY;
        }

        // 创建用于注液的流体副本
        FluidStack fluidForFilling = fluid.copy();
        fluidForFilling.setAmount(required);

        // 执行注液
        ItemStack result = FillingBySpout.fillItem(
                level, required, original, fluidForFilling);

        if (!result.isEmpty()) {
            // 更新传送带上的物品
            lockedItem.stack = result;

            // 清除风扇处理数据
            lockedItem.clearFanProcessingData();

            // 注意：不在这里消耗流体，让调用者处理

            // 通知传送带更新
            BeltBlockEntity controller = BeltHelper.getControllerBE(level, pos);
            if (controller != null) {
                controller.setChanged();
                controller.sendData();
            }
        }

        // 解锁物品
        unlockItem();
        return result;
    }

    /**
     * 获取所需的流体量
     */
    public int getRequiredAmount(FluidStack fluid) {
        if (lockedItem == null) return 0;
        return FillingBySpout.getRequiredAmountForItem(level, lockedItem.stack, fluid);
    }

    public ItemStack getLockedItem() {
        return lockedItem != null ? lockedItem.stack.copy() : ItemStack.EMPTY;
    }

    public void unlockItem() {
        if (lockedItem != null) {
            // 解锁物品，让它继续移动
            lockedItem.locked = false;

            // 通知传送带更新
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
        // 传送带只能作为输出端
        return;
    }
}