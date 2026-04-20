package com.adonis.fluid.block.GutterOutlet;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.api.contraption.storage.SyncedMountedStorage;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;
import com.simibubi.create.api.contraption.storage.fluid.WrapperMountedFluidStorage;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.adonis.fluid.registry.CFMountedStorageTypes;

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

public class GutterOutletMountedStorage extends WrapperMountedFluidStorage<GutterOutletMountedStorage.Handler> implements SyncedMountedStorage {

    public static final MapCodec<GutterOutletMountedStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("capacity").forGetter(GutterOutletMountedStorage::getCapacity),
            FluidStack.OPTIONAL_CODEC.fieldOf("fluid").forGetter(GutterOutletMountedStorage::getFluid)
    ).apply(i, GutterOutletMountedStorage::new));

    private boolean dirty;

    protected GutterOutletMountedStorage(MountedFluidStorageType<?> type, int capacity, FluidStack stack) {
        super(type, new Handler(capacity, stack));
        this.wrapped.onChange = () -> this.dirty = true;
    }

    protected GutterOutletMountedStorage(int capacity, FluidStack stack) {
        this(CFMountedStorageTypes.GUTTER_OUTLET.get(), capacity, stack);
    }

    @Override
    public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        if (be instanceof GutterOutletBlockEntity gutter) {
            IFluidHandler tank = gutter.tankBehaviour.getCapability();
            if (tank != null) {
                tank.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
                tank.fill(this.wrapped.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
            }
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
        BlockEntity be = contraption.presentBlockEntities.get(localPos);
        if (!(be instanceof GutterOutletBlockEntity gutter))
            return;

        // 直接更新底层 tank 的流体，避免通过 IFluidHandler 的 drain/fill 绕弯
        // 在 contraption 中 TankSegment 的回调可能因 blockEntity.hasLevel() 为 false 而不触发，
        // 因此需要手动同步 fluidLevel
        FluidTank inv = gutter.getTankInventory();
        if (inv != null) {
            inv.setFluid(this.getFluid().copy());
        }

        float fillLevel = (float) this.getFluid().getAmount() / this.getCapacity();

        // 更新 GutterOutletBlockEntity 自己的 fluidLevel（供 MovementBehaviour 使用）
        LerpedFloat fluidLevel = gutter.getFluidLevel();
        if (fluidLevel != null) {
            fluidLevel.chase(fillLevel, 0.5f, LerpedFloat.Chaser.EXP);
        }

        // 更新 SmartFluidTankBehaviour 的 TankSegment（供 Renderer 使用）
        if (gutter.tankBehaviour != null) {
            SmartFluidTankBehaviour.TankSegment primaryTank = gutter.tankBehaviour.getPrimaryTank();
            if (primaryTank != null) {
                primaryTank.getFluidLevel().chase(fillLevel, 0.5f, LerpedFloat.Chaser.EXP);
            }
        }
    }

    public static GutterOutletMountedStorage fromGutter(GutterOutletBlockEntity gutter) {
        FluidStack fluid = gutter.getFluid().copy();
        int capacity = GutterOutletBlockEntity.CAPACITY;
        return new GutterOutletMountedStorage(capacity, fluid);
    }

    public static GutterOutletMountedStorage fromLegacy(HolderLookup.Provider registries, CompoundTag nbt) {
        int capacity = nbt.getInt("Capacity");
        FluidStack fluid = FluidStack.parseOptional(registries, nbt);
        return new GutterOutletMountedStorage(capacity, fluid);
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
