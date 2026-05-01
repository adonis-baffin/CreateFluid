package com.adonis.fluid.block.FluidAtomizer;

import com.adonis.fluid.registry.CFMountedStorageTypes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.api.contraption.storage.SyncedMountedStorage;
import com.simibubi.create.api.contraption.storage.fluid.WrapperMountedFluidStorage;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

public class FluidAtomizerMountedStorage extends WrapperMountedFluidStorage<FluidAtomizerMountedStorage.Handler> implements SyncedMountedStorage {

    public static final MapCodec<FluidAtomizerMountedStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("capacity").forGetter(FluidAtomizerMountedStorage::getCapacity),
            FluidStack.OPTIONAL_CODEC.fieldOf("fluid").forGetter(FluidAtomizerMountedStorage::getFluid)
    ).apply(i, FluidAtomizerMountedStorage::new));

    private boolean dirty;

    protected FluidAtomizerMountedStorage(int capacity, FluidStack stack) {
        super(CFMountedStorageTypes.FLUID_ATOMIZER.get(), new Handler(capacity, stack));
        this.wrapped.onChange = () -> this.dirty = true;
    }

    @Override
    public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        if (!(be instanceof FluidAtomizerBlockEntity atomizer)) {
            return;
        }

        FluidTank tank = atomizer.getPrimaryTankInventory();
        tank.setFluid(this.wrapped.getFluid().copy());
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
        BlockEntity be = contraption.presentBlockEntities.get(localPos);
        if (!(be instanceof FluidAtomizerBlockEntity atomizer)) {
            return;
        }

        FluidTank tank = atomizer.getPrimaryTankInventory();
        tank.setFluid(this.getFluid().copy());

        if (atomizer.tankBehaviour != null) {
            SmartFluidTankBehaviour.TankSegment primaryTank = atomizer.tankBehaviour.getPrimaryTank();
            if (primaryTank != null) {
                float fillLevel = (float) this.getFluid().getAmount() / this.getCapacity();
                LerpedFloat level = primaryTank.getFluidLevel();
                if (level != null) {
                    level.chase(fillLevel, 0.5f, LerpedFloat.Chaser.EXP);
                }
            }
        }
    }

    public static FluidAtomizerMountedStorage fromAtomizer(FluidAtomizerBlockEntity atomizer) {
        FluidStack fluid = atomizer.getFluid().copy();
        return new FluidAtomizerMountedStorage(FluidAtomizerBlockEntity.CAPACITY, fluid);
    }

    public static FluidAtomizerMountedStorage fromLegacy(HolderLookup.Provider registries, CompoundTag nbt) {
        int capacity = nbt.getInt("Capacity");
        FluidStack fluid = FluidStack.parseOptional(registries, nbt);
        return new FluidAtomizerMountedStorage(capacity, fluid);
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
