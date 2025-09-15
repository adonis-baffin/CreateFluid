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
import net.minecraft.world.ticks.TickPriority;

public class CentrifugalPumpBlock extends DirectionalAxisKineticBlock
        implements SimpleWaterloggedBlock, IBE<CentrifugalPumpBlockEntity> {

    // 移除重复的 FACING 定义！父类已经有了
    // public static final DirectionProperty FACING = BlockStateProperties.FACING;  <-- 删除这行

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    // 用于定义泵的垂直/水平模式
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

    // 简化的形状定义
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

    public CentrifugalPumpBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(WATERLOGGED, false)
                .setValue(ORIENTATION, Orientation.HORIZONTAL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // 不要重复添加 FACING，父类会处理
        builder.add(WATERLOGGED, ORIENTATION);
        super.createBlockStateDefinition(builder);  // 父类会添加 FACING
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        Direction facing = context.getNearestLookingDirection();

        if (player != null && player.isShiftKeyDown()) {
            facing = facing.getOpposite();
        }

        // 根据放置方向决定朝向模式
        Orientation orientation = facing.getAxis() == Direction.Axis.Y
                ? Orientation.VERTICAL
                : Orientation.HORIZONTAL;

        // 处理垂直模式
        if (orientation == Orientation.VERTICAL) {
            if (facing == Direction.UP) {
                // 应力从下方输入，流体从上方输出
                facing = Direction.DOWN;
            } else {
                // 应力从上方输入，流体从下方输出
                facing = Direction.UP;
            }
            // 获取水平朝向
            Direction horizontalFacing = context.getHorizontalDirection().getOpposite();
            state = state.setValue(FACING, horizontalFacing);
        } else {
            // 水平模式
            state = state.setValue(FACING, facing.getOpposite());
        }

        state = state.setValue(ORIENTATION, orientation);
        state = ProperWaterloggedBlock.withWater(level, state, pos);

        // 智能连接到附近的管道
        Direction bestConnection = findBestPipeConnection(level, pos, orientation,
                orientation == Orientation.VERTICAL ? state.getValue(FACING) : facing.getOpposite());
        if (bestConnection != null) {
            state = state.setValue(FACING, bestConnection);
        }

        return state;
    }

    private Direction findBestPipeConnection(Level level, BlockPos pos, Orientation orientation, Direction preferredDir) {
        Direction bestDir = null;
        double bestDistance = Double.MAX_VALUE;

        for (Direction dir : Iterate.directions) {
            // 根据朝向模式检查有效连接方向
            if (orientation == Orientation.VERTICAL) {
                if (dir.getAxis() == Direction.Axis.Y) continue; // 垂直模式不检查上下
            } else {
                if (dir.getAxis() != Direction.Axis.Y && dir.getAxis() == preferredDir.getAxis()) continue;
            }

            BlockPos adjPos = pos.relative(dir);
            BlockState adjState = level.getBlockState(adjPos);

            if (FluidPipeBlock.canConnectTo(level, adjPos, adjState, dir)) {
                double distance = Vec3.atLowerCornerOf(dir.getNormal())
                        .distanceTo(Vec3.atLowerCornerOf(preferredDir.getNormal()));
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestDir = dir;
                }
            }
        }

        return bestDir;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        Orientation orientation = state.getValue(ORIENTATION);
        if (orientation == Orientation.VERTICAL) {
            return Direction.Axis.Y;
        } else {
            return state.getValue(FACING).getAxis();
        }
    }

    @Override
    public SpeedLevel getMinimumRequiredSpeedLevel() {
        return SpeedLevel.MEDIUM;
    }

    public boolean hasShaftTowards(LevelAccessor world, BlockPos pos, BlockState state, Direction face) {
        Orientation orientation = state.getValue(ORIENTATION);
        if (orientation == Orientation.VERTICAL) {
            // 垂直模式：轴从上方进入
            return face == Direction.UP;
        } else {
            // 水平模式：轴从后方进入
            return face == state.getValue(FACING).getOpposite();
        }
    }

    public static boolean isOpenAt(BlockState state, Direction direction) {
        Orientation orientation = state.getValue(ORIENTATION);
        if (orientation == Orientation.VERTICAL) {
            // 垂直模式：下方和侧面（根据facing）开放
            return direction == Direction.DOWN || direction == state.getValue(FACING);
        } else {
            // 水平模式：前方和上方开放
            Direction facing = state.getValue(FACING);
            return direction == facing || direction == Direction.UP;
        }
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
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        return SHAPE;
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