package com.adonis.fluid.content.pipette;

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
        // 置物台只能作为输出端（DEPOSIT = 输出，接收流体进行加工）
        this.mode = Mode.DEPOSIT;
    }

    @Override
    public boolean isValid() {
        if (level == null || !level.isLoaded(pos)) {
            System.out.println("DepotPoint invalid: level or chunk not loaded");
            return false;
        }

        // 检查是否仍然是置物台
        BlockState currentState = level.getBlockState(pos);
        if (!com.simibubi.create.AllBlocks.DEPOT.has(currentState)) {
            System.out.println("DepotPoint invalid: not a depot block");
            return false;
        }

        // 检查是否有 DepotBehaviour
        DepotBehaviour behaviour = BlockEntityBehaviour.get(level, pos, DepotBehaviour.TYPE);
        boolean hasBehaviour = behaviour != null;
        if (!hasBehaviour) {
            System.out.println("DepotPoint invalid: no DepotBehaviour");
        }
        return hasBehaviour;
    }

    @Override
    public void cycleMode() {
        // 置物台不允许切换模式，始终保持为输出端
        return;
    }

    /**
     * 获取 DepotBehaviour
     */
    @Nullable
    private DepotBehaviour getDepotBehaviour() {
        return BlockEntityBehaviour.get(level, pos, DepotBehaviour.TYPE);
    }

    /**
     * 检查置物台是否有物品可以进行注液加工
     */
    public boolean hasItemForFilling() {
        // 移除mode检查，因为置物台总是DEPOSIT模式

        DepotBehaviour behaviour = getDepotBehaviour();
        if (behaviour == null) {
            System.out.println("hasItemForFilling: no behaviour");
            return false;
        }

        ItemStack heldItem = behaviour.getHeldItemStack();
        if (heldItem.isEmpty()) {
            System.out.println("hasItemForFilling: empty item");
            return false;
        }

        // 检查该物品是否可以被填充
        boolean canBeFilled = com.simibubi.create.content.fluids.spout.FillingBySpout
                .canItemBeFilled(level, heldItem);

        System.out.println("hasItemForFilling: " + heldItem + " can be filled? " + canBeFilled);
        return canBeFilled;
    }

    /**
     * 获取置物台上的物品
     */
    @Nullable
    public ItemStack getHeldItem() {
        DepotBehaviour behaviour = getDepotBehaviour();
        if (behaviour == null) return null;
        return behaviour.getHeldItemStack().copy();
    }

    /**
     * 设置置物台上的物品（注液后的结果）
     */
    public void setFilledItem(ItemStack filledItem) {
        DepotBehaviour behaviour = getDepotBehaviour();
        if (behaviour == null) return;

        // 直接使用公开的方法
        com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack newStack =
                new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(filledItem);

        // 设置位置
        newStack.beltPosition = 0.5f;
        newStack.prevBeltPosition = 0.5f;

        // 使用公开的setHeldItem方法
        behaviour.setHeldItem(newStack);
        behaviour.blockEntity.notifyUpdate();
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
        // 只有在作为输出端且有可填充物品时才能接受流体
        if (!hasItemForFilling()) return false;

        ItemStack heldItem = getHeldItem();
        if (heldItem == null || heldItem.isEmpty()) return false;

        // 检查流体是否可以用于填充该物品
        int required = com.simibubi.create.content.fluids.spout.FillingBySpout
                .getRequiredAmountForItem(level, heldItem, stack);

        return required > 0 && required <= stack.getAmount();
    }
}