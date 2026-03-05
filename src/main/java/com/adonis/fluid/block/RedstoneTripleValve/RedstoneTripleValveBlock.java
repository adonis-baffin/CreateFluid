package com.adonis.fluid.block.RedstoneTripleValve;
import javax.annotation.Nonnull;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.IAxisPipe;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.adonis.fluid.registry.CFBlockEntity;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.TickPriority;
import java.util.EnumMap;
import java.util.Map;
public class RedstoneTripleValveBlock extends Block
        implements IAxisPipe, IBE<RedstoneTripleValveBlockEntity>, ProperWaterloggedBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty AXIS_ALONG_FIRST_COORDINATE =
            BooleanProperty.create("axis_along_first");
    /** false = 无信号，通正方向侧口；true = 有信号，通负方向侧口 */
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    // T型碰撞箱缓存
    private static final Map<Direction, Map<Axis, VoxelShape>> SHAPES = new EnumMap<>(Direction.class);
    static {
        for (Direction facing : Direction.values()) {
            Map<Axis, VoxelShape> axisMap = new EnumMap<>(Axis.class);
            for (Axis crossAxis : Axis.values()) {
                if (crossAxis == facing.getAxis())
                    continue;
                axisMap.put(crossAxis, buildTShape(facing, crossAxis));
            }
            SHAPES.put(facing, axisMap);
        }
    }
    private static VoxelShape buildTShape(Direction fixedDir, Axis crossAxis) {
        double min = 4, max = 12;
        // 固定端口管段（从中心到 fixedDir 方向的面）
        double fx1 = min, fy1 = min, fz1 = min;
        double fx2 = max, fy2 = max, fz2 = max;
        switch (fixedDir) {
            case DOWN  -> fy1 = 0;
            case UP    -> fy2 = 16;
            case NORTH -> fz1 = 0;
            case SOUTH -> fz2 = 16;
            case WEST  -> fx1 = 0;
            case EAST  -> fx2 = 16;
        }
        VoxelShape fixedPart = Block.box(fx1, fy1, fz1, fx2, fy2, fz2);
        // 横杆管段（沿 crossAxis 贯穿整个方块）
        double cx1 = min, cy1 = min, cz1 = min;
        double cx2 = max, cy2 = max, cz2 = max;
        switch (crossAxis) {
            case X -> { cx1 = 0; cx2 = 16; }
            case Y -> { cy1 = 0; cy2 = 16; }
            case Z -> { cz1 = 0; cz2 = 16; }
        }
        VoxelShape crossPart = Block.box(cx1, cy1, cz1, cx2, cy2, cz2);
        // 中心方块
        VoxelShape center = Block.box(2, 2, 2, 14, 14, 14);
        return Shapes.join(Shapes.join(fixedPart, crossPart, BooleanOp.OR), center, BooleanOp.OR);
    }
    public RedstoneTripleValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.UP)
                .setValue(AXIS_ALONG_FIRST_COORDINATE, false)
                .setValue(POWERED, false)
                .setValue(WATERLOGGED, false));
    }
    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(FACING, AXIS_ALONG_FIRST_COORDINATE, POWERED, WATERLOGGED);
    }
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos,
                               CollisionContext context) {
        Direction facing = state.getValue(FACING);
        Axis crossAxis = getCrossAxis(state);
        Map<Axis, VoxelShape> axisMap = SHAPES.get(facing);
        if (axisMap != null && axisMap.containsKey(crossAxis))
            return axisMap.get(crossAxis);
        return Shapes.block();
    }
    // ========== 朝向与轴逻辑 ==========
    @Nonnull
    public static Axis getCrossAxis(BlockState state) {
        if (!(state.getBlock() instanceof RedstoneTripleValveBlock))
            throw new IllegalStateException("Provided BlockState is for a different block.");
        Direction facing = state.getValue(FACING);
        boolean alongFirst = !state.getValue(AXIS_ALONG_FIRST_COORDINATE);
        for (Axis axis : Iterate.axes) {
            if (axis == facing.getAxis())
                continue;
            if (!alongFirst) {
                alongFirst = true;
                continue;
            }
            return axis;
        }
        throw new IllegalStateException("Impossible axis.");
    }
    @Override
    public Axis getAxis(BlockState state) {
        return getCrossAxis(state);
    }
    /**
     * 获取当前活跃的侧口方向。
     * 无信号(POWERED=false) → 横杆轴正方向；有信号(POWERED=true) → 横杆轴负方向。
     */
    public static Direction getActiveOutputDirection(BlockState state) {
        Axis crossAxis = getCrossAxis(state);
        boolean powered = state.getValue(POWERED);
        return Direction.get(powered ? AxisDirection.NEGATIVE : AxisDirection.POSITIVE, crossAxis);
    }
    public static Direction getFixedDirection(BlockState state) {
        return state.getValue(FACING);
    }
    /**
     * 检查某个方向是否为当前通流的端口（固定口 + 活跃侧口）。
     * 这个方法被 BlockEntity 的 canHaveFlowToward 调用，决定了流体逻辑连通性。
     */
    public static boolean isOpenAt(BlockState state, Direction d) {
        if (d == getFixedDirection(state))
            return true;
        if (d == getActiveOutputDirection(state))
            return true;
        return false;
    }
    /**
     * 检查某个方向是否为三通阀门的任意端口（三个中任一个）。
     * 这个方法被 neighborChanged 调用，决定了物理连接检测。
     */
    public static boolean isAnyPort(BlockState state, Direction d) {
        if (d == getFixedDirection(state))
            return true;
        return d.getAxis() == getCrossAxis(state);
    }
    // ========== 放置逻辑 ==========
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getNearestLookingDirection().getOpposite();
        BlockState state = defaultBlockState().setValue(FACING, facing);
        state = pickCorrectAxis(state, context);
        boolean powered = context.getLevel().hasNeighborSignal(context.getClickedPos());
        state = state.setValue(POWERED, powered);
        return withWater(state, context);
    }
    protected BlockState pickCorrectAxis(BlockState state, BlockPlaceContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        boolean originalValue = state.getValue(AXIS_ALONG_FIRST_COORDINATE);
        for (int i = 0; i < 2; i++) {
            state = state.setValue(AXIS_ALONG_FIRST_COORDINATE, i == 0);
            Axis crossAxis = getCrossAxis(state);
            Direction d1 = Direction.get(AxisDirection.POSITIVE, crossAxis);
            Direction d2 = Direction.get(AxisDirection.NEGATIVE, crossAxis);
            boolean connect1 = prefersConnectionTo(world, pos, d1);
            boolean connect2 = prefersConnectionTo(world, pos, d2);
            if (connect1 || connect2)
                return state;
        }
        return state.setValue(AXIS_ALONG_FIRST_COORDINATE, originalValue);
    }
    protected boolean prefersConnectionTo(LevelReader reader, BlockPos pos, Direction facing) {
        BlockPos offset = pos.relative(facing);
        BlockState blockState = reader.getBlockState(offset);
        return FluidPipeBlock.canConnectTo(reader, offset, blockState, facing);
    }
    // ========== 红石控制 ==========
    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block otherBlock,
                                BlockPos neighborPos, boolean isMoving) {
        if (world.isClientSide)
            return;
        DebugPackets.sendNeighborsUpdatePacket(world, pos);
        boolean powered = world.hasNeighborSignal(pos);
        boolean currentlyPowered = state.getValue(POWERED);
        if (currentlyPowered != powered) {
            BlockState newState = state.setValue(POWERED, powered);
            world.setBlockAndUpdate(pos, newState);
            // 切换时需要通知流体网络重建连接
            // propagateChangedPipe 会调用 wipePressure，wipePressure 会调用 canHaveFlowToward
            // 重新判断哪些方向有连接，从而实现切换
            FluidPropagator.propagateChangedPipe(world, pos, newState);
            return;
        }
        // 管道邻居变化检测（不用 validateNeighbourChange，自行判断）
        Direction changedDir = null;
        for (Direction d : Iterate.directions) {
            if (pos.relative(d).equals(neighborPos)) {
                changedDir = d;
                break;
            }
        }
        if (changedDir == null)
            return;
        // 只有当变化的是三个物理端口之一时，才触发更新
        if (!isAnyPort(state, changedDir))
            return;
        world.scheduleTick(pos, this, 1, TickPriority.HIGH);
    }
    // ========== 管道生命周期 ==========
    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        boolean blockTypeChanged = !state.is(newState.getBlock());
        if (blockTypeChanged && !world.isClientSide)
            FluidPropagator.propagateChangedPipe(world, pos, state);
        super.onRemove(state, world, pos, newState, isMoving);
    }
    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, world, pos, oldState, isMoving);
        if (world.isClientSide)
            return;
        if (state != oldState)
            world.scheduleTick(pos, this, 1, TickPriority.HIGH);
    }
    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource r) {
        FluidPropagator.propagateChangedPipe(world, pos, state);
    }
    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return true;
    }
    @Override
    public boolean isPathfindable(BlockState state, BlockGetter reader, BlockPos pos, PathComputationType type) {
        return false;
    }
    // ========== BlockEntity ==========
    @Override
    public Class<RedstoneTripleValveBlockEntity> getBlockEntityClass() {
        return RedstoneTripleValveBlockEntity.class;
    }
    @Override
    public BlockEntityType<? extends RedstoneTripleValveBlockEntity> getBlockEntityType() {
        return CFBlockEntity.REDSTONE_TRIPLE_VALVE.get();
    }
    // ========== 含水 ==========
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
                                  LevelAccessor world, BlockPos pos, BlockPos neighbourPos) {
        updateWater(world, state, pos);
        return state;
    }
    @Override
    public FluidState getFluidState(BlockState state) {
        return fluidState(state);
    }
}