package com.adonis.fluid.block.CentrifugalPump;

import com.adonis.fluid.registry.CFBlockEntity;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.kinetics.base.AbstractEncasedShaftBlock;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.AbstractShaftBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
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
        implements IBE<CentrifugalPumpBlockEntity>, SimpleWaterloggedBlock, IWrenchable {

    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

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
        Level world = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();

        // 潜行模式：让主要接口对着玩家点击的面
        if (ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown()) {
            Direction clickedFace = ctx.getClickedFace();
            AttachFace face;
            Direction facing;

            switch (clickedFace) {
                case UP:
                    // 点击顶部（对着天花板）：pipefront无法朝上，让pipe_up朝上，pipefront朝玩家
                    face = AttachFace.FLOOR;
                    facing = ctx.getHorizontalDirection().getOpposite(); // pipefront朝向玩家
                    break;

                case DOWN:
                    // 点击底部（对着地板）：pipefront朝下，pipe_up朝玩家
                    face = AttachFace.WALL;
                    facing = ctx.getHorizontalDirection(); // pipe_up(secondary)朝玩家
                    break;

                default:
                    // 点击侧面（东南西北）：pipefront朝那个面，pipe_up朝上
                    face = AttachFace.FLOOR;
                    facing = clickedFace.getOpposite(); // pipefront朝向点击的面
                    break;
            }

            return this.defaultBlockState()
                    .setValue(FACE, face)
                    .setValue(FACING, facing)
                    .setValue(WATERLOGGED, ctx.getLevel().getFluidState(pos).getType() == Fluids.WATER);
        }

        // 非潜行模式：智能放置
        // 1. 检查各个方向的管道和应力连接
        boolean hasPipeUp = hasFluidConnection(world, pos.above(), pos);
        boolean hasPipeDown = hasFluidConnection(world, pos.below(), pos);
        boolean hasKineticUp = hasKineticConnection(world, pos.above(), world.getBlockState(pos.above()), Direction.DOWN);
        boolean hasKineticDown = hasKineticConnection(world, pos.below(), world.getBlockState(pos.below()), Direction.UP);

        // 收集水平方向的管道和应力连接
        Direction firstHorizontalPipe = null;
        Direction firstHorizontalKinetic = null;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = world.getBlockState(neighborPos);

            if (firstHorizontalPipe == null && hasFluidConnection(world, neighborPos, pos)) {
                firstHorizontalPipe = dir;
            }

            if (firstHorizontalKinetic == null && hasKineticConnection(world, neighborPos, neighborState, dir.getOpposite())) {
                firstHorizontalKinetic = dir;
            }
        }

        AttachFace face;
        Direction facing;

        // 2. 根据优先级决定放置方式
        if (hasPipeUp) {
            // 上方有管道 -> FLOOR模式
            face = AttachFace.FLOOR;
            // pipefront指向第一个有管道的水平方向，没有则朝向玩家
            facing = firstHorizontalPipe != null ? firstHorizontalPipe : ctx.getHorizontalDirection().getOpposite();

        } else if (hasPipeDown) {
            // 下方有管道
            if (hasKineticUp) {
                // 上方有应力 -> WALL模式
                face = AttachFace.WALL;
                // pipe_up指向第一个有管道的水平方向，没有则朝向玩家
                facing = firstHorizontalPipe != null ? firstHorizontalPipe : ctx.getHorizontalDirection();
            } else {
                // 上方无应力 -> CEILING模式
                face = AttachFace.CEILING;
                // pipefront指向第一个有管道的水平方向，没有则朝向玩家
                facing = firstHorizontalPipe != null ? firstHorizontalPipe : ctx.getHorizontalDirection().getOpposite();
            }

        } else if (firstHorizontalPipe != null) {
            // 上下都没管道，但水平方向有管道
            if (hasKineticUp) {
                // 上方有应力 -> WALL模式
                face = AttachFace.WALL;
                facing = firstHorizontalPipe; // pipe_up朝向有管道的方向
            } else {
                // 上方无应力 -> FLOOR模式
                face = AttachFace.FLOOR;
                facing = firstHorizontalPipe; // pipefront朝向有管道的方向
            }

        } else {
            // 没有任何管道连接，检查应力
            if (firstHorizontalKinetic != null) {
                // 水平方向有应力 -> FLOOR模式，应力相接
                face = AttachFace.FLOOR;
                facing = firstHorizontalKinetic.getOpposite(); // back_for_rotate朝向应力源

            } else if (hasKineticUp) {
                // 只有上方有应力 -> WALL模式
                face = AttachFace.WALL;
                // 没有管道连接，facing朝向玩家
                facing = ctx.getHorizontalDirection();

            } else {
                // 完全没有连接，使用默认逻辑
                face = clickedFaceToAttachFace(ctx.getClickedFace());
                if (face == AttachFace.WALL) {
                    // 对着墙面放置时，pipe_up应该朝外，这样base（pipe_up的对面）会贴着墙
                    Direction clickedFace = ctx.getClickedFace();
                    if (clickedFace.getAxis() != Direction.Axis.Y) {
                        // 点击的是侧面墙壁
                        facing = clickedFace; // pipe_up朝外，base贴墙
                    } else {
                        // 点击的是地面或天花板，但进入了WALL模式（不太可能）
                        facing = ctx.getHorizontalDirection();
                    }
                } else {
                    // FLOOR或CEILING模式，pipefront朝向玩家
                    facing = ctx.getHorizontalDirection().getOpposite();
                }
            }
        }

        return this.defaultBlockState()
                .setValue(FACE, face)
                .setValue(FACING, facing)
                .setValue(WATERLOGGED, ctx.getLevel().getFluidState(pos).getType() == Fluids.WATER);
    }

    // 辅助方法：检查是否有流体连接（从指定方向）
    private boolean hasFluidConnection(Level world, BlockPos neighborPos, BlockPos fromPos) {
        BlockState state = world.getBlockState(neighborPos);

        // 忽略其他离心泵
        if (state.getBlock() instanceof CentrifugalPumpBlock) {
            return false;
        }

        // 计算从当前位置到邻居位置的方向
        Direction directionToNeighbor = Direction.fromDelta(
                neighborPos.getX() - fromPos.getX(),
                neighborPos.getY() - fromPos.getY(),
                neighborPos.getZ() - fromPos.getZ()
        );

        if (directionToNeighbor != null) {
            // 检查是否可以连接（这会包括流体管道和流体管道箱）
            return FluidPipeBlock.canConnectTo(world, neighborPos, state, directionToNeighbor.getOpposite());
        }

        return false;
    }

    // 辅助方法：检查是否有动力连接
    private boolean hasKineticConnection(Level world, BlockPos pos, BlockState state, Direction from) {
        Block block = state.getBlock();

        // 忽略其他离心泵
        if (block instanceof CentrifugalPumpBlock) {
            return false;
        }

        if (block instanceof AbstractShaftBlock || block instanceof AbstractEncasedShaftBlock) {
            if (state.hasProperty(RotatedPillarKineticBlock.AXIS)) {
                return state.getValue(RotatedPillarKineticBlock.AXIS) == from.getAxis();
            }
        }

        if (block instanceof ICogWheel) {
            return true;
        }

        BlockEntity be = world.getBlockEntity(pos);
        return be instanceof KineticBlockEntity;
    }

    private AttachFace clickedFaceToAttachFace(Direction clicked) {
        switch (clicked) {
            case UP: return AttachFace.FLOOR;
            case DOWN: return AttachFace.CEILING;
            default: return AttachFace.WALL;
        }
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (!world.isClientSide) {
            AttachFace face = state.getValue(FACE);
            Direction facing = state.getValue(FACING);
            Direction newFacing = facing;

            // 根据不同的附着面旋转
            if (face == AttachFace.WALL) {
                // 垂直模式，旋转到下一个水平方向
                newFacing = facing.getClockWise();
                if (newFacing.getAxis() == Direction.Axis.Y) {
                    newFacing = Direction.NORTH;
                }
            } else {
                // 水平模式（FLOOR或CEILING），旋转到下一个水平方向
                newFacing = facing.getClockWise();
                if (newFacing.getAxis() == Direction.Axis.Y) {
                    newFacing = Direction.NORTH;
                }
            }

            BlockState newState = state.setValue(FACING, newFacing);
            world.setBlock(pos, newState, 3);

            // 通知流体网络更新
            if (world.getBlockEntity(pos) instanceof CentrifugalPumpBlockEntity pump) {
                pump.onPipeNetworkChanged();
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        AttachFace face = state.getValue(FACE);
        Direction facing = state.getValue(FACING);

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
            // 使用默认形状
        }

        return PUMP_SHAPE;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return getPumpAxis(state);
    }

    protected static Direction.Axis getPumpAxis(BlockState state) {
        if (!(state.getBlock() instanceof CentrifugalPumpBlock)) {
            return Direction.Axis.Y;
        }

        if (!state.hasProperty(FACE) || !state.hasProperty(FACING)) {
            return Direction.Axis.Y;
        }

        AttachFace face = state.getValue(FACE);
        Direction facing = state.getValue(FACING);

        if (face == AttachFace.WALL) {
            return Direction.Axis.Y;
        } else {
            return facing.getAxis();
        }
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == getShaftDirection(state);
    }

    public static Direction getShaftDirection(BlockState state) {
        if (!(state.getBlock() instanceof CentrifugalPumpBlock)) {
            return Direction.UP;
        }

        if (!state.hasProperty(FACE) || !state.hasProperty(FACING)) {
            return Direction.UP;
        }

        AttachFace face = state.getValue(FACE);
        Direction facing = state.getValue(FACING);

        if (face == AttachFace.WALL) {
            return Direction.UP;
        } else {
            return facing.getOpposite();
        }
    }

    public static Direction getPrimaryFluidDirection(BlockState state) {
        if (!(state.getBlock() instanceof CentrifugalPumpBlock)) {
            return Direction.UP;
        }

        if (!state.hasProperty(FACE) || !state.hasProperty(FACING)) {
            return Direction.UP;
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
        if (!(state.getBlock() instanceof CentrifugalPumpBlock)) {
            return Direction.NORTH;
        }

        if (!state.hasProperty(FACE) || !state.hasProperty(FACING)) {
            return Direction.NORTH;
        }

        AttachFace face = state.getValue(FACE);
        Direction facing = state.getValue(FACING);

        if (face == AttachFace.WALL) {
            return facing;
        } else if (face == AttachFace.FLOOR) {
            return Direction.UP;
        } else {
            return Direction.DOWN;
        }
    }

    public static boolean isOpenAt(BlockState state, Direction d) {
        if (!(state.getBlock() instanceof CentrifugalPumpBlock)) {
            return false;
        }

        if (!state.hasProperty(FACE) || !state.hasProperty(FACING)) {
            return false;
        }

        return d == getPrimaryFluidDirection(state) || d == getSecondaryFluidDirection(state);
    }

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
            if (state != oldState) {
                world.scheduleTick(pos, this, 1, TickPriority.HIGH);

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
            world.scheduleTick(pos, this, 1, TickPriority.HIGH);

            if (world.getBlockEntity(pos) instanceof CentrifugalPumpBlockEntity pump) {
                pump.pressureUpdate = true;
            }
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource r) {
        FluidPropagator.propagateChangedPipe(world, pos, state);

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