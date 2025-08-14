package com.adonis.fluid.block.aqueduct;

import com.adonis.fluid.config.CFCommonConfig;
import com.adonis.fluid.content.aqueduct.AqueductBehaviour;
import com.adonis.fluid.content.aqueduct.AqueductPropagator;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public abstract class AbstractAqueductBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    protected SmartFluidTank tank;
    protected LazyOptional<IFluidHandler> fluidCapability;
    protected AqueductBehaviour aqueductBehaviour;

    protected boolean locked = false;
    protected int transferCooldown = 0;
    protected static final int CAPACITY = 1000; // 1 bucket

    public AbstractAqueductBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);

        tank = new SmartFluidTank(CAPACITY, this::onFluidStackChanged);
        fluidCapability = LazyOptional.of(() -> new AqueductFluidHandler());
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        aqueductBehaviour = new AqueductBehaviour(this);
        behaviours.add(aqueductBehaviour);
    }

    protected void onFluidStackChanged(FluidStack newFluid) {
        if (!level.isClientSide) {
            setChanged();
            sendData();
        }
    }

    public void tick() {
        if (level.isClientSide) return;

        if (transferCooldown > 0) {
            transferCooldown--;
            return;
        }

        if (!locked) {
            performTransfer();
        }
    }

    protected void performTransfer() {
        // 子类实现具体的传输逻辑
    }

    public Direction getFlowDirection() {
        return getBlockState().getValue(AbstractAqueductBlock.FACING);
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
        setChanged();
    }

    public float getFluidLevel() {
        return tank.getFluidAmount() / (float) tank.getCapacity();
    }

    public FluidStack getFluid() {
        return tank.getFluid();
    }

    public int getSpace() {
        return tank.getCapacity() - tank.getFluidAmount();
    }

    // 公共访问方法，用于AqueductNetwork
    public SmartFluidTank getTank() {
        return tank;
    }

    public void notifyNetworkUpdate() {
        if (!level.isClientSide) {
            AqueductPropagator.notifyNetworkUpdate(level, worldPosition);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        // 使用与Create流体储罐相同的显示方式
        return this.containedFluidTooltip(tooltip, isPlayerSneaking, this.getCapability(ForgeCapabilities.FLUID_HANDLER));
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.put("TankContent", tank.writeToNBT(new CompoundTag()));
        tag.putBoolean("Locked", locked);
        tag.putInt("TransferCooldown", transferCooldown);
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        tank.readFromNBT(tag.getCompound("TankContent"));
        locked = tag.getBoolean("Locked");
        transferCooldown = tag.getInt("TransferCooldown");
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCapability.invalidate();
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    // 自定义流体处理器
    protected class AqueductFluidHandler implements IFluidHandler {

        @Override
        public int getTanks() {
            return 1;
        }

        @Nonnull
        @Override
        public FluidStack getFluidInTank(int tank) {
            return AbstractAqueductBlockEntity.this.tank.getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
            return AbstractAqueductBlockEntity.this.tank.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (locked) return 0;
            return AbstractAqueductBlockEntity.this.tank.fill(resource, action);
        }

        @Nonnull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (locked) return FluidStack.EMPTY;
            return AbstractAqueductBlockEntity.this.tank.drain(resource, action);
        }

        @Nonnull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (locked) return FluidStack.EMPTY;
            return AbstractAqueductBlockEntity.this.tank.drain(maxDrain, action);
        }
    }

    protected int getTransferRate() {
        return CFCommonConfig.AQUEDUCT_TRANSFER_RATE.get();
    }
}