package com.adonis.fluid.block.CentrifugalPump;

import com.adonis.fluid.registry.CFBlockEntity;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.kinetics.base.AbstractEncasedShaftBlock;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.AbstractShaftBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.TickPriority;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * 离心泵方块
 * 继承 KineticBlock 以获得动力学功能
 * 使用 HORIZONTAL_FACING（4方向）而非 FACING（6方向）
 */
public class CentrifugalPumpBlock extends KineticBlock
        implements IBE<CentrifugalPumpBlockEntity>, SimpleWaterloggedBlock, IWrenchable {

    // 使用 HORIZONTAL_FACING - 只有东南西北四个方向
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty ENCASED = BooleanProperty.create("encased");

    private static final VoxelShape PUMP_SHAPE = Block.box(2, 2, 2, 14, 14, 14);
    private static final VoxelShape FULL_SHAPE = Shapes.block();

    public CentrifugalPumpBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACE, AttachFace.FLOOR)
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false)
                .setValue(ENCASED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, WATERLOGGED, ENCASED);
        super.createBlockStateDefinition(builder);
    }

    // ============== IRotate 实现 ==============

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return getPumpAxis(state);
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == getShaftDirection(state);
    }

    protected static Direction.Axis getPumpAxis(BlockState state) {
        if (!(state.getBlock() instanceof CentrifugalPumpBlock)) {
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

    public static Direction getShaftDirection(BlockState state) {
        if (!(state.getBlock() instanceof CentrifugalPumpBlock)) {
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

    // ============== 流体方向 ==============

    public static Direction getPrimaryFluidDirection(BlockState state) {
        if (!(state.getBlock() instanceof CentrifugalPumpBlock)) {
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

        return d == getPrimaryFluidDirection(state) || d == getSecondaryFluidDirection(state);
    }

    // ============== 扳手旋转逻辑 ==============

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        // 封装状态下不允许旋转
        if (originalState.getValue(ENCASED)) {
            return originalState;
        }

        Direction currentFacing = originalState.getValue(FACING);
        // 顺时针旋转到下一个水平方向
        Direction newFacing = currentFacing.getClockWise();

        return originalState.setValue(FACING, newFacing);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();

        // 封装状态下不允许旋转
        if (state.getValue(ENCASED)) {
            return InteractionResult.PASS;
        }

        if (!world.isClientSide) {
            BlockState rotated = getRotatedBlockState(state, context.getClickedFace());

            if (rotated != state) {
                KineticBlockEntity.switchToBlockState(world, pos, rotated);
                IWrenchable.playRotateSound(world, pos);

                // 通知流体网络更新
                if (world.getBlockEntity(pos) instanceof CentrifugalPumpBlockEntity pump) {
                    pump.onPipeNetworkChanged();
                }
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        // 如果是封装状态，移除封装
        if (state.getValue(ENCASED)) {
            if (!world.isClientSide) {
                BlockState newState = state.setValue(ENCASED, false);
                world.setBlock(pos, newState, 3);

                // 掉落铜机壳
                if (player != null && !player.isCreative()) {
                    Block.popResource(world, pos, AllBlocks.COPPER_CASING.asStack());
                }

                // 播放破坏声音
                world.playSound(null, pos, SoundEvents.COPPER_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);

                // 通知流体网络更新
                if (world.getBlockEntity(pos) instanceof CentrifugalPumpBlockEntity pump) {
                    pump.onEncasedStateChanged(false);
                }
            }
            return InteractionResult.SUCCESS;
        }

        // 非封装状态，使用默认的潜行扳手行为（拆除方块）
        return super.onSneakWrenched(state, context);
    }

    // ============== 旋转和镜像 ==============

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirrorIn) {
        return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
    }

    // ============== 交互逻辑 ==============

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(hand);

        // 检查是否是铜机壳（不消耗铜机壳）
        if (!state.getValue(ENCASED) && heldItem.is(AllBlocks.COPPER_CASING.asItem())) {
            if (!world.isClientSide) {
                BlockState newState = state.setValue(ENCASED, true);
                world.setBlock(pos, newState, 3);

                world.playSound(null, pos, SoundEvents.COPPER_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);

                if (world.getBlockEntity(pos) instanceof CentrifugalPumpBlockEntity pump) {
                    pump.onEncasedStateChanged(true);
                }
            }
            return InteractionResult.sidedSuccess(world.isClientSide);
        }

        return InteractionResult.PASS;
    }

    // ============== 形状和碰撞箱 ==============

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        if (state.getValue(ENCASED)) {
            return FULL_SHAPE;
        }

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

    // ============== 放置逻辑 ==============

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
                    face = AttachFace.FLOOR;
                    facing = ctx.getHorizontalDirection().getOpposite();
                    break;

                case DOWN:
                    face = AttachFace.WALL;
                    facing = ctx.getHorizontalDirection();
                    break;

                default:
                    face = AttachFace.FLOOR;
                    // 确保 facing 是水平方向
                    if (clickedFace.getAxis() != Direction.Axis.Y) {
                        facing = clickedFace.getOpposite();
                    } else {
                        facing = ctx.getHorizontalDirection().getOpposite();
                    }
                    break;
            }

            return this.defaultBlockState()
                    .setValue(FACE, face)
                    .setValue(FACING, facing)
                    .setValue(WATERLOGGED, world.getFluidState(pos).getType() == Fluids.WATER)
                    .setValue(ENCASED, false);
        }

        // 非潜行模式：智能放置
        boolean hasPipeUp = hasFluidConnection(world, pos.above(), pos);
        boolean hasPipeDown = hasFluidConnection(world, pos.below(), pos);
        boolean hasKineticUp = hasKineticConnection(world, pos.above(), world.getBlockState(pos.above()), Direction.DOWN);

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

        if (hasPipeUp) {
            face = AttachFace.FLOOR;
            facing = firstHorizontalPipe != null ? firstHorizontalPipe : ctx.getHorizontalDirection().getOpposite();

        } else if (hasPipeDown) {
            if (hasKineticUp) {
                face = AttachFace.WALL;
                facing = firstHorizontalPipe != null ? firstHorizontalPipe : ctx.getHorizontalDirection();
            } else {
                face = AttachFace.CEILING;
                facing = firstHorizontalPipe != null ? firstHorizontalPipe : ctx.getHorizontalDirection().getOpposite();
            }

        } else if (firstHorizontalPipe != null) {
            if (hasKineticUp) {
                face = AttachFace.WALL;
                facing = firstHorizontalPipe;
            } else {
                face = AttachFace.FLOOR;
                facing = firstHorizontalPipe;
            }

        } else {
            if (firstHorizontalKinetic != null) {
                face = AttachFace.FLOOR;
                facing = firstHorizontalKinetic.getOpposite();

            } else if (hasKineticUp) {
                face = AttachFace.WALL;
                facing = ctx.getHorizontalDirection();

            } else {
                face = clickedFaceToAttachFace(ctx.getClickedFace());
                if (face == AttachFace.WALL) {
                    Direction clickedFace = ctx.getClickedFace();
                    if (clickedFace.getAxis() != Direction.Axis.Y) {
                        facing = clickedFace;
                    } else {
                        facing = ctx.getHorizontalDirection();
                    }
                } else {
                    facing = ctx.getHorizontalDirection().getOpposite();
                }
            }
        }

        return this.defaultBlockState()
                .setValue(FACE, face)
                .setValue(FACING, facing)
                .setValue(WATERLOGGED, world.getFluidState(pos).getType() == Fluids.WATER)
                .setValue(ENCASED, false);
    }

    private boolean hasFluidConnection(Level world, BlockPos neighborPos, BlockPos fromPos) {
        BlockState state = world.getBlockState(neighborPos);

        if (state.getBlock() instanceof CentrifugalPumpBlock) {
            return false;
        }

        Direction directionToNeighbor = Direction.fromDelta(
                neighborPos.getX() - fromPos.getX(),
                neighborPos.getY() - fromPos.getY(),
                neighborPos.getZ() - fromPos.getZ()
        );

        if (directionToNeighbor != null) {
            return FluidPipeBlock.canConnectTo(world, neighborPos, state, directionToNeighbor.getOpposite());
        }

        return false;
    }

    private boolean hasKineticConnection(Level world, BlockPos pos, BlockState state, Direction from) {
        Block block = state.getBlock();

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

    // ============== 方块事件 ==============

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        boolean blockTypeChanged = !state.is(newState.getBlock());
        if (blockTypeChanged && !world.isClientSide) {
            FluidPropagator.propagateChangedPipe(world, pos, state);
        }
        super.onRemove(state, world, pos, newState, isMoving);
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
        DebugPackets.sendNeighborsUpdatePacket(world, pos);
        Direction d = FluidPropagator.validateNeighbourChange(state, world, pos, otherBlock, neighborPos, isMoving);

        if (d != null && isOpenAt(state, d)) {
            world.scheduleTick(pos, this, 1, TickPriority.HIGH);

            if (world.getBlockEntity(pos) instanceof CentrifugalPumpBlockEntity pump) {
                pump.pressureUpdate = true;
            }
        }

        // 检测流体容器的放置
        if (!world.isClientSide && world.getBlockEntity(pos) instanceof CentrifugalPumpBlockEntity pump) {
            Direction neighborDir = null;
            for (Direction dir : Direction.values()) {
                if (pos.relative(dir).equals(neighborPos)) {
                    neighborDir = dir;
                    break;
                }
            }

            if (neighborDir != null && isOpenAt(state, neighborDir)) {
                BlockEntity neighborBE = world.getBlockEntity(neighborPos);
                if (neighborBE != null) {
                    LazyOptional<IFluidHandler> capability = neighborBE.getCapability(
                            ForgeCapabilities.FLUID_HANDLER, neighborDir.getOpposite()
                    );
                    if (!capability.isPresent()) {
                        capability = neighborBE.getCapability(ForgeCapabilities.FLUID_HANDLER, null);
                    }

                    if (capability.isPresent()) {
                        pump.onFluidContainerDetected(neighborDir);
                    }
                }
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