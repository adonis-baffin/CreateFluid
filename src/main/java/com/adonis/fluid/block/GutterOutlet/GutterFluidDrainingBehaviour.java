package com.adonis.fluid.block.GutterOutlet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import com.simibubi.create.AllTags;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.simibubi.create.infrastructure.config.AllConfigs;

import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.BBHelper;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 集水器流体收集行为
 * 从集水器上方收集世界中的流体源方块
 * 仿照机械动力软管滑轮的 FluidDrainingBehaviour 实现
 *
 * 与软管滑轮的区别：
 * 1. 不需要应力驱动，被动收集
 * 2. 只向上搜索，不向下搜索
 * 3. 收集起点是方块上方
 */
public class GutterFluidDrainingBehaviour extends BlockEntityBehaviour {

    public static final BehaviourType<GutterFluidDrainingBehaviour> TYPE = new BehaviourType<>();

    // ============== 来自 FluidManipulationBehaviour 的字段 ==============

    protected BoundingBox affectedArea;
    protected BlockPos rootPos;
    protected boolean infinite;
    protected boolean counterpartActed;

    // 搜索相关
    protected static final int searchedPerTick = 1024;
    protected static final int validationTimerMin = 160;
    protected List<BlockPosEntry> frontier;
    protected Set<BlockPos> visited;
    protected int revalidateIn;

    // ============== 来自 FluidDrainingBehaviour 的字段 ==============

    protected Fluid fluid;

    // 执行队列
    protected Set<BlockPos> validationSet;
    protected PriorityQueue<BlockPosEntry> queue;
    protected boolean isValid;

    // 验证相关
    protected List<BlockPosEntry> validationFrontier;
    protected Set<BlockPos> validationVisited;
    protected Set<BlockPos> newValidationSet;

    // ============== 内部类 ==============

    public static record BlockPosEntry(BlockPos pos, int distance) {
    }

    public static class ChunkNotLoadedException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    protected enum FluidBlockType {
        NONE, SOURCE, FLOWING;
    }

    // ============== 构造函数 ==============

