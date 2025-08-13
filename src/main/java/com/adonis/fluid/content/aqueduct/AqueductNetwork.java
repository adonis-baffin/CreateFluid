package com.adonis.fluid.content.aqueduct;

import com.adonis.fluid.block.aqueduct.AbstractAqueductBlockEntity;
import com.adonis.fluid.config.CFCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.*;

public class AqueductNetwork {
    
    private final Level level;
    private final List<AqueductNode> nodes;
    private final Direction flowDirection;
    private final Set<BlockPos> inputSources;
    private final Set<BlockPos> outputSources;
    
    private NetworkState state = NetworkState.IDLE;
    private int transferRate;
    private int tickCounter = 0;
    
    public AqueductNetwork(Level level, List<AqueductNode> nodes, Direction flowDirection) {
        this.level = level;
        this.nodes = nodes;
        this.flowDirection = flowDirection;
        this.inputSources = new HashSet<>();
        this.outputSources = new HashSet<>();
        this.transferRate = CFCommonConfig.AQUEDUCT_TRANSFER_RATE.get();
        
        identifySources();
    }

    private void identifySources() {
        // 识别输入源和输出源
        for (AqueductNode node : nodes) {
            if (node.hasFluidSource()) {
                if (node.isInputSource()) {
                    inputSources.add(node.getPos());
                } else if (node.isOutputSource()) {
                    outputSources.add(node.getPos());
                }
            }
        }
    }

    public void tick() {
        if (++tickCounter % 2 != 0) return; // 每2tick执行一次传输
        
        if (isPaused()) return;
        
        // 根据不同情况执行传输
        if (hasInputAndOutput()) {
            executeBridgedTransfer();
        } else if (hasOnlyInput()) {
            executeSequentialFill();
        } else if (hasOnlyOutput()) {
            executeDrainage();
        } else {
            executePassiveFlow();
        }
    }

    public void requestTransfer(BlockPos from) {
        // 处理来自特定节点的传输请求
        AqueductNode node = findNode(from);
        if (node != null && !node.isLocked()) {
            executeNodeTransfer(node);
        }
    }

    private void executeBridgedTransfer() {
        // 检查中间节点是否全满
        boolean allMiddleFull = true;
        for (int i = 1; i < nodes.size() - 1; i++) {
            if (!nodes.get(i).isFull()) {
                allMiddleFull = false;
                break;
            }
        }
        
        if (allMiddleFull && !nodes.isEmpty()) {
            // 直接从输入源传输到输出源
            for (BlockPos inputPos : inputSources) {
                for (BlockPos outputPos : outputSources) {
                    transferBetween(inputPos, outputPos);
                }
            }
        } else {
            // 正常顺序填充
            executeSequentialFill();
        }
    }

    private void executeSequentialFill() {
        // 从输入源开始，顺序填充
        for (AqueductNode node : nodes) {
            if (node.isLocked()) continue;
            
            // 查找下一个可填充的节点
            int currentIndex = nodes.indexOf(node);
            if (currentIndex < nodes.size() - 1) {
                AqueductNode nextNode = nodes.get(currentIndex + 1);
                
                if (!nextNode.isLocked() && node.hasFluid() && !nextNode.isFull()) {
                    transferBetweenNodes(node, nextNode);
                }
            }
        }
    }

    private void executeDrainage() {
        // 向输出源排放
        for (BlockPos outputPos : outputSources) {
            AqueductNode outputNode = findNode(outputPos);
            if (outputNode != null && !outputNode.isLocked()) {
                // 查找最近的有流体的节点
                for (int i = nodes.size() - 1; i >= 0; i--) {
                    AqueductNode node = nodes.get(i);
                    if (node.hasFluid() && !node.isLocked()) {
                        transferBetweenNodes(node, outputNode);
                        break;
                    }
                }
            }
        }
    }

    private void executePassiveFlow() {
        // 被动流动：每个节点尝试向下游传输
        for (int i = 0; i < nodes.size() - 1; i++) {
            AqueductNode current = nodes.get(i);
            AqueductNode next = nodes.get(i + 1);
            
            if (!current.isLocked() && !next.isLocked() && 
                current.hasFluid() && !next.isFull()) {
                transferBetweenNodes(current, next);
            }
        }
    }

    private void transferBetweenNodes(AqueductNode from, AqueductNode to) {
        BlockEntity fromBE = level.getBlockEntity(from.getPos());
        BlockEntity toBE = level.getBlockEntity(to.getPos());
        
        if (fromBE instanceof AbstractAqueductBlockEntity && toBE instanceof AbstractAqueductBlockEntity) {
            AbstractAqueductBlockEntity fromAqueduct = (AbstractAqueductBlockEntity) fromBE;
            AbstractAqueductBlockEntity toAqueduct = (AbstractAqueductBlockEntity) toBE;
            
            FluidStack fluid = fromAqueduct.getFluid();
            if (fluid.isEmpty()) return;
            
            FluidStack toTransfer = fluid.copy();
            toTransfer.setAmount(Math.min(transferRate, fluid.getAmount()));
            
            // 模拟填充
            IFluidHandler toHandler = toAqueduct.getCapability(
                    net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
            if (toHandler == null) return;
            
            int filled = toHandler.fill(toTransfer, IFluidHandler.FluidAction.SIMULATE);
            if (filled > 0) {
                // 实际传输
                toTransfer.setAmount(filled);
                fromAqueduct.getTank().drain(toTransfer, IFluidHandler.FluidAction.EXECUTE);
                toHandler.fill(toTransfer, IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    private void transferBetween(BlockPos from, BlockPos to) {
        // 直接传输（用于桥接模式）
        transferBetweenNodes(findNode(from), findNode(to));
    }

    private AqueductNode findNode(BlockPos pos) {
        return nodes.stream()
                .filter(n -> n.getPos().equals(pos))
                .findFirst()
                .orElse(null);
    }

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

    private void executeNodeTransfer(AqueductNode node) {
        int index = nodes.indexOf(node);
        if (index >= 0 && index < nodes.size() - 1) {
            AqueductNode nextNode = nodes.get(index + 1);
            if (!nextNode.isLocked() && !nextNode.isFull()) {
                transferBetweenNodes(node, nextNode);
            }
        }
    }

    public void invalidate() {
        state = NetworkState.INVALID;
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