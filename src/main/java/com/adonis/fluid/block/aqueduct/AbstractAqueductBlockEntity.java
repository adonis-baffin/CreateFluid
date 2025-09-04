package com.adonis.fluid.block.aqueduct;

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

    protected boolean locked = false;
    protected int transferCooldown = 0;
    protected static final int CAPACITY = 1000;

    // 控制状态
    protected boolean isBeingFilled = false; // 是否正在被填充
    protected boolean hasInputPump = false; // 是否有输入泵
    protected boolean hasOutputPump = false; // 是否有输出泵

    // 初始化标记
    private boolean needsInitialSync = false;
    private int initialSyncDelay = 0;

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
            if (flowAnimation == null) {
                flowAnimation = new com.adonis.fluid.content.aqueduct.FluidFlowAnimation();
            }
            // 客户端立即更新流体级别
            float currentLevel = tank.getFluidAmount() / (float) tank.getCapacity();
            fluidLevel.startWithValue(currentLevel);
        } else {
            // 服务端：标记需要初始同步
            if (!tank.isEmpty()) {
                needsInitialSync = true;
                initialSyncDelay = 2; // 延迟2tick发送初始数据
            }
        }
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // 可以添加行为
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

            // 处理初始同步
            if (needsInitialSync && initialSyncDelay > 0) {
                initialSyncDelay--;
                if (initialSyncDelay == 0) {
                    sendData();
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                    needsInitialSync = false;
                }
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
        }
    }

    protected abstract void performTransfer();

    public Direction getFlowDirection() {
        return getBlockState().getValue(AbstractAqueductBlock.FACING);
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

        // 总是保存tank数据
        tag.put("Tank", tank.writeToNBT(new CompoundTag()));
        tag.putBoolean("Locked", locked);
        tag.putInt("TransferCooldown", transferCooldown);
        tag.putBoolean("IsBeingFilled", isBeingFilled);
        tag.putBoolean("HasInputPump", hasInputPump);
        tag.putBoolean("HasOutputPump", hasOutputPump);

        // 保存流体级别状态
        tag.put("FluidLevel", fluidLevel.writeNBT());

        if (clientPacket) {
            // 客户端数据包 - 确保发送完整数据
            tag.putFloat("CurrentLevel", getFluidLevel());
            if (!tank.isEmpty()) {
                tag.put("ClientFluid", tank.getFluid().writeToNBT(new CompoundTag()));
            }
        }
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);

        // 读取tank数据
        if (tag.contains("Tank")) {
            tank.readFromNBT(tag.getCompound("Tank"));
        }

        locked = tag.getBoolean("Locked");
        transferCooldown = tag.getInt("TransferCooldown");
        isBeingFilled = tag.getBoolean("IsBeingFilled");
        hasInputPump = tag.getBoolean("HasInputPump");
        hasOutputPump = tag.getBoolean("HasOutputPump");

        // 读取流体级别
        if (tag.contains("FluidLevel")) {
            fluidLevel.readNBT(tag.getCompound("FluidLevel"), clientPacket);
        }

        if (clientPacket) {
            // 客户端处理
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

            // 确保客户端动画初始化
            if (level != null && level.isClientSide && flowAnimation == null) {
                flowAnimation = new com.adonis.fluid.content.aqueduct.FluidFlowAnimation();
            }

            // 触发渲染更新
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
            }
        } else {
            // 服务端：加载后立即同步
            if (!tank.isEmpty()) {
                needsInitialSync = true;
                initialSyncDelay = 2;
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
                // 标记正在被填充
                AbstractAqueductBlockEntity.this.isBeingFilled = true;
            }
            return filled;
        }

        @Nonnull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (locked) return FluidStack.EMPTY;
            // 如果正在被填充且没有输出泵，不允许排出
            if (isBeingFilled && !hasOutputPump) return FluidStack.EMPTY;

            return AbstractAqueductBlockEntity.this.tank.drain(resource, action);
        }

        @Nonnull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (locked) return FluidStack.EMPTY;
            // 如果正在被填充且没有输出泵，不允许排出
            if (isBeingFilled && !hasOutputPump) return FluidStack.EMPTY;

            return AbstractAqueductBlockEntity.this.tank.drain(maxDrain, action);
        }
    }

    protected int getTransferRate() {
        return CFCommonConfig.AQUEDUCT_TRANSFER_RATE.get();
    }
}