    public GutterFluidDrainingBehaviour(SmartBlockEntity be) {
        super(be);
        setValidationTimer();
        infinite = false;
        visited = new HashSet<>();
        frontier = new ArrayList<>();
        validationVisited = new HashSet<>();
        validationFrontier = new ArrayList<>();
        validationSet = new HashSet<>();
        newValidationSet = new HashSet<>();
        queue = new ObjectHeapPriorityQueue<>(this::comparePositions);
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    // ============== 配置方法 ==============

    protected int validationTimer() {
        int maxBlocks = maxBlocks();
        return maxBlocks < 0 ? validationTimerMin : Math.max(validationTimerMin, maxBlocks / searchedPerTick + 1);
    }

    protected int setValidationTimer() {
        return revalidateIn = validationTimer();
    }

    protected int setLongValidationTimer() {
        return revalidateIn = validationTimer() * 2;
    }

    protected int maxRange() {
        return AllConfigs.server().fluids.hosePulleyRange.get();
    }

    protected int maxBlocks() {
        return AllConfigs.server().fluids.hosePulleyBlockThreshold.get();
    }

    // ============== 公共接口 ==============

    /**
     * 获取收集起始位置（集水器正上方）
     */
    public BlockPos getRootPos() {
        return blockEntity.getBlockPos().above();
    }

    public boolean isInfinite() {
        return infinite;
    }

    public void counterpartActed() {
        counterpartActed = true;
    }

    /**
     * 检查是否有流体可收集
     */
    public boolean hasFluidToDrain() {
        return fluid != null && !isSearching() && (infinite || !queue.isEmpty());
    }

    /**
     * 获取当前流体类型
     */
    @Nullable
    public Fluid getFluid() {
        return fluid;
    }

    /**
     * 获取源方块数量
     */
    public int getSourceCount() {
        return queue.size();
    }

    protected boolean isSearching() {
        return !frontier.isEmpty();
    }

    /**
     * 供外部调用的搜索状态检查
     */
    public boolean isCurrentlySearching() {
        return isSearching();
    }

    public FluidStack getDrainableFluid(BlockPos rootPos) {
        return fluid == null || isSearching() || !pullNext(rootPos, true) ? FluidStack.EMPTY
                : new FluidStack(fluid, 1000);
    }

    // ============== 核心拉取逻辑 ==============

    public boolean pullNext(BlockPos root, boolean simulate) {
        if (!frontier.isEmpty())
            return false;
        if (!Objects.equals(root, rootPos)) {
            rebuildContext(root);
            return false;
        }

        if (counterpartActed) {
            counterpartActed = false;
            softReset(root);
            return false;
        }

        if (affectedArea == null)
            affectedArea = BoundingBox.fromCorners(root, root);

        Level world = getWorld();
        if (!queue.isEmpty() && !isValid) {
            rebuildContext(root);
            return false;
        }

        if (validationFrontier.isEmpty() && !queue.isEmpty() && !simulate && revalidateIn == 0)
            revalidate(root);

        if (infinite) {
            playEffect(world, root, fluid, true);
            return true;
        }

        while (!queue.isEmpty()) {
            // 不要在这里出队，这样我们可以在模拟时决定不出队一个有效的条目
            BlockPos currentPos = queue.first().pos();

            BlockState blockState = world.getBlockState(currentPos);
            BlockState emptied = blockState;
            Fluid fluid = Fluids.EMPTY;

            if (blockState.hasProperty(BlockStateProperties.WATERLOGGED)
                    && blockState.getValue(BlockStateProperties.WATERLOGGED)) {
                emptied = blockState.setValue(BlockStateProperties.WATERLOGGED, Boolean.valueOf(false));
                fluid = Fluids.WATER;
            } else if (blockState.getBlock() instanceof LiquidBlock flowingFluid) {
                emptied = Blocks.AIR.defaultBlockState();
                if (blockState.getValue(LiquidBlock.LEVEL) == 0)
                    fluid = flowingFluid.fluid;
                else {
                    affectedArea = BBHelper.encapsulate(affectedArea, BoundingBox.fromCorners(currentPos, currentPos));
                    if (!blockEntity.isVirtual())
                        world.setBlock(currentPos, emptied, 2 | 16);
                    queue.dequeue();
                    if (queue.isEmpty()) {
                        isValid = checkValid(world, rootPos);
                        reset();
                    }
                    continue;
                }
            } else if (blockState.getFluidState().getType() != Fluids.EMPTY
                    && blockState.getCollisionShape(world, currentPos, CollisionContext.empty()).isEmpty()) {
                fluid = blockState.getFluidState().getType();
                emptied = Blocks.AIR.defaultBlockState();
            }

            if (this.fluid == null)
                this.fluid = fluid;

            if (!this.fluid.isSame(fluid)) {
                queue.dequeue();
                if (queue.isEmpty()) {
                    isValid = checkValid(world, rootPos);
                    reset();
                }
                continue;
            }

            if (simulate)
                return true;

            playEffect(world, currentPos, fluid, true);

            if (!blockEntity.isVirtual()) {
                world.setBlock(currentPos, emptied, 2 | 16);

                BlockState stateAbove = world.getBlockState(currentPos.above());
                if (stateAbove.getFluidState().getType() == Fluids.EMPTY
                        && !stateAbove.canSurvive(world, currentPos.above()))
                    world.setBlock(currentPos.above(), Blocks.AIR.defaultBlockState(), 2 | 16);
            }
            affectedArea = BBHelper.encapsulate(affectedArea, currentPos);

            queue.dequeue();
            if (queue.isEmpty()) {
                isValid = checkValid(world, rootPos);
                reset();
            } else if (!validationSet.contains(currentPos)) {
                reset();
            }
            return true;
        }

        if (rootPos == null)
            return false;

        if (isValid)
            rebuildContext(root);

        return false;
    }

    // ============== 搜索和验证逻辑 ==============

    @Override
    public void tick() {
        super.tick();

        Level world = getWorld();
        if (world == null || world.isClientSide)
            return;

        BlockPos root = getRootPos();

        // 初始化或位置变化时重建上下文
        if (rootPos == null || !rootPos.equals(root)) {
            rebuildContext(root);
            return; // 下一tick继续搜索
        }

        // 更新isValid状态
        isValid = checkValid(world, rootPos);

        // 如果正在搜索，继续搜索
        if (!frontier.isEmpty()) {
            continueSearch();
            return;
        }

        // 如果正在验证，继续验证
        if (!validationFrontier.isEmpty()) {
            continueValidation();
            return;
        }

        // 如果队列为空但上方有流体，重新开始搜索
        if (queue.isEmpty() && isValid && revalidateIn <= 0) {
            frontier.add(new BlockPosEntry(root, 0));
            return;
        }

        if (revalidateIn > 0)
            revalidateIn--;
    }

    protected void softReset(BlockPos root) {
        queue.clear();
        validationSet.clear();
        newValidationSet.clear();
        validationFrontier.clear();
        validationVisited.clear();
        visited.clear();
        infinite = false;
        setValidationTimer();
        frontier.add(new BlockPosEntry(root, 0));
        blockEntity.sendData();
    }

    /**
     * 检查上方是否有有效的流体可以收集
     * 对于集水器，我们简化逻辑：只要上方有流体（源或流动）就认为有效
     */
    protected boolean checkValid(Level world, BlockPos root) {
        if (world == null) return false;
        if (!world.isLoaded(root)) return false;

        // 直接检查root位置是否有流体
        FluidBlockType type = canPullFluidsFrom(world.getBlockState(root), root);
        if (type == FluidBlockType.SOURCE)
            return true;

        // 如果是流动的流体，向上查找源
        if (type == FluidBlockType.FLOWING) {
            BlockPos currentPos = root;
            for (int i = 0; i < 256; i++) { // 防止无限循环
                // 检查周围是否有源
                for (Direction d : Iterate.directions) {
                    BlockPos side = currentPos.relative(d);
                    if (world.isLoaded(side) && canPullFluidsFrom(world.getBlockState(side), side) == FluidBlockType.SOURCE)
                        return true;
                }
                // 向上移动
                currentPos = currentPos.above();
                if (!world.isLoaded(currentPos)) break;
                FluidBlockType aboveType = canPullFluidsFrom(world.getBlockState(currentPos), currentPos);
                if (aboveType == FluidBlockType.NONE) break;
                if (aboveType == FluidBlockType.SOURCE) return true;
            }
        }

        return false;
    }

    protected FluidBlockType canPullFluidsFrom(BlockState blockState, BlockPos pos) {
        if (blockState.hasProperty(BlockStateProperties.WATERLOGGED)
                && blockState.getValue(BlockStateProperties.WATERLOGGED))
            return FluidBlockType.SOURCE;
        if (blockState.getBlock() instanceof LiquidBlock)
            return blockState.getValue(LiquidBlock.LEVEL) == 0 ? FluidBlockType.SOURCE : FluidBlockType.FLOWING;
        if (blockState.getFluidState().getType() != Fluids.EMPTY
                && blockState.getCollisionShape(getWorld(), pos, CollisionContext.empty()).isEmpty())
            return FluidBlockType.SOURCE;
        return FluidBlockType.NONE;
    }

    public void rebuildContext(BlockPos root) {
        // 先保存affectedArea用于reset
        BoundingBox oldArea = affectedArea;

        // 清空状态但不触发完整reset
        if (oldArea != null)
            scheduleUpdatesInAffectedArea();

        affectedArea = null;
        setValidationTimer();
        frontier.clear();
        visited.clear();
        infinite = false;
        fluid = null;
        queue.clear();
        validationSet.clear();
        newValidationSet.clear();
        validationFrontier.clear();
        validationVisited.clear();

        // 设置新的rootPos和affectedArea
        rootPos = root;
        affectedArea = BoundingBox.fromCorners(rootPos, rootPos);

        // 添加起始搜索点
        frontier.add(new BlockPosEntry(root, 0));

        blockEntity.sendData();
    }

    public void revalidate(BlockPos root) {
        validationFrontier.clear();
        validationVisited.clear();
        newValidationSet.clear();
        validationFrontier.add(new BlockPosEntry(root, 0));
        setValidationTimer();
    }

    private void continueSearch() {
        try {
            // 注意：这里传入 false 表示不向下搜索（只向上搜索）
            fluid = search(fluid, frontier, visited, (e, d) -> {
                queue.enqueue(new BlockPosEntry(e, d));
                validationSet.add(e);
            }, false);
        } catch (ChunkNotLoadedException e) {
            blockEntity.sendData();
            frontier.clear();
            visited.clear();
        }

        int maxBlocks = maxBlocks();
        if (visited.size() > maxBlocks && canDrainInfinitely(fluid) && !queue.isEmpty()) {
            infinite = true;
            BlockPos firstValid = queue.first().pos();
            frontier.clear();
            visited.clear();
            queue.clear();
            queue.enqueue(new BlockPosEntry(firstValid, 0));
            blockEntity.sendData();
            return;
        }

        if (!frontier.isEmpty())
            return;

        blockEntity.sendData();
        visited.clear();
    }

    private void continueValidation() {
        try {
            search(fluid, validationFrontier, validationVisited, (e, d) -> newValidationSet.add(e), false);
        } catch (ChunkNotLoadedException e) {
            validationFrontier.clear();
            validationVisited.clear();
            setLongValidationTimer();
            return;
        }

        int maxBlocks = maxBlocks();
        if (validationVisited.size() > maxBlocks && canDrainInfinitely(fluid)) {
            if (!infinite)
                reset();
            validationFrontier.clear();
            setLongValidationTimer();
            return;
        }

        if (!validationFrontier.isEmpty())
            return;
        if (infinite) {
            reset();
            return;
        }

        validationSet = newValidationSet;
        newValidationSet = new HashSet<>();
        validationVisited.clear();
    }

    // ============== 搜索算法 ==============

    /**
     * 搜索流体
     * @param fluid 当前流体类型（可为null）
     * @param frontier 搜索前沿
     * @param visited 已访问集合
     * @param add 添加找到的流体源的回调
     * @param searchDownward 是否向下搜索（集水器始终为false）
     * @return 找到的流体类型
     */
    protected Fluid search(Fluid fluid, List<BlockPosEntry> frontier, Set<BlockPos> visited,
                           BiConsumer<BlockPos, Integer> add, boolean searchDownward) throws ChunkNotLoadedException {
        Level world = getWorld();
        int maxBlocks = maxBlocks();
        int maxRange = maxRange();
        int maxRangeSq = maxRange * maxRange;
        int i;

        for (i = 0; i < searchedPerTick && !frontier.isEmpty()
                && (visited.size() <= maxBlocks || !canDrainInfinitely(fluid)); i++) {
            BlockPosEntry entry = frontier.remove(0);
            BlockPos currentPos = entry.pos();
            if (visited.contains(currentPos))
                continue;
            visited.add(currentPos);

            if (!world.isLoaded(currentPos))
                throw new ChunkNotLoadedException();

            FluidState fluidState = world.getFluidState(currentPos);
            if (fluidState.isEmpty())
                continue;

            Fluid currentFluid = FluidHelper.convertToStill(fluidState.getType());
            if (fluid == null)
                fluid = currentFluid;
            if (!currentFluid.isSame(fluid))
                continue;

            add.accept(currentPos, entry.distance());

            for (Direction side : Iterate.directions) {
                // 关键区别：集水器不向下搜索
                if (!searchDownward && side == Direction.DOWN)
                    continue;

                BlockPos offsetPos = currentPos.relative(side);
                if (!world.isLoaded(offsetPos))
                    throw new ChunkNotLoadedException();
                if (visited.contains(offsetPos))
                    continue;
                if (offsetPos.distSqr(rootPos) > maxRangeSq)
                    continue;

                FluidState nextFluidState = world.getFluidState(offsetPos);
                if (nextFluidState.isEmpty())
                    continue;
                Fluid nextFluid = nextFluidState.getType();
                if (nextFluid == FluidHelper.convertToFlowing(nextFluid) && side == Direction.UP
                        && !VecHelper.onSameAxis(rootPos, offsetPos, Axis.Y))
                    continue;

                frontier.add(new BlockPosEntry(offsetPos, entry.distance() + 1));
            }
        }

        return fluid;
    }

    protected int comparePositions(BlockPosEntry e1, BlockPosEntry e2) {
        Vec3 centerOfRoot = VecHelper.getCenterOf(rootPos);
        BlockPos pos2 = e2.pos();
        BlockPos pos1 = e1.pos();
        // 优先处理Y值高的（从上往下消耗）
        if (pos1.getY() != pos2.getY())
            return Integer.compare(pos2.getY(), pos1.getY());
        int compareDistance = Integer.compare(e2.distance(), e1.distance());
        if (compareDistance != 0)
            return compareDistance;
        return Double.compare(VecHelper.getCenterOf(pos2).distanceToSqr(centerOfRoot),
                VecHelper.getCenterOf(pos1).distanceToSqr(centerOfRoot));
    }

    protected boolean canDrainInfinitely(Fluid fluid) {
        if (fluid == null)
            return false;
        return maxBlocks() != -1 && AllConfigs.server().fluids.bottomlessFluidMode.get().test(fluid);
    }

    // ============== 重置 ==============

    public void reset() {
        if (affectedArea != null)
            scheduleUpdatesInAffectedArea();
        affectedArea = null;
        setValidationTimer();
        frontier.clear();
        visited.clear();
        infinite = false;

        fluid = null;
        // 注意：不清空rootPos，让tick中的逻辑来决定是否需要rebuildContext
        queue.clear();
        validationSet.clear();
        newValidationSet.clear();
        validationFrontier.clear();
        validationVisited.clear();
        blockEntity.sendData();
    }

    @Override
    public void destroy() {
        reset();
        super.destroy();
    }

    protected void scheduleUpdatesInAffectedArea() {
        Level world = getWorld();
        if (world == null || affectedArea == null) return;

        BlockPos.betweenClosedStream(
                        new BlockPos(affectedArea.minX() - 1, affectedArea.minY() - 1, affectedArea.minZ() - 1),
                        new BlockPos(affectedArea.maxX() + 1, affectedArea.maxY() + 1, affectedArea.maxZ() + 1))
                .forEach(pos -> {
                    FluidState nextFluidState = world.getFluidState(pos);
                    if (nextFluidState.isEmpty())
                        return;
                    world.scheduleTick(pos, nextFluidState.getType(), world.getRandom().nextInt(5));
                });
    }

    // ============== 效果 ==============

    protected void playEffect(Level world, BlockPos pos, Fluid fluid, boolean fillSound) {
        if (fluid == null)
            return;

        BlockPos splooshPos = pos == null ? blockEntity.getBlockPos() : pos;
        FluidStack stack = new FluidStack(fluid, 1);

        SoundEvent soundevent = fillSound ? FluidHelper.getFillSound(stack) : FluidHelper.getEmptySound(stack);
        world.playSound(null, splooshPos, soundevent, SoundSource.BLOCKS, 0.3F, 1.0F);

        // 可选：发送粒子效果包
        // if (world instanceof ServerLevel)
        //     AllPackets.sendToNear(world, splooshPos, 10, new FluidSplashPacket(splooshPos, stack));
    }

    // ============== NBT ==============

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(nbt, registries, clientPacket);
        infinite = nbt.contains("Infinite");
        if (nbt.contains("LastPos"))
            rootPos = NbtUtils.readBlockPos(nbt, "LastPos").orElse(null);
        if (nbt.contains("AffectedAreaFrom") && nbt.contains("AffectedAreaTo"))
            affectedArea = BoundingBox.fromCorners(
                    NbtUtils.readBlockPos(nbt, "AffectedAreaFrom").orElse(BlockPos.ZERO),
                    NbtUtils.readBlockPos(nbt, "AffectedAreaTo").orElse(BlockPos.ZERO));

        if (!clientPacket && affectedArea != null)
            frontier.add(new BlockPosEntry(rootPos, 0));
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(nbt, registries, clientPacket);
        if (infinite)
            NBTHelper.putMarker(nbt, "Infinite");
        if (rootPos != null)
            nbt.put("LastPos", NbtUtils.writeBlockPos(rootPos));
        if (affectedArea != null) {
            nbt.put("AffectedAreaFrom",
                    NbtUtils.writeBlockPos(new BlockPos(affectedArea.minX(), affectedArea.minY(), affectedArea.minZ())));
            nbt.put("AffectedAreaTo",
                    NbtUtils.writeBlockPos(new BlockPos(affectedArea.maxX(), affectedArea.maxY(), affectedArea.maxZ())));
        }
    }
}
