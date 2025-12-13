package com.adonis.fluid.block.CopperSink;

import com.adonis.fluid.config.CFCommonConfig;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public class CopperSinkBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    private static final int CAPACITY = 2000;

    private final CopperSinkTank tank;
    private final LazyOptional<IFluidHandler> fluidCapability;

    private LerpedFloat fluidLevel;
    private boolean forceFluidLevelUpdate;

    public CopperSinkBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.tank = new CopperSinkTank(CAPACITY, fs -> onTankContentsChanged());
        this.fluidCapability = LazyOptional.of(() -> tank);
        this.fluidLevel = LerpedFloat.linear().startWithValue(0);
        this.forceFluidLevelUpdate = true;
    }

    private void onTankContentsChanged() {
        if (level != null && !level.isClientSide) {
            try {
                setChanged();
                sendData();
            } catch (Exception ignored) {}
        }
        onFluidChanged();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && CFCommonConfig.isCopperSinkInfinite()) {
            tank.fillInfiniteWater();
            setChanged();
            sendData();
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null) return;

        if (level.isClientSide) {
            if (fluidLevel != null) {
                fluidLevel.tickChaser();
            }
            return;
        }

        // 在无限模式下不再每 tick 强制填充，由 fill() 处理恢复满水状态
        // 如果需要确保空载时也显示满水，可保留，但不影响输入
        // if (CFCommonConfig.isCopperSinkInfinite()) {
        //     tank.fillInfiniteWater();
        // }
    }

    private void onFluidChanged() {
        float fillState = getFillState();
        if (fluidLevel != null) {
            fluidLevel.chase(fillState, 0.5f, LerpedFloat.Chaser.EXP);
        }
    }

    public float getFillState() {
        int amount = CFCommonConfig.isCopperSinkInfinite() ? CAPACITY : tank.getFluidAmount();
        return (float) amount / CAPACITY;
    }

    public float getRenderedFluidLevel(float partialTicks) {
        if (fluidLevel == null) return getFillState();
        return fluidLevel.getValue(partialTicks);
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCapability.invalidate();
    }

    public LerpedFloat getFluidLevel() {
        return fluidLevel;
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.put("Tank", tank.writeToNBT(new CompoundTag()));

        if (clientPacket && forceFluidLevelUpdate) {
            tag.putBoolean("ForceFluidLevel", true);
            forceFluidLevelUpdate = false;
        }
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        if (tag.contains("Tank")) {
            tank.readFromNBT(tag.getCompound("Tank"));
        }

        if (clientPacket) {
            float fillState = getFillState();
            if (tag.contains("ForceFluidLevel") || fluidLevel == null) {
                fluidLevel = LerpedFloat.linear().startWithValue(fillState);
            }
            fluidLevel.chase(fillState, 0.5f, LerpedFloat.Chaser.EXP);
        }
    }

    public SmartFluidTank getTank() {
        return tank;
    }

    public static class CopperSinkTank extends SmartFluidTank {

        public CopperSinkTank(int capacity, Consumer<FluidStack> onChange) {
            super(capacity, onChange);
        }

        /** 在无限模式下强制显示为满水，用于初始加载或防止意外清空 */
        public void fillInfiniteWater() {
            if (!CFCommonConfig.isCopperSinkInfinite()) return;

            if (fluid.isEmpty() || fluid.getFluid() != Fluids.WATER) {
                setFluid(new FluidStack(Fluids.WATER, capacity));
            } else if (fluid.getAmount() < capacity) {
                fluid.setAmount(capacity);
                onContentsChanged();
            }
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;

            if (!CFCommonConfig.isCopperSinkInfinite()) {
                // 非无限模式：正常只能装水
                return super.fill(resource, action);
            }

            // 无限模式：接受任意流体并全部销毁
            int accepted = resource.getAmount();

            if (action.execute()) {
                // 输入后立即恢复无限水状态（保持显示满水）
                fillInfiniteWater();
                onContentsChanged();
            }

            return accepted;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || resource.getFluid() != Fluids.WATER) return FluidStack.EMPTY;

            if (!CFCommonConfig.isCopperSinkInfinite()) {
                return super.drain(resource, action);
            }

            // 无限模式：无限抽水
            if (action.execute()) {
                onContentsChanged();
            }
            return new FluidStack(Fluids.WATER, resource.getAmount());
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) return FluidStack.EMPTY;

            if (!CFCommonConfig.isCopperSinkInfinite()) {
                return super.drain(maxDrain, action);
            }

            // 无限模式：无限抽水
            if (action.execute()) {
                onContentsChanged();
            }
            return new FluidStack(Fluids.WATER, maxDrain);
        }

        @Override
        public int getFluidAmount() {
            return CFCommonConfig.isCopperSinkInfinite() ? capacity : super.getFluidAmount();
        }

        @Override
        public FluidStack getFluid() {
            if (CFCommonConfig.isCopperSinkInfinite()) {
                return new FluidStack(Fluids.WATER, getFluidAmount());
            }
            return super.getFluid();
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.translate("gui.goggles.fluid_container")
                .forGoggles(tooltip);

        int displayedAmount = CFCommonConfig.isCopperSinkInfinite() ? CAPACITY : tank.getFluidAmount();

        CreateLang.text("")
                .add(CreateLang.fluidName(new FluidStack(Fluids.WATER, 1))
                        .add(CreateLang.text(" "))
                        .style(ChatFormatting.GRAY)
                        .add(CreateLang.number(displayedAmount)
                                .add(CreateLang.translate("generic.unit.millibuckets"))
                                .style(ChatFormatting.BLUE)))
                .forGoggles(tooltip, 1);

        CreateLang.translate("gui.goggles.fluid_container.capacity")
                .add(CreateLang.number(CAPACITY)
                        .add(CreateLang.translate("generic.unit.millibuckets"))
                        .style(ChatFormatting.DARK_GREEN))
                .style(ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);

        if (CFCommonConfig.isCopperSinkInfinite()) {
            CreateLang.text("∞ ")
                    .style(ChatFormatting.AQUA)
                    .add(CreateLang.translate("block.fluid.copper_sink.infinite_source")
                            .style(ChatFormatting.DARK_AQUA))
                    .forGoggles(tooltip, 1);
        }

        return true;
    }
}