package com.adonis.fluid.content.aqueduct;

import com.adonis.fluid.block.aqueduct.AbstractAqueductBlockEntity;
import com.adonis.fluid.block.aqueduct.AqueductBlockEntity;
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

    private int inputNodeIndex = -1;
    private int outputNodeIndex = -1;
    private int currentFillingIndex = -1;  // 当前正在填充的节点索引
    private boolean bridgeMode = false;

    private NetworkState state = NetworkState.IDLE;
    private int transferRate;
    private int tickCounter = 0;

    private final Map<BlockPos, AqueductNode> nodeMap = new HashMap<>();
    private final Set<BlockPos> controlledNodes = new HashSet<>();

    public AqueductNetwork(Level level, List<AqueductNode> nodes, Direction flowDirection) {
        this.level = level;
        this.nodes = new ArrayList<>(nodes);
        this.flowDirection = flowDirection;
        this.transferRate = CFCommonConfig.AQUEDUCT_TRANSFER_RATE.get();

        for (AqueductNode node : nodes) {
            nodeMap.put(node.getPos(), node);
        }

        identifySources();
    }

    private void identifySources() {
        inputNodeIndex = -1;
        outputNodeIndex = -1;

        for (int i = 0; i < nodes.size(); i++) {
            AqueductNode node = nodes.get(i);
            node.updateState();

            if (node.hasFluidSource()) {
                if (node.isInputSource() && inputNodeIndex == -1) {
                    inputNodeIndex = i;
                }
                if (node.isOutputSource() && outputNodeIndex == -1) {
                    outputNodeIndex = i;
                }
            }
        }
    }

    public void tick() {
        if (++tickCounter % 2 != 0) return;
        if (!isValid()) return;

        try {
            if (tickCounter % 20 == 0) {
                identifySources();
            }

            if (inputNodeIndex >= 0) {
                handlePumpDrivenFlow();
            } else {
                clearNetworkControl();
                executeSequentialFilling();
            }

        } catch (Exception e) {
            LOGGER.error("Error during network tick", e);
            invalidate();
        }
    }

    private void executeSequentialFilling() {
        // 普通顺序填充
        for (int i = 0; i < nodes.size() - 1; i++) {
            AqueductNode current = nodes.get(i);
            AqueductNode next = nodes.get(i + 1);

            if (current.hasFluid() && current.getFluidAmount() > 0 && !next.isFull()) {
                if (canTransfer(current, next)) {
                    transferBetweenNodes(current, next);
                    return;
                }
            }
        }
    }

    private void handlePumpDrivenFlow() {
        // 检查桥接模式
        if (outputNodeIndex >= 0 && checkBridgeMode()) {
            bridgeMode = true;
            // 桥接模式：标记所有节点为受控，停止内部传输
            markAllControlled();
            return;
        }

        bridgeMode = false;

        // 初始化当前填充索引
        if (currentFillingIndex == -1) {
            currentFillingIndex = inputNodeIndex;
        }

        // 确保当前填充节点已满
        if (currentFillingIndex < nodes.size()) {
            AqueductNode currentNode = nodes.get(currentFillingIndex);

            if (!currentNode.isFull()) {
                // 当前节点未满，继续填充
                // 标记从输入到当前节点的所有节点为受控
                markControlledRange(inputNodeIndex, currentFillingIndex);

                // 如果这不是输入节点，从前一个节点传输
                if (currentFillingIndex > inputNodeIndex) {
                    AqueductNode prevNode = nodes.get(currentFillingIndex - 1);
                    if (prevNode.hasFluid() && prevNode.isFull()) {
                        transferBetweenNodes(prevNode, currentNode);
                    }
                }
                return;
            }

            // 当前节点已满，移动到下一个
            currentFillingIndex++;

            // 如果还有节点要填充
            if (currentFillingIndex < nodes.size()) {
                // 标记从输入到当前位置的所有节点为受控
                markControlledRange(inputNodeIndex, currentFillingIndex);

                // 开始填充新节点
                AqueductNode newTarget = nodes.get(currentFillingIndex);
                AqueductNode sourceNode = nodes.get(currentFillingIndex - 1);

                if (!newTarget.isFull() && sourceNode.isFull()) {
                    transferBetweenNodes(sourceNode, newTarget);
                }
            } else {
                // 所有节点都满了
                markAllControlled();
            }
        }
    }

    private boolean checkBridgeMode() {
        if (inputNodeIndex < 0 || outputNodeIndex < 0) return false;

        for (int i = inputNodeIndex; i <= outputNodeIndex && i < nodes.size(); i++) {
            if (!nodes.get(i).isFull()) {
                return false;
            }
        }
        return true;
    }

    private void markControlledRange(int start, int end) {
        // 清除旧的控制
        clearNetworkControl();

        // 标记新的控制范围
        for (int i = start; i <= end && i < nodes.size(); i++) {
            BlockPos pos = nodes.get(i).getPos();
            controlledNodes.add(pos);

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AqueductBlockEntity aqueduct) {
                aqueduct.setNetworkControlled(true);
            }
        }
    }

    private void markAllControlled() {
        if (inputNodeIndex >= 0) {
            int end = outputNodeIndex >= 0 ? outputNodeIndex : nodes.size() - 1;
            markControlledRange(inputNodeIndex, end);
        }
    }

    private void clearNetworkControl() {
        for (BlockPos pos : controlledNodes) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AqueductBlockEntity aqueduct) {
                aqueduct.setNetworkControlled(false);
            }
        }
        controlledNodes.clear();
    }

    private boolean canTransfer(AqueductNode from, AqueductNode to) {
        if (from == null || to == null) return false;
        if (from.isLocked() || to.isLocked()) return false;
        if (!from.hasFluid() || from.getFluidAmount() <= 0) return false;
        if (to.isFull()) return false;

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

        int toTransfer = Math.min(
                Math.min(transferRate, fluid.getAmount()),
                toAqueduct.getSpace()
        );

        if (toTransfer <= 0) return;

        FluidStack transferStack = fluid.copy();
        transferStack.setAmount(toTransfer);

        IFluidHandler toHandler = toAqueduct.getCapability(
                        net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER, null)
                .orElse(null);

        if (toHandler != null) {
            int filled = toHandler.fill(transferStack, IFluidHandler.FluidAction.SIMULATE);
            if (filled > 0) {
                transferStack.setAmount(filled);
                fromAqueduct.getTank().drain(transferStack, IFluidHandler.FluidAction.EXECUTE);
                toHandler.fill(transferStack, IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    public boolean isNodeControlled(BlockPos pos) {
        return controlledNodes.contains(pos);
    }

    public boolean isBridgeMode() {
        return bridgeMode;
    }

    public void invalidate() {
        state = NetworkState.INVALID;
        clearNetworkControl();
        nodeMap.clear();
        currentFillingIndex = -1;
    }

    public boolean isValid() {
        return state != NetworkState.INVALID;
    }

    public List<BlockPos> getNodePositions() {
        List<BlockPos> positions = new ArrayList<>();
        for (AqueductNode node : nodes) {
            positions.add(node.getPos());
        }
        return positions;
    }

    public enum NetworkState {
        IDLE,
        ACTIVE,
        PAUSED,
        INVALID
    }
}