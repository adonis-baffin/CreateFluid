package com.adonis.fluid.content.aqueduct;

import com.adonis.fluid.block.aqueduct.AbstractAqueductBlock;
import com.adonis.fluid.config.CFCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class AqueductNetworkManager {
    
    private static AqueductNetworkManager instance;
    private final Map<Level, Map<BlockPos, AqueductNetwork>> networkCache = new WeakHashMap<>();
    private final Map<Level, Set<BlockPos>> dirtyPositions = new WeakHashMap<>();
    
    private AqueductNetworkManager() {}
    
    public static AqueductNetworkManager getInstance() {
        if (instance == null) {
            instance = new AqueductNetworkManager();
        }
        return instance;
    }

    public AqueductNetwork getOrCreateNetwork(Level level, BlockPos pos) {
        Map<BlockPos, AqueductNetwork> levelNetworks = networkCache.computeIfAbsent(level, k -> new HashMap<>());
        
        // 检查是否已有网络
        AqueductNetwork existing = levelNetworks.get(pos);
        if (existing != null && existing.isValid()) {
            return existing;
        }
        
        // 创建新网络
        return buildNetwork(level, pos);
    }

    private AqueductNetwork buildNetwork(Level level, BlockPos startPos) {
        BlockState startState = level.getBlockState(startPos);
        if (!(startState.getBlock() instanceof AbstractAqueductBlock)) {
            return null;
        }
        
        Direction flowDirection = startState.getValue(AbstractAqueductBlock.FACING);
        List<AqueductNode> nodes = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        
        // 向前搜索
        searchDirection(level, startPos, flowDirection, nodes, visited, true);
        
        // 向后搜索
        searchDirection(level, startPos, flowDirection.getOpposite(), nodes, visited, false);
        
        // 排序节点（按流向）
        nodes.sort((a, b) -> {
            if (flowDirection == Direction.NORTH || flowDirection == Direction.WEST) {
                return comparePositions(b.getPos(), a.getPos(), flowDirection);
            } else {
                return comparePositions(a.getPos(), b.getPos(), flowDirection);
            }
        });
        
        // 创建网络
        AqueductNetwork network = new AqueductNetwork(level, nodes, flowDirection);
        
        // 缓存网络
        Map<BlockPos, AqueductNetwork> levelNetworks = networkCache.computeIfAbsent(level, k -> new HashMap<>());
        for (AqueductNode node : nodes) {
            levelNetworks.put(node.getPos(), network);
        }
        
        return network;
    }

    private void searchDirection(Level level, BlockPos pos, Direction dir, 
                                List<AqueductNode> nodes, Set<BlockPos> visited, boolean forward) {
        BlockPos current = forward ? pos : pos.relative(dir);
        int maxLength = CFCommonConfig.MAX_AQUEDUCT_LENGTH.get();
        int length = 0;
        
        while (length < maxLength) {
            if (visited.contains(current)) break;
            
            BlockState state = level.getBlockState(current);
            if (!(state.getBlock() instanceof AbstractAqueductBlock)) break;
            
            Direction facing = state.getValue(AbstractAqueductBlock.FACING);
            if (facing != (forward ? dir : dir.getOpposite())) break;
            
            visited.add(current);
            
            // 创建节点
            AqueductNode.NodeType type = determineNodeType(state);
            AqueductNode node = new AqueductNode(level, current, type);
            
            if (forward) {
                nodes.add(node);
            } else {
                nodes.add(0, node);
            }
            
            current = current.relative(dir);
            length++;
        }
    }

    private AqueductNode.NodeType determineNodeType(BlockState state) {
        // 根据方块类型确定节点类型
        // 这里暂时都返回REGULAR，后续添加其他类型时再修改
        return AqueductNode.NodeType.REGULAR;
    }

    private int comparePositions(BlockPos a, BlockPos b, Direction flowDir) {
        switch (flowDir.getAxis()) {
            case X:
                return Integer.compare(a.getX(), b.getX());
            case Z:
                return Integer.compare(a.getZ(), b.getZ());
            default:
                return 0;
        }
    }

    public void markDirty(Level level, BlockPos pos) {
        dirtyPositions.computeIfAbsent(level, k -> new HashSet<>()).add(pos);
    }

    public void invalidateNetwork(Level level, BlockPos pos) {
        Map<BlockPos, AqueductNetwork> levelNetworks = networkCache.get(level);
        if (levelNetworks != null) {
            AqueductNetwork network = levelNetworks.get(pos);
            if (network != null) {
                network.invalidate();
                for (BlockPos nodePos : network.getNodePositions()) {
                    levelNetworks.remove(nodePos);
                }
            }
        }
    }

    public void tick(Level level) {
        // 处理脏位置
        Set<BlockPos> dirty = dirtyPositions.get(level);
        if (dirty != null && !dirty.isEmpty()) {
            for (BlockPos pos : dirty) {
                invalidateNetwork(level, pos);
            }
            dirty.clear();
        }
        
        // Tick所有网络
        Map<BlockPos, AqueductNetwork> levelNetworks = networkCache.get(level);
        if (levelNetworks != null) {
            Set<AqueductNetwork> networks = new HashSet<>(levelNetworks.values());
            for (AqueductNetwork network : networks) {
                if (network.isValid()) {
                    network.tick();
                }
            }
        }
    }
}