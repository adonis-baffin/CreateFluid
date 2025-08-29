package com.adonis.fluid.content.aqueduct;

import com.adonis.fluid.block.aqueduct.AbstractAqueductBlockEntity;
import com.adonis.fluid.config.CFCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;

public class AqueductNetwork {

    private static final Logger LOGGER = LoggerFactory.getLogger(AqueductNetwork.class);

    private final Level level;
    private final List<AqueductNode> nodes;
    private final Direction flowDirection;
    private final Set<BlockPos> inputSources;
    private final Set<BlockPos> outputSources;

    private NetworkState state = NetworkState.IDLE;
    private int transferRate;
    private int tickCounter = 0;

    // 桥接模式标志
    private boolean bridgeMode = false;
    private boolean allMiddleFull = false;

    // 缓存
    private final Map<BlockPos, AqueductNode> nodeMap = new HashMap<>();

    public AqueductNetwork(Level level, List<AqueductNode> nodes, Direction flowDirection) {
        this.level = level;
        this.nodes = new ArrayList<>(nodes);
        this.flowDirection = flowDirection;
        this.inputSources = new HashSet<>();
        this.outputSources = new HashSet<>();
        this.transferRate = CFCommonConfig.AQUEDUCT_TRANSFER_RATE.get();

        for (AqueductNode node : nodes) {
            nodeMap.put(node.getPos(), node);
        }

        identifySources();
    }

    private void identifySources() {
        // 更新所有节点的状态
        for (AqueductNode node : nodes) {
            node.updateState();
        }

        // 清空旧的源
        inputSources.clear();
        outputSources.clear();

        // 识别输入和输出源
        for (int i = 0; i < nodes.size(); i++) {
            AqueductNode node = nodes.get(i);

            if (node.hasFluidSource()) {
                if (node.isInputSource()) {
                    inputSources.add(node.getPos());
                    LOGGER.debug("Found input source at {}", node.getPos());
                } else if (node.isOutputSource()) {
                    outputSources.add(node.getPos());
                    LOGGER.debug("Found output source at {}", node.getPos());
                }
            }
        }
    }

    public void tick() {
        if (++tickCounter % 2 != 0) return;

        if (isPaused() || !isValid()) return;

        try {
            // 每20tick重新识别源（1秒）
            if (tickCounter % 20 == 0) {
                identifySources();
            }

            updateBridgeMode();

            if (hasInputAndOutput()) {
                if (bridgeMode) {
                    executeBridgedTransfer();
                } else {
                    executeNormalTransfer();
                }
            } else if (hasOnlyInput()) {
                executeInputOnlyTransfer();
            } else if (hasOnlyOutput()) {
                executeOutputOnlyTransfer();
            } else {
                executePassiveFlow();
            }
        } catch (Exception e) {
            LOGGER.error("Error during network tick", e);
            invalidate();
        }
    }

    private void updateBridgeMode() {
        if (!hasInputAndOutput()) {
            bridgeMode = false;
            allMiddleFull = false;
            return;
        }

        // 检查所有节点（不只是中间节点）是否都满了
        boolean checkFull = true;
        for (AqueductNode node : nodes) {
            if (!node.isFull()) {
                checkFull = false;
                break;
            }
        }

        allMiddleFull = checkFull;
        bridgeMode = allMiddleFull;

        if (bridgeMode) {
            LOGGER.debug("Bridge mode activated for network");
        }
    }

    private void executeBridgedTransfer() {
        // 桥接模式：直接从外部输入源传输到外部输出源
        // 这里不是从水渠传输，而是让外部源直接交互

        // 由于水渠已满，外部输入源应该直接向外部输出源传输
        // 但这需要外部系统（如Create的管道）自己处理
        // 水渠网络只是作为通道

        // 保持水渠满状态
        for (AqueductNode node : nodes) {
            maintainFullState(node);
        }
    }

    private void executeNormalTransfer() {
        // 正常传输：从输入填充到输出

        // 1. 从外部输入源填充第一个节点
        for (BlockPos inputPos : inputSources) {
            AqueductNode inputNode = nodeMap.get(inputPos);
            if (inputNode != null && !inputNode.isFull()) {
                // 这个节点会自己从外部源获取流体（通过其capability）
            }
        }

        // 2. 顺序传输
        for (int i = 0; i < nodes.size() - 1; i++) {
            AqueductNode current = nodes.get(i);
            AqueductNode next = nodes.get(i + 1);

            if (canTransfer(current, next)) {
                transferBetweenNodes(current, next);
            }
        }

        // 3. 输出到外部
        for (BlockPos outputPos : outputSources) {
            AqueductNode outputNode = nodeMap.get(outputPos);
            if (outputNode != null && outputNode.hasFluid()) {
                // 这个节点会自己向外部槽排放流体（通过其capability）
            }
        }
    }

