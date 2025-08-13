package com.adonis.fluid.content.aqueduct;

import com.adonis.fluid.block.aqueduct.AbstractAqueductBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;

public class AqueductNode {
    
    private final BlockPos pos;
    private final NodeType type;
    private final Level level;
    
    private boolean locked = false;
    private boolean hasFluidSource = false;
    private boolean isInputSource = false;
    private boolean isOutputSource = false;
    
    public AqueductNode(Level level, BlockPos pos, NodeType type) {
        this.level = level;
        this.pos = pos;
        this.type = type;
        updateState();
    }

    public void updateState() {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof AbstractAqueductBlockEntity) {
            AbstractAqueductBlockEntity aqueduct = (AbstractAqueductBlockEntity) be;
            this.locked = aqueduct.isLocked();
            
            // 检查是否连接了流体管道或其他流体源
            checkFluidConnections();
        }
    }

    private void checkFluidConnections() {
        // 检查是否有流体管道连接
        hasFluidSource = AqueductHelper.hasFluidConnection(level, pos);
        
        if (hasFluidSource) {
            // 判断是输入还是输出
            isInputSource = AqueductHelper.isFluidInput(level, pos);
            isOutputSource = !isInputSource;
        }
    }

    public boolean hasFluid() {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof AbstractAqueductBlockEntity) {
            return !((AbstractAqueductBlockEntity) be).getFluid().isEmpty();
        }
        return false;
    }

    public boolean isFull() {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof AbstractAqueductBlockEntity) {
            AbstractAqueductBlockEntity aqueduct = (AbstractAqueductBlockEntity) be;
            return aqueduct.getSpace() == 0;
        }
        return false;
    }

    public FluidStack getFluid() {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof AbstractAqueductBlockEntity) {
            return ((AbstractAqueductBlockEntity) be).getFluid();
        }
        return FluidStack.EMPTY;
    }

    public int getFluidAmount() {
        return getFluid().getAmount();
    }

    public BlockPos getPos() {
        return pos;
    }

    public NodeType getType() {
        return type;
    }

    public boolean isLocked() {
        return locked;
    }

    public boolean hasFluidSource() {
        return hasFluidSource;
    }

    public boolean isInputSource() {
        return isInputSource;
    }

    public boolean isOutputSource() {
        return isOutputSource;
    }

    public enum NodeType {
        REGULAR,
        INTERFACE,
        CLOSED
    }
}