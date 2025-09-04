package com.adonis.fluid.block.aqueduct;

import com.adonis.fluid.config.CFCommonConfig;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;

public class AqueductBlockEntity extends AbstractAqueductBlockEntity {

    private boolean hasWaterSource = false;
    private int waterFillCooldown = 0;

    // 泵控制的填充链
    private List<BlockPos> fillingChain = new ArrayList<>();
    private int currentFillingIndex = -1;
    private boolean bridgeMode = false; // 桥接模式
    private BlockPos bridgeOutputPos = null; // 桥接输出位置

    public AqueductBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();

        if (level.isClientSide) return;

        // 每20 tick检查一次泵连接
        if (level.getGameTime() % 20 == 0) {
            checkPumpConnections();
        }

        // 水源方块自动填充
        if (hasWaterSource && !locked && !isBeingFilled) {
            fillFromWaterSource();
        }

        // 执行传输逻辑
        if (transferCooldown == 0 && !locked) {
            performTransfer();
        }

        // 重置被填充状态
        if (isBeingFilled && level.getGameTime() % 10 == 0) {
            if (!hasActiveInput()) {
                isBeingFilled = false;
            }
        }
    }

    private void checkPumpConnections() {
        Direction flowDir = getFlowDirection();

        // 检查输入端
        BlockPos inputPos = worldPosition.relative(flowDir.getOpposite());
        hasInputPump = checkForPump(inputPos, flowDir);

        // 检查输出端
        BlockPos outputPos = worldPosition.relative(flowDir);
        hasOutputPump = checkForPump(outputPos, flowDir.getOpposite());

        // 如果有输入泵，建立填充链
        if (hasInputPump) {
            buildFillingChain();
        } else if (!hasInputPump && !fillingChain.isEmpty()) {
            clearFillingChain();
        }
    }

    private boolean checkForPump(BlockPos pos, Direction expectedFacing) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof PumpBlock) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof PumpBlockEntity pump) {
                Direction pumpFacing = state.getValue(PumpBlock.FACING);
                return pumpFacing == expectedFacing && pump.getSpeed() != 0;
            }
        }
        return false;
    }

    private boolean hasActiveInput() {
        return hasInputPump || (transferCooldown > 0);
    }

    private void buildFillingChain() {
        fillingChain.clear();
        bridgeMode = false;
        bridgeOutputPos = null;
        currentFillingIndex = 0;

        Direction flowDir = getFlowDirection();
        BlockPos current = worldPosition;
        int maxLength = CFCommonConfig.MAX_AQUEDUCT_LENGTH.get();

        // 添加自己
        fillingChain.add(current);

        // 向前搜索
        for (int i = 0; i < maxLength; i++) {
            current = current.relative(flowDir);
            BlockEntity be = level.getBlockEntity(current);

            if (!(be instanceof AqueductBlockEntity next)) {
                // 检查是否是输出泵
                if (checkForPump(current, flowDir.getOpposite())) {
                    bridgeOutputPos = current;
                }
                break;
            }

            // 检查方向
            if (next.getFlowDirection() != flowDir) {
                break;
            }

            fillingChain.add(current);

            // 如果下一个有输出泵，记录桥接输出位置
            if (next.hasOutputPump) {
                bridgeOutputPos = current.relative(flowDir);
                break;
            }
        }
    }

    private void clearFillingChain() {
        for (BlockPos pos : fillingChain) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AqueductBlockEntity aqueduct) {
                aqueduct.isBeingFilled = false;
            }
        }
        fillingChain.clear();
        currentFillingIndex = -1;
        bridgeMode = false;
        bridgeOutputPos = null;
    }

    @Override
    protected void performTransfer() {
        // 如果是填充链的起点，执行链式填充
        if (hasInputPump && !fillingChain.isEmpty()) {
            performChainFilling();
        } else if (!isBeingFilled) {
            // 否则执行普通传输（不满也能传输）
            performNormalTransfer();
        }
    }

    private void performChainFilling() {
        // 检查是否可以进入桥接模式
        if (bridgeOutputPos != null && checkBridgeMode()) {
            bridgeMode = true;
            // 桥接模式下，输入泵直接向输出设备传输
            // 这里不做实际传输，让泵自己处理
            return;
        }

        bridgeMode = false;

        // 正常的链式填充
        if (currentFillingIndex >= fillingChain.size() - 1) {
            return;
        }

        BlockPos currentPos = fillingChain.get(currentFillingIndex);
        BlockEntity currentBE = level.getBlockEntity(currentPos);

        if (!(currentBE instanceof AqueductBlockEntity current)) {
            return;
        }

        // 如果当前已满，移到下一个
        if (current.isFull()) {
            currentFillingIndex++;

            if (currentFillingIndex < fillingChain.size()) {
                BlockPos nextPos = fillingChain.get(currentFillingIndex);
                BlockEntity nextBE = level.getBlockEntity(nextPos);
                if (nextBE instanceof AqueductBlockEntity next) {
                    next.isBeingFilled = true;
                }
            }
        }

        // 从前一个传输到当前
        if (currentFillingIndex > 0 && currentFillingIndex < fillingChain.size()) {
            BlockPos prevPos = fillingChain.get(currentFillingIndex - 1);
            BlockPos currPos = fillingChain.get(currentFillingIndex);
            transferBetweenAqueducts(prevPos, currPos);
        }
    }

    private boolean checkBridgeMode() {
        // 检查链上所有水渠是否都满
        for (BlockPos pos : fillingChain) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AqueductBlockEntity aqueduct) {
                if (!aqueduct.isFull()) {
                    return false;
                }
            }
        }
        return true;
    }

    private void performNormalTransfer() {
        // 不满也能传输（只要有流体）
        if (tank.getFluidAmount() <= 0) {
            return;
        }

        Direction flowDir = getFlowDirection();
        BlockPos targetPos = worldPosition.relative(flowDir);

        transferBetweenAqueducts(worldPosition, targetPos);
    }

    private void transferBetweenAqueducts(BlockPos fromPos, BlockPos toPos) {
        BlockEntity fromBE = level.getBlockEntity(fromPos);
        BlockEntity toBE = level.getBlockEntity(toPos);

        if (!(fromBE instanceof AbstractAqueductBlockEntity from) ||
                !(toBE instanceof AbstractAqueductBlockEntity to)) {
            return;
        }

        if (from.tank.getFluidAmount() <= 0 || to.getSpace() <= 0) {
            return;
        }

        FluidStack fluid = from.getFluid();
        if (!to.tank.isEmpty() && !to.tank.getFluid().isFluidEqual(fluid)) {
            return;
        }

        int transfer = Math.min(
                Math.min(getTransferRate(), fluid.getAmount()),
                to.getSpace()
        );

        FluidStack transferStack = fluid.copy();
        transferStack.setAmount(transfer);

        from.tank.drain(transferStack, IFluidHandler.FluidAction.EXECUTE);
        to.tank.fill(transferStack, IFluidHandler.FluidAction.EXECUTE);

        if (to instanceof AqueductBlockEntity targetAqueduct) {
            targetAqueduct.isBeingFilled = true;
        }
    }

    private void fillFromWaterSource() {
        if (waterFillCooldown > 0) {
            waterFillCooldown--;
            return;
        }

        Direction inputDir = getFlowDirection().getOpposite();
        BlockPos sourcePos = worldPosition.relative(inputDir);

        if (level.getBlockState(sourcePos).getBlock() != Fluids.WATER.defaultFluidState().createLegacyBlock().getBlock()) {
            hasWaterSource = false;
            return;
        }

        if (tank.getFluidAmount() >= tank.getCapacity()) {
            return;
        }

        if (!tank.getFluid().isEmpty() && tank.getFluid().getFluid() != Fluids.WATER) {
            return;
        }

        int fillAmount = Math.min(CFCommonConfig.WATER_SOURCE_FILL_RATE.get(), tank.getSpace());
        FluidStack water = new FluidStack(Fluids.WATER, fillAmount);
        tank.fill(water, IFluidHandler.FluidAction.EXECUTE);

        waterFillCooldown = 2;
    }

    public boolean isBridgeMode() {
        return bridgeMode;
    }

    public BlockPos getBridgeOutputPos() {
        return bridgeOutputPos;
    }

    public void setHasWaterSource(boolean hasWaterSource) {
        this.hasWaterSource = hasWaterSource;
        setChanged();
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.putBoolean("HasWaterSource", hasWaterSource);
        tag.putInt("WaterFillCooldown", waterFillCooldown);
        tag.putBoolean("BridgeMode", bridgeMode);

        if (!clientPacket) {
            tag.putInt("ChainSize", fillingChain.size());
            tag.putInt("CurrentFillingIndex", currentFillingIndex);
        }
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        hasWaterSource = tag.getBoolean("HasWaterSource");
        waterFillCooldown = tag.getInt("WaterFillCooldown");
        bridgeMode = tag.getBoolean("BridgeMode");

        if (!clientPacket) {
            currentFillingIndex = tag.getInt("CurrentFillingIndex");
        }
    }
}