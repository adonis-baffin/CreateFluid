package com.adonis.fluid.block.CopperSink;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.api.contraption.storage.SyncedMountedStorage;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;
import com.simibubi.create.api.contraption.storage.fluid.WrapperMountedFluidStorage;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.foundation.utility.CreateCodecs;
import com.adonis.fluid.registry.CFMountedStorageTypes;

import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import org.jetbrains.annotations.Nullable;

public class CopperSinkMountedStorage extends WrapperMountedFluidStorage<CopperSinkMountedStorage.Handler> implements SyncedMountedStorage {

    public static final Codec<CopperSinkMountedStorage> CODEC = RecordCodecBuilder.create(i -> i.group(
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("capacity").forGetter(CopperSinkMountedStorage::getCapacity),
            CreateCodecs.FLUID_STACK_CODEC.fieldOf("fluid").forGetter(CopperSinkMountedStorage::getFluid)
    ).apply(i, CopperSinkMountedStorage::new));

    private boolean dirty;

    protected CopperSinkMountedStorage(MountedFluidStorageType<?> type, int capacity, FluidStack stack) {
        super(type, new Handler(capacity, stack));
        this.wrapped.onChange = () -> this.dirty = true;
    }

    protected CopperSinkMountedStorage(int capacity, FluidStack stack) {
        this(CFMountedStorageTypes.COPPER_SINK.get(), capacity, stack);
    }

    @Override
    public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        if (be instanceof CopperSinkBlockEntity sink) {
            // Restore fluid to the sink when contraption is disassembled
            sink.getTank().setFluid(this.wrapped.getFluid().copy());
        }
    }

    public FluidStack getFluid() {
        return this.wrapped.getFluid();
    }

    public int getCapacity() {
        return this.wrapped.getCapacity();
    }

    @Override
    public boolean isDirty() {
        return this.dirty;
    }

    @Override
    public void markClean() {
        this.dirty = false;
    }

    @Override
    public void afterSync(Contraption contraption, BlockPos localPos) {
        BlockEntity be = contraption.getBlockEntityClientSide(localPos);
        if (!(be instanceof CopperSinkBlockEntity sink))
            return;

        // Sync fluid to client-side block entity for rendering
        sink.getTank().setFluid(this.getFluid().copy());

        // Manually update the fluid level for rendering
        float fillLevel = (float) this.getFluid().getAmount() / this.getCapacity();
        LerpedFloat fluidLevel = sink.getFluidLevel();
        if (fluidLevel != null) {
            fluidLevel.chase(fillLevel, 0.5f, LerpedFloat.Chaser.EXP);
        }
    }

    public static CopperSinkMountedStorage fromSink(CopperSinkBlockEntity sink) {
        // Create an isolated copy of the tank contents
        FluidStack fluid = sink.getTank().getFluid().copy();
        int capacity = sink.getTank().getCapacity();
        return new CopperSinkMountedStorage(capacity, fluid);
    }

    public static CopperSinkMountedStorage fromLegacy(CompoundTag nbt) {
        int capacity = nbt.getInt("Capacity");
        FluidStack fluid = FluidStack.loadFluidStackFromNBT(nbt);
        return new CopperSinkMountedStorage(capacity, fluid);
    }

    public static final class Handler extends FluidTank {
        private Runnable onChange = () -> {};

        public Handler(int capacity, FluidStack stack) {
            super(capacity);
            this.setFluid(stack);
        }

        @Override
        protected void onContentsChanged() {
            this.onChange.run();
        }
    }
}