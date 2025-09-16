package com.adonis.fluid.block.CentrifugalPump;

import com.adonis.fluid.registry.CFBlockEntity;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;

import com.simibubi.create.content.kinetics.base.AbstractEncasedShaftBlock;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;

import com.simibubi.create.content.kinetics.simpleRelays.AbstractShaftBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
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

import java.util.ArrayList;
import java.util.List;

public class CentrifugalPumpBlock extends DirectionalKineticBlock
        implements IBE<CentrifugalPumpBlockEntity>, SimpleWaterloggedBlock {

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
                    // 点击顶部（对着天花板）：pipefront朝上不可能，所以让pipe_up朝上，pipefront朝玩家
                    face = AttachFace.FLOOR;
                    facing = ctx.getHorizontalDirection(); // pipefront朝玩家
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

            BlockState state = this.defaultBlockState()
                    .setValue(FACE, face)
                    .setValue(FACING, facing)
                    .setValue(WATERLOGGED, ctx.getLevel().getFluidState(pos).getType() == Fluids.WATER);

            return state;
        }

        // 非潜行模式：收集连接信息
        List<Direction> fluidConnections = new ArrayList<>();
        Direction kineticConnection = null;

        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = world.getBlockState(neighborPos);

            // 检查流体连接
            if (FluidPipeBlock.canConnectTo(world, neighborPos, neighborState, dir)) {
                fluidConnections.add(dir);
            }

            // 检查动力连接
            if (kineticConnection == null && hasKineticConnection(world, neighborPos, neighborState, dir)) {
                kineticConnection = dir;
            }
        }

        // 智能决定放置形态
        AttachFace face;
        Direction facing;

        if (fluidConnections.size() >= 2) {
            // 多个流体连接
            PlacementResult best = findBestPlacement(fluidConnections, kineticConnection);
            face = best.face;
            facing = best.facing;
        } else if (fluidConnections.size() == 1) {
            // 单个流体连接
            Direction fluid = fluidConnections.get(0);
            if (kineticConnection != null) {
                PlacementResult result = placementForBoth(fluid, kineticConnection);
                face = result.face;
                facing = result.facing;
            } else {
                PlacementResult result = placementForSingleFluid(fluid);
                face = result.face;
                facing = result.facing;
            }
        } else if (kineticConnection != null) {
            // 只有动力连接
            PlacementResult result = placementForKinetic(kineticConnection);
            face = result.face;
            facing = result.facing;
        } else {
            // 没有连接，使用默认
            face = clickedFaceToAttachFace(ctx.getClickedFace());
            facing = face == AttachFace.WALL ? ctx.getClickedFace() : ctx.getHorizontalDirection().getOpposite();
        }

        BlockState state = this.defaultBlockState()
                .setValue(FACE, face)
                .setValue(FACING, facing)
                .setValue(WATERLOGGED, ctx.getLevel().getFluidState(pos).getType() == Fluids.WATER);

        return state;
    }

    private boolean hasKineticConnection(Level world, BlockPos pos, BlockState state, Direction from) {
        Block block = state.getBlock();

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

    private PlacementResult findBestPlacement(List<Direction> fluids, Direction kinetic) {
        PlacementResult best = null;
        int maxConnections = 0;

        for (AttachFace face : AttachFace.values()) {
            for (Direction facing : Direction.values()) {
                if (face == AttachFace.WALL && facing.getAxis() == Direction.Axis.Y) continue;
                if (face != AttachFace.WALL && facing.getAxis() == Direction.Axis.Y) continue;

                int connections = 0;
                Direction primary = getPrimaryForPlacement(face, facing);
                Direction secondary = getSecondaryForPlacement(face, facing);

                if (fluids.contains(primary)) connections++;
                if (fluids.contains(secondary)) connections++;

                boolean kineticOk = kinetic == null || kinetic.getOpposite() == getShaftForPlacement(face, facing);

                if (connections > maxConnections && kineticOk) {
                    maxConnections = connections;
                    best = new PlacementResult(face, facing);
                }
            }
        }

        return best != null ? best : new PlacementResult(AttachFace.FLOOR, Direction.NORTH);
    }

    private PlacementResult placementForSingleFluid(Direction fluid) {
        if (fluid.getAxis() == Direction.Axis.Y) {
            return new PlacementResult(AttachFace.FLOOR, Direction.NORTH);
        }
        return new PlacementResult(AttachFace.WALL, fluid);
    }

    private PlacementResult placementForBoth(Direction fluid, Direction kinetic) {
        if (kinetic.getAxis() == Direction.Axis.Y) {
            return new PlacementResult(AttachFace.WALL,
                    fluid.getAxis() != Direction.Axis.Y ? fluid : Direction.NORTH);
        }
        return new PlacementResult(AttachFace.FLOOR, kinetic.getOpposite());
    }

    private PlacementResult placementForKinetic(Direction kinetic) {
        if (kinetic.getAxis() == Direction.Axis.Y) {
            return new PlacementResult(AttachFace.WALL, Direction.NORTH);
        }
        return new PlacementResult(AttachFace.FLOOR, kinetic.getOpposite());
    }

    private Direction getPrimaryForPlacement(AttachFace face, Direction facing) {
        if (face == AttachFace.WALL) {
            return Direction.DOWN;
        }
        return facing;
    }

    private Direction getSecondaryForPlacement(AttachFace face, Direction facing) {
        if (face == AttachFace.WALL) {
            return facing;
        } else if (face == AttachFace.FLOOR) {
            return Direction.UP;
        } else {
            return Direction.DOWN;
        }
    }

    private Direction getShaftForPlacement(AttachFace face, Direction facing) {
        if (face == AttachFace.WALL) {
            return Direction.UP;
        }
        return facing.getOpposite();
    }

    private AttachFace clickedFaceToAttachFace(Direction clicked) {
        switch (clicked) {
            case UP: return AttachFace.FLOOR;
            case DOWN: return AttachFace.CEILING;
            default: return AttachFace.WALL;
        }
    }

    private static class PlacementResult {
        final AttachFace face;
        final Direction facing;

        PlacementResult(AttachFace face, Direction facing) {
            this.face = face;
            this.facing = facing;
        }
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