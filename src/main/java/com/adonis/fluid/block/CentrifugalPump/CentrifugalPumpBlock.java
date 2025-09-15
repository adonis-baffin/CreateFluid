package com.adonis.fluid.block.CentrifugalPump;

import com.adonis.fluid.registry.CFBlockEntity;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.ticks.TickPriority;

public class CentrifugalPumpBlock extends DirectionalAxisKineticBlock
        implements SimpleWaterloggedBlock, IBE<CentrifugalPumpBlockEntity> {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final EnumProperty<Orientation> ORIENTATION = EnumProperty.create("orientation", Orientation.class);

    public enum Orientation implements net.minecraft.util.StringRepresentable {
        HORIZONTAL("horizontal"),
        VERTICAL("vertical");

        private final String name;

        Orientation(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    // 基础组件形状（基于模型文件）
    private static final VoxelShape PUMP_CENTER = Block.box(2, 2, 2, 14, 14, 14);
    private static final VoxelShape PIPE_FRONT = Block.box(3, 3, 0, 13, 13, 2);
    private static final VoxelShape PIPE_UP = Block.box(3, 14, 3, 13, 16, 13);
    private static final VoxelShape BACK_FOR_ROTATE = Block.box(3, 3, 13, 13, 13, 15);
    private static final VoxelShape ADJUST_LEFT = Block.box(0, 4, 4, 2, 12, 12);
    private static final VoxelShape ADJUST_RIGHT = Block.box(14, 4, 4, 16, 12, 12);

    // 垂直模式组件
    private static final VoxelShape PIPE_DOWN = Block.box(3, 0, 3, 13, 2, 13);
    private static final VoxelShape PIPE_SIDE = Block.box(3, 3, 0, 13, 13, 2);
    private static final VoxelShape TOP_FOR_STRESS = Block.box(3, 14, 3, 13, 16, 13);

    // 预计算的碰撞箱
    private static final VoxelShape[] HORIZONTAL_SHAPES = new VoxelShape[4];
    private static final VoxelShape[] VERTICAL_SHAPES = new VoxelShape[4];

    static {
        // 水平模式 - 北（pipe_front朝北）
        HORIZONTAL_SHAPES[0] = Shapes.or(PUMP_CENTER, PIPE_FRONT, PIPE_UP, BACK_FOR_ROTATE, ADJUST_LEFT, ADJUST_RIGHT);

        // 水平模式 - 南（pipe_front朝南）
        HORIZONTAL_SHAPES[1] = Shapes.or(
                PUMP_CENTER,
                Block.box(3, 3, 14, 13, 13, 16),  // pipe_front朝南
                PIPE_UP,
                Block.box(3, 3, 0, 13, 13, 2),     // back_for_rotate朝北
                ADJUST_LEFT,
                ADJUST_RIGHT
        );

        // 水平模式 - 西（pipe_front朝西）
        HORIZONTAL_SHAPES[2] = Shapes.or(
                PUMP_CENTER,
                Block.box(0, 3, 3, 2, 13, 13),     // pipe_front朝西
                PIPE_UP,
                Block.box(14, 3, 3, 16, 13, 13),   // back_for_rotate朝东
                Block.box(4, 4, 0, 12, 12, 2),     // adjust朝北
                Block.box(4, 4, 14, 12, 12, 16)    // adjust朝南
        );

        // 水平模式 - 东（pipe_front朝东）
        HORIZONTAL_SHAPES[3] = Shapes.or(
                PUMP_CENTER,
                Block.box(14, 3, 3, 16, 13, 13),   // pipe_front朝东
                PIPE_UP,
                Block.box(0, 3, 3, 2, 13, 13),     // back_for_rotate朝西
                Block.box(4, 4, 0, 12, 12, 2),     // adjust朝北
                Block.box(4, 4, 14, 12, 12, 16)    // adjust朝南
        );

        // 垂直模式 - 北（pipe_up朝北）
        VERTICAL_SHAPES[0] = Shapes.or(
                PUMP_CENTER,
                PIPE_DOWN,                          // pipe_front向下
                PIPE_SIDE,                          // pipe_up朝北
                TOP_FOR_STRESS,                     // back_for_rotate向上
                ADJUST_LEFT,                         // 东西两侧调节
                ADJUST_RIGHT
        );

        // 垂直模式 - 南（pipe_up朝南）
        VERTICAL_SHAPES[1] = Shapes.or(
                PUMP_CENTER,
                PIPE_DOWN,
                Block.box(3, 3, 14, 13, 13, 16),   // pipe_up朝南
                TOP_FOR_STRESS,
                ADJUST_LEFT,
                ADJUST_RIGHT
        );

        // 垂直模式 - 西（pipe_up朝西）
        VERTICAL_SHAPES[2] = Shapes.or(
                PUMP_CENTER,
                PIPE_DOWN,
                Block.box(0, 3, 3, 2, 13, 13),     // pipe_up朝西
                TOP_FOR_STRESS,
                Block.box(4, 4, 0, 12, 12, 2),     // adjust朝北
                Block.box(4, 4, 14, 12, 12, 16)    // adjust朝南
        );

        // 垂直模式 - 东（pipe_up朝东）
        VERTICAL_SHAPES[3] = Shapes.or(
                PUMP_CENTER,
                PIPE_DOWN,
                Block.box(14, 3, 3, 16, 13, 13),   // pipe_up朝东
                TOP_FOR_STRESS,
                Block.box(4, 4, 0, 12, 12, 2),     // adjust朝北
                Block.box(4, 4, 14, 12, 12, 16)    // adjust朝南
        );
    }

    public CentrifugalPumpBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(WATERLOGGED, false)
                .setValue(ORIENTATION, Orientation.HORIZONTAL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED, ORIENTATION);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        Orientation orientation = state.getValue(ORIENTATION);
        Direction facing = state.getValue(FACING);

        if (orientation == Orientation.VERTICAL) {
            return VERTICAL_SHAPES[facing.get2DDataValue()];
        } else {
            return HORIZONTAL_SHAPES[facing.get2DDataValue()];
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        Direction clickedFace = context.getClickedFace();

        // 获取基础状态（从父类获取会设置初始的FACING）
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            state = this.defaultBlockState();
        }

        // 判断放置模式
        Orientation orientation;
        Direction facing;

        if (clickedFace.getAxis() == Direction.Axis.Y) {
            // 点击上下面，使用垂直模式
            orientation = Orientation.VERTICAL;
            // facing决定pipe_up的朝向，必须是水平方向
            facing = context.getHorizontalDirection().getOpposite();
        } else {
            // 点击侧面，使用水平模式
            orientation = Orientation.HORIZONTAL;
            // facing决定pipe_front的朝向
            facing = clickedFace;
            if (player != null && player.isShiftKeyDown()) {
                facing = facing.getOpposite();
            }
        }

        // 确保facing永远不是垂直方向
        if (facing.getAxis() == Direction.Axis.Y) {
            facing = Direction.NORTH;
        }

        state = state.setValue(FACING, facing).setValue(ORIENTATION, orientation);
        state = ProperWaterloggedBlock.withWater(level, state, pos);

        return state;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        // 旋转轴总是从back_for_rotate指向pipe_front
        Orientation orientation = state.getValue(ORIENTATION);
        if (orientation == Orientation.VERTICAL) {
            return Direction.Axis.Y;  // 垂直轴
        } else {
            return state.getValue(FACING).getAxis();  // 水平轴
        }
    }

    @Override
    public SpeedLevel getMinimumRequiredSpeedLevel() {
        return SpeedLevel.MEDIUM;
    }

    public boolean hasShaftTowards(LevelAccessor world, BlockPos pos, BlockState state, Direction face) {
        // 轴总是从back_for_rotate面进入
        return face == getShaftDirection(state);
    }

    /**
     * 获取应力输入方向（back_for_rotate面的方向）
     */
    public static Direction getShaftDirection(BlockState state) {
        Orientation orientation = state.getValue(ORIENTATION);
        if (orientation == Orientation.VERTICAL) {
            return Direction.UP;  // 垂直模式，轴从上方进入
        } else {
            return state.getValue(FACING).getOpposite();  // 水平模式，轴从pipe_front的对面进入
        }
    }

    /**
     * 获取主流体输出方向（pipe_front的方向）
     */
    public static Direction getPrimaryFluidDirection(BlockState state) {
        Orientation orientation = state.getValue(ORIENTATION);
        if (orientation == Orientation.VERTICAL) {
            return Direction.DOWN;  // 垂直模式，pipe_front固定向下
        } else {
            return state.getValue(FACING);  // 水平模式，pipe_front由facing决定
        }
    }

    /**
     * 获取次流体输出方向（pipe_up的方向）
     */
    public static Direction getSecondaryFluidDirection(BlockState state) {
        Orientation orientation = state.getValue(ORIENTATION);
        if (orientation == Orientation.VERTICAL) {
            return state.getValue(FACING);  // 垂直模式，pipe_up由facing决定
        } else {
            return Direction.UP;  // 水平模式，pipe_up固定向上
        }
    }

    public static boolean isOpenAt(BlockState state, Direction direction) {
        // 流体可以从pipe_front和pipe_up两个方向进出
        return direction == getPrimaryFluidDirection(state) ||
                direction == getSecondaryFluidDirection(state);
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block otherBlock,
                                BlockPos neighborPos, boolean isMoving) {
        super.neighborChanged(state, world, pos, otherBlock, neighborPos, isMoving);
        DebugPackets.sendNeighborsUpdatePacket(world, pos);

        Direction d = FluidPropagator.validateNeighbourChange(state, world, pos, otherBlock, neighborPos, isMoving);
        if (d != null && isOpenAt(state, d)) {
            world.scheduleTick(pos, this, 1, TickPriority.HIGH);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        FluidPropagator.propagateChangedPipe(world, pos, state);
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, world, pos, oldState, isMoving);
        if (!world.isClientSide && state != oldState) {
            world.scheduleTick(pos, this, 1, TickPriority.HIGH);
        }
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        boolean blockTypeChanged = !state.is(newState.getBlock());
        if (blockTypeChanged && !world.isClientSide) {
            FluidPropagator.propagateChangedPipe(world, pos, state);
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }
        return state;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter reader, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    public Class<CentrifugalPumpBlockEntity> getBlockEntityClass() {
        return CentrifugalPumpBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CentrifugalPumpBlockEntity> getBlockEntityType() {
        return CFBlockEntity.CENTRIFUGAL_PUMP.get();
    }
}