package com.adonis.fluid.block.CentrifugalPump;

import com.adonis.fluid.registry.CFBlockEntity;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.TickPriority;

public class CentrifugalPumpBlock extends DirectionalKineticBlock
        implements IBE<CentrifugalPumpBlockEntity>, SimpleWaterloggedBlock {

    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    // 临时的形状定义，避免崩溃
    private static final VoxelShape PUMP_SHAPE = Block.box(2, 2, 2, 14, 14, 14);

    public CentrifugalPumpBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACE, AttachFace.FLOOR)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACE, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState stateForPlacement = super.getStateForPlacement(ctx);
        if (stateForPlacement == null) {
            stateForPlacement = this.defaultBlockState();
        }

        Direction clickedFace = ctx.getClickedFace();
        AttachFace face;
        Direction facing = ctx.getHorizontalDirection().getOpposite(); // 默认朝向改为反方向

        // 根据点击的面确定AttachFace
        switch (clickedFace) {
            case UP:
                face = AttachFace.FLOOR;
                break;
            case DOWN:
                face = AttachFace.CEILING;
                break;
            default:
                face = AttachFace.WALL;
                // 对于墙面放置，facing应该就是点击的面本身
                // 因为在WALL模式下，facing决定了pipe_up的朝向
                // pipe_up朝向点击面（贴墙的反方向），base就在对面（贴墙）
                facing = clickedFace;
                break;
        }

        // 检查周围的管道连接，优先连接
        Direction.Axis prefferedAxis = null;
        BlockPos pos = ctx.getClickedPos();
        Level world = ctx.getLevel();

        for (Direction direction : Iterate.directions) {
            if (this.prefersConnectionTo(world, pos, direction)) {
                if (prefferedAxis != null && prefferedAxis != direction.getAxis()) {
                    prefferedAxis = null;
                    break;
                }
                prefferedAxis = direction.getAxis();
            }
        }

        // 根据优先连接调整朝向
        if (prefferedAxis == Direction.Axis.Y) {
            // 垂直轴优先，使用WALL模式
            face = AttachFace.WALL;
        } else if (prefferedAxis != null) {
            // 水平轴优先
            if (face == AttachFace.WALL) {
                face = AttachFace.FLOOR;
            }
            // 设置facing为优先连接的方向
            for (Direction d : Direction.values()) {
                if (d.getAxis() == prefferedAxis && d.getAxis() != Direction.Axis.Y) {
                    facing = d;
                    break;
                }
            }
        }

        stateForPlacement = stateForPlacement
                .setValue(FACE, face)
                .setValue(FACING, facing);

        return ProperWaterloggedBlock.withWater(world, stateForPlacement, pos);
    }

    protected boolean prefersConnectionTo(LevelReader reader, BlockPos pos, Direction facing) {
        BlockPos offset = pos.relative(facing);
        BlockState blockState = reader.getBlockState(offset);
        return FluidPipeBlock.canConnectTo(reader, offset, blockState, facing);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        // 安全的形状获取，避免返回null
        AttachFace face = state.getValue(FACE);
        Direction facing = state.getValue(FACING);

        // 尝试从CFShapes获取形状
        try {
            if (com.adonis.fluid.registry.CFShapes.CENTRIFUGAL_PUMP_FLOOR != null &&
                    com.adonis.fluid.registry.CFShapes.CENTRIFUGAL_PUMP_CEILING != null &&
                    com.adonis.fluid.registry.CFShapes.CENTRIFUGAL_PUMP_WALL != null) {

                VoxelShape shape = null;
                if (face == AttachFace.FLOOR) {
                    shape = com.adonis.fluid.registry.CFShapes.CENTRIFUGAL_PUMP_FLOOR.get(facing);
                } else if (face == AttachFace.CEILING) {
                    shape = com.adonis.fluid.registry.CFShapes.CENTRIFUGAL_PUMP_CEILING.get(facing);
                } else {
                    shape = com.adonis.fluid.registry.CFShapes.CENTRIFUGAL_PUMP_WALL.get(facing);
                }

                if (shape != null) {
                    return shape;
                }
            }
        } catch (Exception e) {
            // 如果出现任何错误，使用默认形状
        }

        // 返回默认形状，避免null
        return PUMP_SHAPE;
    }

    // ====== 应力系统关键方法 ======

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return getPumpAxis(state);
    }

    protected static Direction.Axis getPumpAxis(BlockState state) {
        AttachFace face = state.getValue(FACE);
        Direction facing = state.getValue(FACING);

        if (face == AttachFace.WALL) {
            // 垂直泵：轴是Y轴
            return Direction.Axis.Y;
        } else {
            // 水平泵：轴是facing的轴
            return facing.getAxis();
        }
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == getShaftDirection(state);
    }

    /**
     * 获取应力输入方向（传动杆连接的方向）
     */
    public static Direction getShaftDirection(BlockState state) {
        AttachFace face = state.getValue(FACE);
        Direction facing = state.getValue(FACING);

        if (face == AttachFace.WALL) {
            // 垂直泵：从上方接收动力
            return Direction.UP;
        } else {
            // 水平泵（FLOOR/CEILING）：从facing的反方向接收动力
            return facing.getOpposite();
        }
    }

    // ====== 流体系统方法 ======

    public static Direction getPrimaryFluidDirection(BlockState state) {
        // 添加安全检查
        if (!(state.getBlock() instanceof CentrifugalPumpBlock)) {
            return Direction.UP; // 返回默认值
        }

        // 确保state包含必要的属性
        if (!state.hasProperty(FACE) || !state.hasProperty(FACING)) {
            return Direction.UP; // 返回默认值
        }

        AttachFace face = state.getValue(FACE);
        Direction facing = state.getValue(FACING);

        if (face == AttachFace.WALL) {
            return Direction.DOWN;
        } else {
            return facing;
        }
    }

    public static Direction getSecondaryFluidDirection(BlockState state) {
        // 添加安全检查
        if (!(state.getBlock() instanceof CentrifugalPumpBlock)) {
            return Direction.NORTH; // 返回默认值
        }

        // 确保state包含必要的属性
        if (!state.hasProperty(FACE) || !state.hasProperty(FACING)) {
            return Direction.NORTH; // 返回默认值
        }

        AttachFace face = state.getValue(FACE);
        Direction facing = state.getValue(FACING);

        if (face == AttachFace.WALL) {
            return facing;
        } else if (face == AttachFace.FLOOR) {
            return Direction.UP;
        } else { // CEILING
            return Direction.DOWN;
        }
    }

    public static boolean isOpenAt(BlockState state, Direction d) {
        // 添加安全检查
        if (!(state.getBlock() instanceof CentrifugalPumpBlock)) {
            return false;
        }

        // 确保state包含必要的属性
        if (!state.hasProperty(FACE) || !state.hasProperty(FACING)) {
            return false;
        }

        return d == getPrimaryFluidDirection(state) || d == getSecondaryFluidDirection(state);
    }

    // ====== 流体传播相关 ======

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        boolean blockTypeChanged = !state.is(newState.getBlock());
        if (blockTypeChanged && !world.isClientSide) {
            FluidPropagator.propagateChangedPipe(world, pos, state);
        }
        IBE.onRemove(state, world, pos, newState);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return true;
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, world, pos, oldState, isMoving);

        if (!world.isClientSide) {
            // 立即触发网络更新
            if (state != oldState) {
                world.scheduleTick(pos, this, 1, TickPriority.HIGH);

                // 通知泵实体需要更新
                if (world.getBlockEntity(pos) instanceof CentrifugalPumpBlockEntity pump) {
                    pump.onPipeNetworkChanged();
                }
            }
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block otherBlock,
                                BlockPos neighborPos, boolean isMoving) {
        super.neighborChanged(state, world, pos, otherBlock, neighborPos, isMoving);

        DebugPackets.sendNeighborsUpdatePacket(world, pos);
        Direction d = FluidPropagator.validateNeighbourChange(state, world, pos, otherBlock, neighborPos, isMoving);

        if (d != null && isOpenAt(state, d)) {
            // 立即安排tick
            world.scheduleTick(pos, this, 1, TickPriority.HIGH);

            // 标记需要更新压力
            if (world.getBlockEntity(pos) instanceof CentrifugalPumpBlockEntity pump) {
                pump.pressureUpdate = true;
            }
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource r) {
        // 先传播管道变化
        FluidPropagator.propagateChangedPipe(world, pos, state);

        // 然后触发泵的压力更新
        if (world.getBlockEntity(pos) instanceof CentrifugalPumpBlockEntity pump) {
            pump.pressureUpdate = true;
        }
    }

    @Override
    public BlockState updateShape(BlockState pState, Direction pFacing, BlockState pFacingState,
                                  LevelAccessor pLevel, BlockPos pCurrentPos, BlockPos pFacingPos) {
        if (pState.getValue(WATERLOGGED)) {
            pLevel.scheduleTick(pCurrentPos, Fluids.WATER, Fluids.WATER.getTickDelay(pLevel));
        }
        return pState;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
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