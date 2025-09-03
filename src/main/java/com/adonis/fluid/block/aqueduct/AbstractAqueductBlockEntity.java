package com.adonis.fluid.block.aqueduct;

import com.adonis.fluid.config.CFCommonConfig;
import com.adonis.fluid.content.aqueduct.AqueductBehaviour;
import com.adonis.fluid.content.aqueduct.AqueductPropagator;
import com.adonis.fluid.content.aqueduct.FluidFlowAnimation;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.createmod.catnip.animation.LerpedFloat;
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
    protected LerpedFloat fluidLevel;

    @OnlyIn(Dist.CLIENT)
    protected FluidFlowAnimation flowAnimation;

    protected boolean locked = false;
    public int transferCooldown = 0;
    protected static final int CAPACITY = 1000;

    private Direction cachedFlowDirection = null;
    private boolean flowDirectionDirty = true;
    private boolean needsSync = false;

    // 延迟同步机制
    private int syncDelayTicks = -1;
    private static final int INITIAL_SYNC_DELAY = 5; // 延迟5个tick进行初始同步

    public AbstractAqueductBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);

        tank = new SmartFluidTank(CAPACITY, this::onFluidStackChanged);
        fluidCapability = LazyOptional.of(() -> new AqueductFluidHandler());
        fluidLevel = LerpedFloat.linear().startWithValue(0);
    }

    @Override
    public void onLoad() {
        super.onLoad();

        if (level.isClientSide) {
            // 客户端初始化
            if (flowAnimation == null) {
                flowAnimation = new FluidFlowAnimation();
            }
        } else {
            // 服务端：设置延迟同步
            if (!tank.isEmpty()) {
                syncDelayTicks = INITIAL_SYNC_DELAY;
                needsSync = true;
            }
        }
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        aqueductBehaviour = new AqueductBehaviour(this);
        behaviours.add(aqueductBehaviour);
    }

    protected void onFluidStackChanged(FluidStack newFluid) {
        if (!level.isClientSide) {
            float targetLevel = tank.getFluidAmount() / (float) tank.getCapacity();
            fluidLevel.chase(targetLevel, 0.25, LerpedFloat.Chaser.LINEAR);

            setChanged();
            sendData();
            needsSync = false;
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

            // 处理延迟同步
            if (syncDelayTicks > 0) {
                syncDelayTicks--;
                if (syncDelayTicks == 0 && !tank.isEmpty()) {
                    // 执行延迟同步
                    sendData();
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                    needsSync = false;
                }
            }

            // 常规同步检查
            if (needsSync && syncDelayTicks <= 0) {
                sendData();
                needsSync = false;
            }
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

        // 保存tank内容
        tag.put("Tank", tank.writeToNBT(new CompoundTag()));
        tag.putBoolean("Locked", locked);
        tag.putInt("TransferCooldown", transferCooldown);

        // 保存流体动画状态
        tag.put("FluidLevel", fluidLevel.writeNBT());

        // 客户端数据包 - 总是发送完整数据
        if (clientPacket) {
            tag.putFloat("CurrentLevel", getFluidLevel());
            if (!tank.isEmpty()) {
                tag.put("ClientFluid", tank.getFluid().writeToNBT(new CompoundTag()));
            }
        }
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);

        // 读取tank内容
        if (tag.contains("Tank")) {
            FluidStack oldFluid = tank.getFluid().copy();
            tank.readFromNBT(tag.getCompound("Tank"));

            // 无论客户端还是服务端，如果流体改变都更新动画级别
            if (!oldFluid.isFluidEqual(tank.getFluid()) ||
                    oldFluid.getAmount() != tank.getFluidAmount()) {
                float newLevel = tank.getFluidAmount() / (float) tank.getCapacity();
                fluidLevel.startWithValue(newLevel);

                // 服务端触发更新
                if (!level.isClientSide) {
                    onFluidStackChanged(tank.getFluid());
                }
            }
        }

        locked = tag.getBoolean("Locked");
        transferCooldown = tag.getInt("TransferCooldown");

        // 读取流体动画状态
        if (tag.contains("FluidLevel")) {
            fluidLevel.readNBT(tag.getCompound("FluidLevel"), clientPacket);
        }

        // 客户端处理
        if (clientPacket) {
            // 读取客户端流体数据
            if (tag.contains("ClientFluid")) {
                FluidStack clientFluid = FluidStack.loadFluidStackFromNBT(tag.getCompound("ClientFluid"));
                if (!clientFluid.isEmpty()) {
                    // 检查流体是否改变
                    boolean fluidChanged = !clientFluid.isFluidEqual(tank.getFluid()) ||
                            clientFluid.getAmount() != tank.getFluidAmount();
                    tank.setFluid(clientFluid);

                    // 更新流体级别
                    float newLevel = clientFluid.getAmount() / (float) tank.getCapacity();
                    fluidLevel.startWithValue(newLevel);

                    // 如果流体改变了，请求渲染更新
                    if (fluidChanged && this.level != null && this.level.isClientSide) {
                        requestModelDataUpdate();
                        // 触发重新渲染
                        this.level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
                    }
                }
            } else if (tag.contains("CurrentLevel")) {
                // 如果没有流体数据但有液位，说明tank被清空了
                float currentLevel = tag.getFloat("CurrentLevel");
                if (currentLevel == 0 && !tank.isEmpty()) {
                    tank.setFluid(FluidStack.EMPTY);
                    fluidLevel.startWithValue(0);
                    if (this.level != null && this.level.isClientSide) {
                        requestModelDataUpdate();
                        this.level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
                    }
                } else {
                    // 更新液位动画
                    fluidLevel.startWithValue(currentLevel);
                }
            }

            // 确保客户端动画初始化
            if (this.level != null && this.level.isClientSide && flowAnimation == null) {
                flowAnimation = new FluidFlowAnimation();
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