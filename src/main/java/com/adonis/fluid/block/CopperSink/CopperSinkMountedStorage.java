package com.adonis.fluid.block.CopperSink;

import com.adonis.fluid.config.CFCommonConfig;
import com.adonis.fluid.registry.CFMountedStorageTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.api.contraption.storage.SyncedMountedStorage;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;
import com.simibubi.create.api.contraption.storage.fluid.WrapperMountedFluidStorage;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.foundation.utility.CreateCodecs;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import org.jetbrains.annotations.Nullable;

public class CopperSinkMountedStorage extends WrapperMountedFluidStorage<CopperSinkMountedStorage.Handler> implements SyncedMountedStorage {

    // CODEC：从 NBT 加载时使用当前配置决定是否无限
    public static final Codec<CopperSinkMountedStorage> CODEC = RecordCodecBuilder.create(i -> i.group(
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("capacity").forGetter(s -> s.wrapped.getCapacity()),
            CreateCodecs.FLUID_STACK_CODEC.fieldOf("fluid").forGetter(s -> s.wrapped.getFluid())
    ).apply(i, (capacity, fluid) -> {
        boolean infinite = CFCommonConfig.isCopperSinkInfinite();
        Handler handler = new Handler(capacity, fluid, infinite);
        return new CopperSinkMountedStorage(handler);
    }));

    private boolean dirty;

    // 私有构造函数：直接传入自定义 Handler
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
        BlockEntity be = contraption.getBlockEntityClientSide(localPos);
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

    // 旧版本兼容（如果有的话）
    public static CopperSinkMountedStorage fromLegacy(CompoundTag nbt) {
        int capacity = nbt.getInt("Capacity");
        FluidStack fluid = FluidStack.loadFluidStackFromNBT(nbt.getCompound("fluid")); // 根据实际 NBT 结构调整
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
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;

            if (!infinite) {
                return super.fill(resource, action);
            }

            // 无限模式：接受任意流体并销毁
            int accepted = resource.getAmount();

            if (action.execute()) {
                // 强制保持满水状态
                this.setFluid(new FluidStack(Fluids.WATER, capacity));
                onContentsChanged();
            }

            return accepted;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || resource.getFluid() != Fluids.WATER) return FluidStack.EMPTY;
            if (!infinite) return super.drain(resource, action);
            if (action.execute()) onContentsChanged();
            return new FluidStack(Fluids.WATER, resource.getAmount());
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
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