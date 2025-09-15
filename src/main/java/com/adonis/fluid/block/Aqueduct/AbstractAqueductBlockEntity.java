package com.adonis.fluid.block.Aqueduct;

import com.adonis.fluid.config.CFCommonConfig;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
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
    protected LerpedFloat fluidLevel;

    @OnlyIn(Dist.CLIENT)
    protected com.adonis.fluid.content.aqueduct.FluidFlowAnimation flowAnimation;

    protected boolean locked = false; // 红石锁定
    protected static final int CAPACITY = 1000;

    // 工作状态
    protected WorkingState workingState = WorkingState.IDLE;
    protected BlockPos workingPartner = null; // 结对工作的伙伴
    protected int transferCooldown = 0;

    // 数据同步
    private boolean dataLoaded = false;
    private CompoundTag pendingTankData = null;

    public enum WorkingState {
        IDLE,        // 空闲
        SOURCE,      // 作为源头输出
        TARGET       // 作为目标接收
    }

    public AbstractAqueductBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        initializeTank();
        fluidLevel = LerpedFloat.linear().startWithValue(0);
    }

    private void initializeTank() {
        tank = new SmartFluidTank(CAPACITY, this::onFluidStackChanged);
        fluidCapability = LazyOptional.of(() -> new AqueductFluidHandler());
    }

    @Override
    public void onLoad() {
        super.onLoad();

        // 延迟加载tank数据
        if (!dataLoaded && pendingTankData != null) {
            tank.readFromNBT(pendingTankData);
            pendingTankData = null;
            dataLoaded = true;

            float currentLevel = tank.getFluidAmount() / (float) tank.getCapacity();
            fluidLevel.startWithValue(currentLevel);

            if (!level.isClientSide) {
                sendData();
            }
        }

        if (level.isClientSide && flowAnimation == null) {
            flowAnimation = new com.adonis.fluid.content.aqueduct.FluidFlowAnimation();
        }
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    protected void onFluidStackChanged(FluidStack newFluidStack) {
        if (!level.isClientSide) {
            float targetLevel = tank.getFluidAmount() / (float) tank.getCapacity();
            fluidLevel.chase(targetLevel, 0.25, LerpedFloat.Chaser.LINEAR);
            setChanged();
            sendData();
        }
    }

    @Override
    public void tick() {
        super.tick();

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
        }

        // 工作状态处理
        if (workingState == WorkingState.SOURCE && workingPartner != null) {
            // 作为源头，向伙伴传输
            performTransferToPartner();
        }
    }

    // 执行向伙伴传输
    private void performTransferToPartner() {
        if (workingPartner == null || tank.isEmpty()) {
            clearWorkingState();
            return;
        }

        BlockEntity partnerBE = level.getBlockEntity(workingPartner);
        if (!(partnerBE instanceof AbstractAqueductBlockEntity partner)) {
            clearWorkingState();
            return;
        }

        // 检查伙伴是否还在接收状态
        if (partner.workingState != WorkingState.TARGET) {
            clearWorkingState();
            return;
        }

        // 执行传输
        int transferRate = getTransferRate();
        int canDrain = Math.min(transferRate, tank.getFluidAmount());
        int canFill = Math.min(canDrain, partner.getSpace());

        if (canFill <= 0) {
            // 伙伴满了，结束工作
            clearWorkingState();
            partner.clearWorkingState();
            return;
        }

        FluidStack toTransfer = tank.getFluid().copy();
        toTransfer.setAmount(canFill);

        tank.drain(toTransfer, IFluidHandler.FluidAction.EXECUTE);
        partner.tank.fill(toTransfer, IFluidHandler.FluidAction.EXECUTE);

        // 如果自己空了，结束工作
        if (tank.isEmpty()) {
            clearWorkingState();
            partner.clearWorkingState();
        }
    }

    // 清除工作状态
    public void clearWorkingState() {
        workingState = WorkingState.IDLE;
        workingPartner = null;
        setChanged();
    }

    // 设置为源头状态
    public void setAsSource(BlockPos partner) {
        workingState = WorkingState.SOURCE;
        workingPartner = partner;
        setChanged();
    }

    // 设置为目标状态
    public void setAsTarget(BlockPos partner) {
        workingState = WorkingState.TARGET;
        workingPartner = partner;
        setChanged();
    }

    // 检查是否在工作状态
    public boolean isWorking() {
        return workingState != WorkingState.IDLE;
    }

    // 检查是否可以作为源头
    public boolean canBeSource() {
        return !locked && !tank.isEmpty() && workingState == WorkingState.IDLE;
    }

    // 检查是否可以作为目标
    public boolean canBeTarget(FluidStack fluid) {
        if (locked || workingState != WorkingState.IDLE) return false;
        if (tank.isEmpty()) return true;
        if (tank.getFluidAmount() >= tank.getCapacity()) return false;
        return tank.getFluid().isFluidEqual(fluid);
    }

    // 检查能否跳过（满且流体相同）
    public boolean canSkip(FluidStack fluid) {
        if (locked || workingState != WorkingState.IDLE) return false;
        return tank.getFluidAmount() >= tank.getCapacity() && tank.getFluid().isFluidEqual(fluid);
    }

    public Direction getFlowDirection() {
        return getBlockState().getValue(AbstractAqueductBlock.FACING);
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        if (this.locked != locked) {
            this.locked = locked;
            if (locked && isWorking()) {
                clearWorkingState();
            }
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

    public boolean isFull() {
        return tank.getFluidAmount() >= tank.getCapacity();
    }

    public boolean isEmpty() {
        return tank.isEmpty();
    }

    public SmartFluidTank getTank() {
        return tank;
    }

    @OnlyIn(Dist.CLIENT)
    public com.adonis.fluid.content.aqueduct.FluidFlowAnimation getFlowAnimation() {
        return flowAnimation;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return this.containedFluidTooltip(tooltip, isPlayerSneaking,
                this.getCapability(ForgeCapabilities.FLUID_HANDLER));
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);

        // 保存tank数据
        CompoundTag tankData = new CompoundTag();
        tank.writeToNBT(tankData);
        tag.put("Tank", tankData);

        // 保存状态
        tag.putBoolean("Locked", locked);
        tag.putInt("WorkingState", workingState.ordinal());

        if (workingPartner != null) {
            tag.putLong("WorkingPartner", workingPartner.asLong());
        }

        // 保存流体级别
        tag.put("FluidLevel", fluidLevel.writeNBT());

        if (clientPacket) {
            tag.putFloat("CurrentLevel", getFluidLevel());
            if (!tank.isEmpty()) {
                CompoundTag fluidTag = new CompoundTag();
                tank.getFluid().writeToNBT(fluidTag);
                tag.put("ClientFluid", fluidTag);
            }
        }
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);

        // 读取tank数据
        if (tag.contains("Tank")) {
            if (tank == null) {
                initializeTank();
            }
            pendingTankData = tag.getCompound("Tank");
            // 如果已经加载到世界中，直接读取数据
            if (level != null) {
                tank.readFromNBT(pendingTankData);
                pendingTankData = null;
                dataLoaded = true;
            }
        }

        locked = tag.getBoolean("Locked");

        if (tag.contains("WorkingState")) {
            workingState = WorkingState.values()[tag.getInt("WorkingState")];
        }

        if (tag.contains("WorkingPartner")) {
            workingPartner = BlockPos.of(tag.getLong("WorkingPartner"));
        }

        // 读取流体级别
        if (tag.contains("FluidLevel")) {
            fluidLevel.readNBT(tag.getCompound("FluidLevel"), clientPacket);
        }

        if (clientPacket) {
            if (tag.contains("ClientFluid")) {
                FluidStack clientFluid = FluidStack.loadFluidStackFromNBT(tag.getCompound("ClientFluid"));
                if (!clientFluid.isEmpty()) {
                    tank.setFluid(clientFluid);
                    float newLevel = clientFluid.getAmount() / (float) tank.getCapacity();
                    fluidLevel.startWithValue(newLevel);
                }
            } else if (tag.contains("CurrentLevel")) {
                float currentLevel = tag.getFloat("CurrentLevel");
                if (currentLevel == 0 && !tank.isEmpty()) {
                    tank.setFluid(FluidStack.EMPTY);
                }
                fluidLevel.startWithValue(currentLevel);
            }

            if (level != null && level.isClientSide && flowAnimation == null) {
                flowAnimation = new com.adonis.fluid.content.aqueduct.FluidFlowAnimation();
            }
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
            // 工作状态下不允许外部填充
            if (locked || isWorking()) return 0;

            int filled = AbstractAqueductBlockEntity.this.tank.fill(resource, action);
            if (filled > 0 && action.execute()) {
                AbstractAqueductBlockEntity.this.transferCooldown = 2;
            }
            return filled;
        }

        @Nonnull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            // 工作状态下不允许外部排出
            if (locked || isWorking()) return FluidStack.EMPTY;
            return AbstractAqueductBlockEntity.this.tank.drain(resource, action);
        }

        @Nonnull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            // 工作状态下不允许外部排出
            if (locked || isWorking()) return FluidStack.EMPTY;
            return AbstractAqueductBlockEntity.this.tank.drain(maxDrain, action);
        }
    }

    protected int getTransferRate() {
        return CFCommonConfig.AQUEDUCT_TRANSFER_RATE.get();
    }
}