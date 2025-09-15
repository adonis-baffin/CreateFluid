package com.adonis.fluid.block.Aqueduct;

import com.adonis.fluid.config.CFCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class AqueductBlockEntity extends AbstractAqueductBlockEntity {

    private static final Logger LOGGER = LoggerFactory.getLogger(AqueductBlockEntity.class);

    // 有源工作相关
    private BlockPos fluidSourcePos = null; // 流体源位置（储罐、水源等）
    private boolean hasFluidSource = false;
    private int sourceFillCooldown = 0;

    // 检查调度
    private static final Map<BlockPos, AqueductBlockEntity> allAqueducts = new HashMap<>();
    private static BlockPos checkingPos = null; // 当前正在检查的位置
    private int checkDelay = 0;

    public AqueductBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!level.isClientSide) {
            allAqueducts.put(worldPosition, this);
            triggerSystemRecheck();
        }
    }

    @Override
    public void onChunkUnloaded() {
        if (!level.isClientSide) {
            allAqueducts.remove(worldPosition);
        }
        super.onChunkUnloaded();
    }

    @Override
    public void invalidate() {
        if (!level.isClientSide) {
            allAqueducts.remove(worldPosition);
            clearWorkingState();

            // 清除伙伴的工作状态
            if (workingPartner != null) {
                BlockEntity partnerBE = level.getBlockEntity(workingPartner);
                if (partnerBE instanceof AqueductBlockEntity partner) {
                    partner.clearWorkingState();
                }
            }
        }
        super.invalidate();
    }

    @Override
    public void tick() {
        super.tick();

        if (level.isClientSide) return;

        // 初始化时检查流体源
        if (level.getGameTime() % 20 == 0) {
            checkFluidSource();
        }

        // 检查延迟
        if (checkDelay > 0) {
            checkDelay--;
            if (checkDelay == 0) {
                performCheck();
            }
        }

        // 有源工作：从流体源填充
        if (hasFluidSource && workingState == WorkingState.IDLE) {
            fillFromSource();
        }
    }

    // 当水渠被放置时调用
    public void onBlockPlaced() {
        if (!level.isClientSide) {
            checkFluidSource();
            triggerSystemRecheck();
        }
    }

    // 当水渠被移除时调用
    public void onBlockRemoved() {
        if (!level.isClientSide) {
            clearWorkingState();

            // 清除伙伴的工作状态
            if (workingPartner != null) {
                BlockEntity partnerBE = level.getBlockEntity(workingPartner);
                if (partnerBE instanceof AqueductBlockEntity partner) {
                    partner.clearWorkingState();
                }
            }

            triggerSystemRecheck();
        }
    }

    // 触发系统重新检查
    public static void triggerSystemRecheck() {
        // 取消所有正在进行的检查（但不打断传输）
        checkingPos = null;

        // 找到最上游的水渠开始
        AqueductBlockEntity upstreamMost = findUpstreamMost();
        if (upstreamMost != null) {
            upstreamMost.checkDelay = 1;
        }
    }

    // 查找最上游的水渠
    private static AqueductBlockEntity findUpstreamMost() {
        AqueductBlockEntity result = null;
        int minUpstreamDistance = Integer.MAX_VALUE;

        for (AqueductBlockEntity aqueduct : allAqueducts.values()) {
            int distance = calculateUpstreamDistance(aqueduct);
            if (distance < minUpstreamDistance) {
                minUpstreamDistance = distance;
                result = aqueduct;
            }
        }

        return result;
    }

    // 计算上游距离（用于排序）
    private static int calculateUpstreamDistance(AqueductBlockEntity aqueduct) {
        int distance = 0;
        Direction upstreamDir = aqueduct.getFlowDirection().getOpposite();
        BlockPos checkPos = aqueduct.worldPosition.relative(upstreamDir);

        // 向上游搜索，计算距离
        for (int i = 0; i < 100; i++) { // 防止无限循环
            BlockEntity be = aqueduct.level.getBlockEntity(checkPos);
            if (!(be instanceof AqueductBlockEntity upstream)) {
                break;
            }
            if (upstream.getFlowDirection() != aqueduct.getFlowDirection()) {
                break;
            }
            distance++;
            checkPos = checkPos.relative(upstreamDir);
        }

        return distance;
    }

    // 获取自己的上游距离
    private int getUpstreamDistance() {
        return calculateUpstreamDistance(this);
    }

    // 执行检查
    private void performCheck() {
        checkingPos = worldPosition;

        // 如果自己为空，退出
        if (tank.isEmpty()) {
            scheduleNextCheck();
            return;
        }

        // 如果已经在工作状态，退出
        if (workingState != WorkingState.IDLE) {
            scheduleNextCheck();
            return;
        }

        // 向下游检查
        Direction flowDir = getFlowDirection();
        BlockPos currentCheckPos = worldPosition;

        for (int i = 0; i < CFCommonConfig.MAX_AQUEDUCT_LENGTH.get(); i++) {
            currentCheckPos = currentCheckPos.relative(flowDir);
            BlockEntity be = level.getBlockEntity(currentCheckPos);

            // 如果不是水渠，结束检查
            if (!(be instanceof AqueductBlockEntity target)) {
                break;
            }

            // 如果方向不同，结束检查
            if (target.getFlowDirection() != flowDir) {
                break;
            }

            // 如果目标是其他种类流体，退出
            if (!target.tank.isEmpty() && !target.tank.getFluid().isFluidEqual(tank.getFluid())) {
                break;
            }

            // 如果目标处于工作状态，退出
            if (target.workingState != WorkingState.IDLE) {
                break;
            }

            // 如果目标为空或未满，锁定并进入结对工作
            if (target.canBeTarget(tank.getFluid())) {
                // 进入结对工作状态
                this.setAsSource(currentCheckPos);
                target.setAsTarget(worldPosition);
                LOGGER.debug("水渠 {} 与 {} 进入结对工作状态", worldPosition, currentCheckPos);
                break;
            }

            // 如果目标满了且流体相同，继续检查下一个
            if (target.canSkip(tank.getFluid())) {
                continue;
            }

            // 其他情况退出
            break;
        }

        scheduleNextCheck();
    }

    // 调度下一个检查
    private void scheduleNextCheck() {
        checkingPos = null;

        // 找到下一个需要检查的水渠
        AqueductBlockEntity next = findNextToCheck();
        if (next != null) {
            next.checkDelay = 1;
        }
    }

    // 查找下一个需要检查的水渠
    private AqueductBlockEntity findNextToCheck() {
        // 按照从上游到下游的顺序
        List<AqueductBlockEntity> sorted = new ArrayList<>(allAqueducts.values());
        sorted.sort(Comparator.comparingInt(this::calculateUpstreamDistanceForSort));

        boolean foundCurrent = false;
        for (AqueductBlockEntity aqueduct : sorted) {
            if (foundCurrent) {
                return aqueduct;
            }
            if (aqueduct.worldPosition.equals(worldPosition)) {
                foundCurrent = true;
            }
        }

        // 如果没有找到下一个，从头开始
        return sorted.isEmpty() ? null : sorted.get(0);
    }

    // 用于排序的上游距离计算
    private int calculateUpstreamDistanceForSort(AqueductBlockEntity aqueduct) {
        return calculateUpstreamDistance(aqueduct);
    }

    // 检查流体源
    public void checkFluidSource() {
        Direction upstreamDir = getFlowDirection().getOpposite();
        BlockPos sourcePos = worldPosition.relative(upstreamDir);

        // 检查水源方块
        if (level.getBlockState(sourcePos).getBlock() == Fluids.WATER.defaultFluidState().createLegacyBlock().getBlock()) {
            hasFluidSource = true;
            fluidSourcePos = sourcePos;
            return;
        }

        // 检查流体容器（包括Create的流体储罐等）
        BlockEntity be = level.getBlockEntity(sourcePos);
        if (be != null) {
            // 尝试从对应方向获取流体处理能力
            LazyOptional<IFluidHandler> cap = be.getCapability(ForgeCapabilities.FLUID_HANDLER, getFlowDirection());
            if (!cap.isPresent()) {
                // 如果特定方向没有，尝试获取通用的
                cap = be.getCapability(ForgeCapabilities.FLUID_HANDLER, null);
            }

            cap.ifPresent(handler -> {
                // 检查是否有流体
                for (int i = 0; i < handler.getTanks(); i++) {
                    if (!handler.getFluidInTank(i).isEmpty()) {
                        hasFluidSource = true;
                        fluidSourcePos = sourcePos;
                        return;
                    }
                }
            });
        }

        // 如果之前有源但现在没有了，清除状态
        if (hasFluidSource && (fluidSourcePos == null || !fluidSourcePos.equals(sourcePos))) {
            hasFluidSource = false;
            fluidSourcePos = null;
        }
    }

    // 从源填充
    private void fillFromSource() {
        if (sourceFillCooldown > 0) {
            sourceFillCooldown--;
            return;
        }

        if (fluidSourcePos == null) {
            checkFluidSource();
            if (!hasFluidSource) return;
        }

        Direction upstreamDir = getFlowDirection().getOpposite();

        // 水源方块
        if (level.getBlockState(fluidSourcePos).getBlock() == Fluids.WATER.defaultFluidState().createLegacyBlock().getBlock()) {
            if (isFull()) {
                // 自己满了，当作虚拟源头执行检查
                performVirtualSourceCheck();
                return;
            }

            if (!tank.getFluid().isEmpty() && tank.getFluid().getFluid() != Fluids.WATER) {
                return;
            }

            int fillAmount = Math.min(CFCommonConfig.WATER_SOURCE_FILL_RATE.get(), getSpace());
            FluidStack water = new FluidStack(Fluids.WATER, fillAmount);
            tank.fill(water, IFluidHandler.FluidAction.EXECUTE);
            sourceFillCooldown = 2;
            return;
        }

        // 流体容器
        BlockEntity be = level.getBlockEntity(fluidSourcePos);
        if (be != null) {
            // 获取流体处理能力
            LazyOptional<IFluidHandler> cap = be.getCapability(ForgeCapabilities.FLUID_HANDLER, getFlowDirection());
            if (!cap.isPresent()) {
                cap = be.getCapability(ForgeCapabilities.FLUID_HANDLER, null);
            }

            cap.ifPresent(handler -> {
                // 虚拟源头检查
                if (isFull()) {
                    performVirtualSourceCheck();
                    return;
                }

                // 从容器抽取流体
                FluidStack drain = handler.drain(getTransferRate(), IFluidHandler.FluidAction.SIMULATE);
                if (!drain.isEmpty()) {
                    if (!tank.isEmpty() && !tank.getFluid().isFluidEqual(drain)) return;

                    int toDrain = Math.min(drain.getAmount(), getSpace());
                    FluidStack actualDrain = handler.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
                    if (!actualDrain.isEmpty()) {
                        tank.fill(actualDrain, IFluidHandler.FluidAction.EXECUTE);
                        sourceFillCooldown = 2;
                    }
                }
            });
        }
    }

    // 作为虚拟源头执行检查
    private void performVirtualSourceCheck() {
        if (workingState != WorkingState.IDLE) return;

        Direction flowDir = getFlowDirection();
        BlockPos currentCheckPos = worldPosition;

        // 模拟从源容器检查
        for (int i = 0; i < CFCommonConfig.MAX_AQUEDUCT_LENGTH.get(); i++) {
            currentCheckPos = currentCheckPos.relative(flowDir);
            BlockEntity be = level.getBlockEntity(currentCheckPos);

            if (!(be instanceof AqueductBlockEntity target)) {
                break;
            }

            if (target.getFlowDirection() != flowDir) {
                break;
            }

            // 如果目标是其他种类流体或处于工作状态，退出
            if ((!target.tank.isEmpty() && !target.tank.getFluid().isFluidEqual(tank.getFluid()))
                    || target.workingState != WorkingState.IDLE) {
                break;
            }

            // 如果目标为空或未满，让源直接填充它
            if (target.canBeTarget(tank.getFluid())) {
                // 标记为虚拟源工作
                this.setAsSource(currentCheckPos);
                target.setAsTarget(worldPosition);
                LOGGER.debug("虚拟源 {} 开始填充 {}", worldPosition, currentCheckPos);
                break;
            }

            // 如果目标满了，继续检查
            if (target.canSkip(tank.getFluid())) {
                continue;
            }

            break;
        }
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);

        tag.putBoolean("HasFluidSource", hasFluidSource);
        if (fluidSourcePos != null) {
            tag.putLong("FluidSourcePos", fluidSourcePos.asLong());
        }
        tag.putInt("SourceFillCooldown", sourceFillCooldown);
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);

        hasFluidSource = tag.getBoolean("HasFluidSource");
        if (tag.contains("FluidSourcePos")) {
            fluidSourcePos = BlockPos.of(tag.getLong("FluidSourcePos"));
        }
        sourceFillCooldown = tag.getInt("SourceFillCooldown");
    }
}