package com.adonis.fluid.content.pipette;

import com.adonis.fluid.block.FluidInterface.FluidInterfaceBlockEntity;
import com.adonis.fluid.block.SmartFluidInterface.SmartFluidInterfaceBlockEntity;
import com.adonis.fluid.registry.CFBlock;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;
import java.util.Optional;

public class FluidInteractionPoint {
    private BlockPos pos;
    private Direction face;
    private Mode mode;
    private Level level;
    private long lastKnownValid = -1;

    public FluidInteractionPoint(Level level, BlockPos pos, BlockState state) {
        this.level = level;
        this.pos = pos;
        this.face = Direction.UP; // 默认从上方访问
        this.mode = Mode.DEPOSIT; // 默认为存放模式
    }

    public static FluidInteractionPoint create(Level level, BlockPos pos, BlockState state) {
        // 检查是否为支持的方块类型
        if (isValidFluidBlock(state)) {
            return new FluidInteractionPoint(level, pos, state);
        }
        return null;
    }

    private static boolean isValidFluidBlock(BlockState state) {
        // 支持工作盆、流体接口、智能流体接口
        return AllBlocks.BASIN.has(state) || 
               CFBlock.FLUID_INTERFACE.has(state) || 
               CFBlock.SMART_FLUID_INTERFACE.has(state);
    }

    public boolean isValid() {
        if (level == null) return false;
        
        long gameTime = level.getGameTime();
        if (gameTime == lastKnownValid) return true;
        
        BlockState state = level.getBlockState(pos);
        boolean valid = isValidFluidBlock(state) && 
                       level.getBlockEntity(pos) != null &&
                       getFluidHandler() != null;
        
        if (valid) {
            lastKnownValid = gameTime;
        }
        
        return valid;
    }

    @Nullable
    private IFluidHandler getFluidHandler() {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        
        return be.getCapability(ForgeCapabilities.FLUID_HANDLER, face).orElse(null);
    }

    public FluidStack extract(int maxAmount, boolean simulate) {
        IFluidHandler handler = getFluidHandler();
        if (handler == null) return FluidStack.EMPTY;
        
        return handler.drain(maxAmount, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
    }

    public FluidStack insert(FluidStack stack, boolean simulate) {
        IFluidHandler handler = getFluidHandler();
        if (handler == null) return stack;
        
        int filled = handler.fill(stack, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        FluidStack remainder = stack.copy();
        remainder.shrink(filled);
        return remainder;
    }

    public boolean canExtract() {
        IFluidHandler handler = getFluidHandler();
        if (handler == null) return false;
        
        return !handler.drain(1, IFluidHandler.FluidAction.SIMULATE).isEmpty();
    }

    public boolean canInsert(FluidStack stack) {
        IFluidHandler handler = getFluidHandler();
        if (handler == null) return false;
        
        return handler.fill(stack, IFluidHandler.FluidAction.SIMULATE) > 0;
    }

    public void cycleMode() {
        mode = mode == Mode.TAKE ? Mode.DEPOSIT : Mode.TAKE;
    }

    public Mode getMode() {
        return mode;
    }

    public BlockPos getPos() {
        return pos;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public void keepAlive() {
        lastKnownValid = level != null ? level.getGameTime() : -1;
    }

    public void updateCachedState() {
        lastKnownValid = -1;
    }

    public CompoundTag serialize(BlockPos armPos) {
        CompoundTag nbt = new CompoundTag();
        nbt.put("Pos", net.minecraft.nbt.NbtUtils.writeBlockPos(pos.subtract(armPos)));
        nbt.putString("Mode", mode.name());
        nbt.putString("Face", face.name());
        return nbt;
    }

    @Nullable
    public static FluidInteractionPoint deserialize(CompoundTag nbt, Level level, BlockPos armPos) {
        BlockPos pos = net.minecraft.nbt.NbtUtils.readBlockPos(nbt.getCompound("Pos")).offset(armPos);
        BlockState state = level.getBlockState(pos);
        
        FluidInteractionPoint point = create(level, pos, state);
        if (point != null) {
            if (nbt.contains("Mode")) {
                try {
                    point.mode = Mode.valueOf(nbt.getString("Mode"));
                } catch (IllegalArgumentException e) {
                    point.mode = Mode.DEPOSIT;
                }
            }
            if (nbt.contains("Face")) {
                try {
                    point.face = Direction.valueOf(nbt.getString("Face"));
                } catch (IllegalArgumentException e) {
                    point.face = Direction.UP;
                }
            }
        }
        return point;
    }

    public static void transformPos(CompoundTag nbt, StructureTransform transform) {
        BlockPos pos = net.minecraft.nbt.NbtUtils.readBlockPos(nbt.getCompound("Pos"));
        pos = transform.apply(pos);
        nbt.put("Pos", net.minecraft.nbt.NbtUtils.writeBlockPos(pos));
        
        if (nbt.contains("Face")) {
            Direction face = Direction.valueOf(nbt.getString("Face"));
            face = transform.transformDirection(face);
            nbt.putString("Face", face.name());
        }
    }

    public enum Mode {
        TAKE("gui.create.mechanical_arm.extract", 0x9A2020),
        DEPOSIT("gui.create.mechanical_arm.deposit", 0x1F6B2D);

        private final String translationKey;
        private final int color;

        Mode(String translationKey, int color) {
            this.translationKey = translationKey;
            this.color = color;
        }

        public String getTranslationKey() {
            return translationKey;
        }

        public int getColor() {
            return color;
        }
    }
}