    private void executeInputOnlyTransfer() {
        // 只有输入：填充所有节点
        for (int i = 0; i < nodes.size() - 1; i++) {
            AqueductNode current = nodes.get(i);
            AqueductNode next = nodes.get(i + 1);

            if (canTransfer(current, next)) {
                transferBetweenNodes(current, next);
            }
        }
    }

    private void executeOutputOnlyTransfer() {
        // 只有输出：从最后的有流体节点开始排放
        for (int i = nodes.size() - 1; i > 0; i--) {
            AqueductNode current = nodes.get(i);
            AqueductNode prev = nodes.get(i - 1);

            if (canTransfer(prev, current)) {
                transferBetweenNodes(prev, current);
            }
        }
    }

    private void executePassiveFlow() {
        // 被动流动：每个节点尝试向下游传输
        for (int i = 0; i < nodes.size() - 1; i++) {
            AqueductNode current = nodes.get(i);
            AqueductNode next = nodes.get(i + 1);

            if (canTransfer(current, next)) {
                transferBetweenNodes(current, next);
            }
        }
    }

    private void maintainFullState(AqueductNode node) {
        BlockEntity be = level.getBlockEntity(node.getPos());
        if (!(be instanceof AbstractAqueductBlockEntity)) return;

        AbstractAqueductBlockEntity aqueduct = (AbstractAqueductBlockEntity) be;

        // 如果不满，尝试补充
        if (!node.isFull()) {
            FluidStack currentFluid = aqueduct.getFluid();
            if (!currentFluid.isEmpty()) {
                int toFill = aqueduct.getSpace();
                if (toFill > 0) {
                    FluidStack fillStack = currentFluid.copy();
                    fillStack.setAmount(toFill);
                    aqueduct.getTank().fill(fillStack, IFluidHandler.FluidAction.EXECUTE);
                }
            }
        }
    }

    private boolean canTransfer(AqueductNode from, AqueductNode to) {
        if (from == null || to == null) return false;
        if (from.isLocked() || to.isLocked()) return false;
        if (!from.hasFluid()) return false;
        if (to.isFull()) return false;

        // 检查流体兼容性
        FluidStack fromFluid = from.getFluid();
        FluidStack toFluid = to.getFluid();

        if (toFluid.isEmpty()) return true;
        return fromFluid.isFluidEqual(toFluid);
    }

    private void transferBetweenNodes(@Nullable AqueductNode from, @Nullable AqueductNode to) {
        if (from == null || to == null) return;

        BlockEntity fromBE = level.getBlockEntity(from.getPos());
        BlockEntity toBE = level.getBlockEntity(to.getPos());

        if (!(fromBE instanceof AbstractAqueductBlockEntity) ||
                !(toBE instanceof AbstractAqueductBlockEntity)) {
            return;
        }

        AbstractAqueductBlockEntity fromAqueduct = (AbstractAqueductBlockEntity) fromBE;
        AbstractAqueductBlockEntity toAqueduct = (AbstractAqueductBlockEntity) toBE;

        FluidStack fluid = fromAqueduct.getFluid();
        if (fluid.isEmpty()) return;

        // 计算传输量
        int toTransfer = Math.min(
                Math.min(transferRate, fluid.getAmount()),
                toAqueduct.getSpace()
        );

        if (toTransfer <= 0) return;

        FluidStack transferStack = fluid.copy();
        transferStack.setAmount(toTransfer);

        // 检查目标是否可以接收
        IFluidHandler toHandler = toAqueduct.getCapability(
                        net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER, null)
                .orElse(null);

        if (toHandler != null) {
            int filled = toHandler.fill(transferStack, IFluidHandler.FluidAction.SIMULATE);
            if (filled > 0) {
                transferStack.setAmount(filled);
                fromAqueduct.getTank().drain(transferStack, IFluidHandler.FluidAction.EXECUTE);
                toHandler.fill(transferStack, IFluidHandler.FluidAction.EXECUTE);

                LOGGER.trace("Transferred {} mB from {} to {}",
                        filled, from.getPos(), to.getPos());
            }
        }
    }

    // Getter methods
    private boolean hasInputAndOutput() {
        return !inputSources.isEmpty() && !outputSources.isEmpty();
    }

    private boolean hasOnlyInput() {
        return !inputSources.isEmpty() && outputSources.isEmpty();
    }

    private boolean hasOnlyOutput() {
        return inputSources.isEmpty() && !outputSources.isEmpty();
    }

    private boolean isPaused() {
        return state == NetworkState.PAUSED;
    }

    public void invalidate() {
        state = NetworkState.INVALID;
        nodeMap.clear();
    }

    public boolean isValid() {
        return state != NetworkState.INVALID;
    }

    public List<BlockPos> getNodePositions() {
        List<BlockPos> positions = new ArrayList<>(nodes.size());
        for (AqueductNode node : nodes) {
            positions.add(node.getPos());
        }
        return positions;
    }

    public boolean isBridgeMode() {
        return bridgeMode;
    }

    public enum NetworkState {
        IDLE,
        ACTIVE,
        PAUSED,
        INVALID
    }
}