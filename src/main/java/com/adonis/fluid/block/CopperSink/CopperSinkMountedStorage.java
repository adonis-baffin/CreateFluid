package com.adonis.fluid.block.CopperSink;

import com.adonis.fluid.config.CFCommonConfig;
import com.adonis.fluid.registry.CFMountedStorageTypes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.api.contraption.storage.SyncedMountedStorage;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;
import com.simibubi.create.api.contraption.storage.fluid.WrapperMountedFluidStorage;
import com.simibubi.create.content.contraptions.Contraption;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import org.jetbrains.annotations.Nullable;

public class CopperSinkMountedStorage extends WrapperMountedFluidStorage<CopperSinkMountedStorage.Handler> implements SyncedMountedStorage {

    public static final MapCodec<CopperSinkMountedStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("capacity").forGetter(s -> s.wrapped.getCapacity()),
            FluidStack.OPTIONAL_CODEC.fieldOf("fluid").forGetter(s -> s.wrapped.getFluid())
    ).apply(i, (capacity, fluid) -> {
        boolean infinite = CFCommonConfig.isCopperSinkInfinite();
        Handler handler = new Handler(capacity, fluid, infinite);
        return new CopperSinkMountedStorage(handler);
    }));

    private boolean dirty;

    private CopperSinkMountedStorage(Handler handler) {
        super(CFMountedStorageTypes.COPPER_SINK.get(), handler);
        this.wrapped.onChange = () -> this.dirty = true;
    }

    @Override
    public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        if (be instanceof CopperSinkBlockEntity sink) {
            sink.getTank().setFluid(this.wrapped.getFluid().copy());
        }
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void markClean() {
        dirty = false;
    }

    @Override
    public void afterSync(Contraption contraption, BlockPos localPos) {
        BlockEntity be = contraption.presentBlockEntities.get(localPos);
        if (!(be instanceof CopperSinkBlockEntity sink)) return;

        sink.getTank().setFluid(this.wrapped.getFluid().copy());

        float fillLevel = (float) this.wrapped.getFluidAmount() / this.wrapped.getCapacity();
        LerpedFloat fluidLevel = sink.getFluidLevel();
        if (fluidLevel != null) {
            fluidLevel.chase(fillLevel, 0.5f, LerpedFloat.Chaser.EXP);
        }
    }

    public static CopperSinkMountedStorage fromSink(CopperSinkBlockEntity sink) {
        FluidStack fluid = sink.getTank().getFluid().copy();
        int capacity = sink.getTank().getCapacity();
        boolean infinite = CFCommonConfig.isCopperSinkInfinite();

        Handler handler = new Handler(capacity, fluid, infinite);
        CopperSinkMountedStorage storage = new CopperSinkMountedStorage(handler);
        return storage;
    }

    public static CopperSinkMountedStorage fromLegacy(HolderLookup.Provider registries, CompoundTag nbt) {
        int capacity = nbt.getInt("Capacity");
        FluidStack fluid = FluidStack.parseOptional(registries, nbt);
        boolean infinite = CFCommonConfig.isCopperSinkInfinite();
        Handler handler = new Handler(capacity, fluid, infinite);
        return new CopperSinkMountedStorage(handler);
    }

    public static final class Handler extends FluidTank {
        private Runnable onChange = () -> {};
        private final boolean infinite;

        public Handler(int capacity, FluidStack stack, boolean infinite) {
            super(capacity);
            this.infinite = infinite;
            this.setFluid(stack);
        }

        @Override
        protected void onContentsChanged() {
            this.onChange.run();
        }

        @Override
        public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
            if (resource.isEmpty()) return 0;

            if (!infinite) {
                return super.fill(resource, action);
            }

            int accepted = resource.getAmount();

            if (action.execute()) {
                this.setFluid(new FluidStack(Fluids.WATER, capacity));
                onContentsChanged();
            }

            return accepted;
        }

        @Override
        public FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action) {
            if (resource.isEmpty() || resource.getFluid() != Fluids.WATER) return FluidStack.EMPTY;
            if (!infinite) return super.drain(resource, action);
            if (action.execute()) onContentsChanged();
            return new FluidStack(Fluids.WATER, resource.getAmount());
        }

        @Override
        public FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
            if (maxDrain <= 0) return FluidStack.EMPTY;
            if (!infinite) return super.drain(maxDrain, action);
            if (action.execute()) onContentsChanged();
            return new FluidStack(Fluids.WATER, maxDrain);
        }

        @Override
        public int getFluidAmount() {
            return infinite ? capacity : super.getFluidAmount();
        }

        @Override
        public FluidStack getFluid() {
            if (infinite) {
                return new FluidStack(Fluids.WATER, getFluidAmount());
            }
            return super.getFluid();
        }
    }
}
