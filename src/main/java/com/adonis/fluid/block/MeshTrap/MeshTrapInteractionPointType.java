package com.adonis.fluid.block.MeshTrap;

import com.adonis.fluid.registry.CFBlock;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

public class MeshTrapInteractionPointType extends ArmInteractionPointType {

    public MeshTrapInteractionPointType() {
    }

    @Override
    public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
        return CFBlock.MESH_TRAP.has(state);
    }

    @Override
    public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
        return new MeshTrapInteractionPoint(this, level, pos, state);
    }
}

class MeshTrapInteractionPoint extends ArmInteractionPoint {

    public MeshTrapInteractionPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
        super(type, level, pos, state);
    }

    @Override
    protected Vec3 getInteractionPositionVector() {
        return Vec3.atLowerCornerOf(this.pos).add(0.5, 14 / 16.0, 0.5);
    }

    @Override
    protected IItemHandler getHandler() {
        if (level.getBlockEntity(pos) instanceof MeshTrapBlockEntity meshTrap) {
            return meshTrap.getInventory();
        }
        return null;
    }

    @Override
    public ItemStack insert(ItemStack stack, boolean simulate) {
        IItemHandler handler = getHandler();
        if (handler == null) {
            return stack;
        }
        return ItemHandlerHelper.insertItem(handler, stack, simulate);
    }

    @Override
    public ItemStack extract(int slot, int amount, boolean simulate) {
        IItemHandler handler = getHandler();
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        return handler.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotCount() {
        IItemHandler handler = getHandler();
        return handler != null ? handler.getSlots() : 0;
    }
}