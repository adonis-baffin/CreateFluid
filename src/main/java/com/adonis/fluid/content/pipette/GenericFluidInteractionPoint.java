package com.adonis.fluid.content.pipette;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;

public class GenericFluidInteractionPoint extends FluidInteractionPoint {

    @Nullable
    private Direction preferredFace;

    public GenericFluidInteractionPoint(Level level, BlockPos pos, BlockState state) {
        super(level, pos, state);
        preferredFace = findBestFace(level, pos);
    }

    public GenericFluidInteractionPoint(Level level, BlockPos pos, BlockState state, Mode defaultMode) {
        super(level, pos, state);
        mode = defaultMode;
        preferredFace = findBestFace(level, pos);
    }

    @Nullable
    private Direction findBestFace(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return Direction.UP;
        }

        Direction[] priorities = {
                Direction.UP,
                Direction.DOWN,
                Direction.NORTH,
                Direction.SOUTH,
                Direction.EAST,
                Direction.WEST
        };

        for (Direction dir : priorities) {
            if (hasFluidCapabilitySafe(level, pos, dir)) {
                return dir;
            }
        }

        if (hasFluidCapabilitySafe(level, pos, null)) {
            return null;
        }

        return Direction.UP;
    }

    private boolean hasFluidCapabilitySafe(Level level, BlockPos pos, @Nullable Direction dir) {
        try {
            return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, dir) != null;
        } catch (NullPointerException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Nullable
    private IFluidHandler getGenericFluidHandler() {
        if (level.getBlockEntity(pos) == null) {
            return null;
        }

        try {
            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, preferredFace);
            if (handler != null) {
                return handler;
            }
        } catch (NullPointerException ignored) {
        } catch (Exception ignored) {
        }

        for (Direction dir : Direction.values()) {
            try {
                IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, dir);
                if (handler != null) {
                    preferredFace = dir;
                    return handler;
                }
            } catch (Exception ignored) {
            }
        }

        try {
            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
            if (handler != null) {
                preferredFace = null;
            }
            return handler;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public boolean isValid() {
        if (level == null) {
            return false;
        }

        long gameTime = level.getGameTime();
        if (gameTime == lastKnownValid) {
            return true;
        }

        IFluidHandler handler = getGenericFluidHandler();
        if (handler != null) {
            lastKnownValid = gameTime;
            return true;
        }

        return false;
    }

    @Override
    public FluidStack extract(int maxAmount, boolean simulate) {
        IFluidHandler handler = getGenericFluidHandler();
        if (handler == null) {
            return FluidStack.EMPTY;
        }

        try {
            return handler.drain(maxAmount, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        } catch (Exception e) {
            return FluidStack.EMPTY;
        }
    }

    @Override
    public FluidStack insert(FluidStack stack, boolean simulate) {
        IFluidHandler handler = getGenericFluidHandler();
        if (handler == null) {
            return stack;
        }

        try {
            int filled = handler.fill(stack, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            FluidStack remainder = stack.copy();
            remainder.shrink(filled);
            return remainder;
        } catch (Exception e) {
            return stack;
        }
    }

    @Override
    public boolean canExtract() {
        IFluidHandler handler = getGenericFluidHandler();
        if (handler == null) {
            return false;
        }

        try {
            return !handler.drain(1, IFluidHandler.FluidAction.SIMULATE).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean canInsert(FluidStack stack) {
        IFluidHandler handler = getGenericFluidHandler();
        if (handler == null) {
            return false;
        }

        try {
            return handler.fill(stack, IFluidHandler.FluidAction.SIMULATE) > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
