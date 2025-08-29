package com.adonis.fluid.block.aqueduct;

import com.adonis.fluid.config.CFCommonConfig;
import com.adonis.fluid.content.aqueduct.AqueductBehaviour;
import com.adonis.fluid.content.aqueduct.AqueductPropagator;
import com.adonis.fluid.content.aqueduct.FluidFlowAnimation;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.createmod.catnip.animation.LerpedFloat;  // 修正导入
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public abstract class AbstractAqueductBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAqueductBlockEntity.class);

    protected SmartFluidTank tank;
    protected LazyOptional<IFluidHandler> fluidCapability;
    protected AqueductBehaviour aqueductBehaviour;

    // 使用LerpedFloat来平滑流体动画
    protected LerpedFloat fluidLevel;

    // 客户端动画
    @OnlyIn(Dist.CLIENT)
    protected FluidFlowAnimation flowAnimation;

    protected boolean locked = false;
    protected int transferCooldown = 0;
    protected static final int CAPACITY = 1000; // 1 bucket

    // 性能优化：缓存方向
    private Direction cachedFlowDirection = null;
    private boolean flowDirectionDirty = true;

    public AbstractAqueductBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);

        tank = new SmartFluidTank(CAPACITY, this::onFluidStackChanged);
        fluidCapability = LazyOptional.of(() -> new AqueductFluidHandler());
        fluidLevel = LerpedFloat.linear().startWithValue(0);

        if (level != null && level.isClientSide) {
            flowAnimation = new FluidFlowAnimation();
        }
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        aqueductBehaviour = new AqueductBehaviour(this);
        behaviours.add(aqueductBehaviour);
    }

    protected void onFluidStackChanged(FluidStack newFluid) {
        if (!level.isClientSide) {
            // 更新流体等级动画
            float targetLevel = tank.getFluidAmount() / (float) tank.getCapacity();
            fluidLevel.chase(targetLevel, 0.25, LerpedFloat.Chaser.LINEAR);

            setChanged();
            sendData();
        }
    }

    @Override
    public void tick() {
        super.tick();

        // 更新流体动画
        fluidLevel.tickChaser();

        if (level.isClientSide) {
            tickClient();
        } else {
            tickServer();
        }
    }

    protected void tickClient() {
        if (flowAnimation != null && !tank.isEmpty()) {
            flowAnimation.tick();
        }
    }

    protected void tickServer() {
        if (transferCooldown > 0) {
            transferCooldown--;
            return;
        }

        if (!locked) {
            performTransfer();
        }
    }

    protected abstract void performTransfer();

    public Direction getFlowDirection() {
        if (flowDirectionDirty || cachedFlowDirection == null) {
            cachedFlowDirection = getBlockState().getValue(AbstractAqueductBlock.FACING);
            flowDirectionDirty = false;
        }
        return cachedFlowDirection;
    }

    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        flowDirectionDirty = true;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        if (this.locked != locked) {
            this.locked = locked;
            setChanged();
        }
    }

    public float getFluidLevel() {
        return tank.getFluidAmount() / (float) tank.getCapacity();
    }

    public float getRenderedFluidLevel(float partialTicks) {
        return fluidLevel.getValue(partialTicks);
    }

    public FluidStack getFluid() {
        return tank.getFluid();
    }

    public int getSpace() {
        return tank.getCapacity() - tank.getFluidAmount();
    }

    public SmartFluidTank getTank() {
        return tank;
    }

    @OnlyIn(Dist.CLIENT)
    public FluidFlowAnimation getFlowAnimation() {
        return flowAnimation;
    }

    public void notifyNetworkUpdate() {
        if (!level.isClientSide) {
            AqueductPropagator.notifyNetworkUpdate(level, worldPosition);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return this.containedFluidTooltip(tooltip, isPlayerSneaking,
                this.getCapability(ForgeCapabilities.FLUID_HANDLER));
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.put("TankContent", tank.writeToNBT(new CompoundTag()));
        tag.putBoolean("Locked", locked);
        tag.putInt("TransferCooldown", transferCooldown);
        tag.put("FluidLevel", fluidLevel.writeNBT());

        if (clientPacket && flowAnimation != null) {
            tag.putFloat("FlowProgress", flowAnimation.getProgress());
        }
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        tank.readFromNBT(tag.getCompound("TankContent"));
        locked = tag.getBoolean("Locked");
        transferCooldown = tag.getInt("TransferCooldown");

        if (tag.contains("FluidLevel")) {
            fluidLevel.readNBT(tag.getCompound("FluidLevel"), clientPacket);
        }

        if (clientPacket && flowAnimation != null && tag.contains("FlowProgress")) {
            flowAnimation.setProgress(tag.getFloat("FlowProgress"));
        }
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

    protected class AqueductFluidHandler implements IFluidHandler {
        // 实现保持不变
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
            int filled = AbstractAqueductBlockEntity.this.tank.fill(resource, action);
            if (filled > 0 && action.execute()) {
                AbstractAqueductBlockEntity.this.transferCooldown = 2;
            }
            return filled;
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