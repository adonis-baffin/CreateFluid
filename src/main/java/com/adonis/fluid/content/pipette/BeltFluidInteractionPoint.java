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
        // 传送带默认作为输出端（接收流体进行加工）
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

        // 确保传送带能传输物品
        if (!com.simibubi.create.content.kinetics.belt.BeltBlock.canTransportObjects(currentState)) {
            return false;
        }

        BeltBlockEntity belt = BeltHelper.getControllerBE(level, pos);
        return belt != null;
    }

    /**
     * 查找传送带上需要注液的物品
     */
    @Nullable
    private TransportedItemStack findItemForFilling(FluidStack fluid) {
        BeltBlockEntity controller = BeltHelper.getControllerBE(level, pos);
        if (controller == null) return null;

        BeltInventory inventory = controller.getInventory();
        if (inventory == null) return null;

        // 获取当前段的信息
        BeltBlockEntity segment = BeltHelper.getSegmentBE(level, pos);
        if (segment == null) return null;

        int segmentIndex = segment.index;

        // 获取传送带上的所有物品
        List<TransportedItemStack> items = inventory.getTransportedItems();

        // 检查当前段附近的物品
        for (TransportedItemStack transported : items) {
            float itemPos = transported.beltPosition;

            // 检查物品是否在当前段附近（±0.75的范围）
            if (Math.abs(itemPos - segmentIndex - 0.5f) < 0.75f) {
                // 检查物品是否已被锁定
                if (transported.locked || transported.lockedExternally) {
                    continue;
                }

                // 检查物品是否可以被注液
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

    /**
     * 检查是否有物品可以进行注液加工
     */
    public boolean hasItemForFilling(FluidStack fluid) {
        return findItemForFilling(fluid) != null;
    }

    /**
     * 锁定物品准备处理
     */
    public boolean lockItemForProcessing(FluidStack fluid) {
        if (lockedItem != null) return false;

        TransportedItemStack item = findItemForFilling(fluid);
        if (item == null) return false;

        // 锁定物品防止移动
        item.lockedExternally = true;
        lockedItem = item;

        return true;
    }

    /**
     * 执行注液处理
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

        // 执行注液
        ItemStack result = FillingBySpout.fillItem(
                level, required, original, fluid);

        if (!result.isEmpty()) {
            // 更新传送带上的物品
            lockedItem.stack = result;

            // 清除风扇处理数据（如果有）
            lockedItem.clearFanProcessingData();

            // 减少流体
            fluid.shrink(required);

            // 通知传送带更新
            BeltBlockEntity controller = BeltHelper.getControllerBE(level, pos);
            if (controller != null) {
                controller.setChanged();
                controller.sendData();
            }
        }

        unlockItem();
        return result;
    }

    /**
     * 获取锁定的物品
     */
    public ItemStack getLockedItem() {
        return lockedItem != null ? lockedItem.stack.copy() : ItemStack.EMPTY;
    }

    /**
     * 解锁物品
     */
    public void unlockItem() {
        if (lockedItem != null) {
            lockedItem.lockedExternally = false;
            lockedItem = null;
        }
    }

    @Override
    public FluidStack extract(int maxAmount, boolean simulate) {
        // 传送带不提供流体
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack insert(FluidStack stack, boolean simulate) {
        // 传送带的注液通过processLockedItem处理
        return stack;
    }

    @Override
    public boolean canExtract() {
        // 传送带不能抽取流体
        return false;
    }

    @Override
    public boolean canInsert(FluidStack stack) {
        // 检查是否有物品可以接受这种流体
        return hasItemForFilling(stack);
    }

    @Override
    public void cycleMode() {
        // 传送带只能作为输出端，不允许切换模式
        return;
    }
}