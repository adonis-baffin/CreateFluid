package com.adonis.fluid.block.aqueduct;

import com.adonis.fluid.content.aqueduct.AqueductPropagator;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;

public abstract class AbstractAqueductBlock extends HorizontalDirectionalBlock
        implements IBE<AbstractAqueductBlockEntity>, IWrenchable, SimpleWaterloggedBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LOCKED = BooleanProperty.create("locked");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    // U型槽形状定义
    protected static final VoxelShape BASE = Block.box(0, 0, 0, 16, 4, 16);
    protected static final VoxelShape WALL_NORTH = Block.box(0, 0, 0, 16, 12, 2);
    protected static final VoxelShape WALL_SOUTH = Block.box(0, 0, 14, 16, 12, 16);
    protected static final VoxelShape WALL_WEST = Block.box(0, 0, 0, 2, 12, 16);
    protected static final VoxelShape WALL_EAST = Block.box(14, 0, 0, 16, 12, 16);

    // 各朝向的完整形状
    protected static final VoxelShape SHAPE_NORTH_SOUTH = Shapes.or(BASE, WALL_WEST, WALL_EAST);
    protected static final VoxelShape SHAPE_EAST_WEST = Shapes.or(BASE, WALL_NORTH, WALL_SOUTH);

    public AbstractAqueductBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LOCKED, false)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOCKED, WATERLOGGED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return (facing == Direction.NORTH || facing == Direction.SOUTH) ? SHAPE_NORTH_SOUTH : SHAPE_EAST_WEST;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction facing = context.getHorizontalDirection();

        // 智能放置：检测相邻水渠并对齐
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adjacentPos = pos.relative(dir);
            BlockState adjacentState = level.getBlockState(adjacentPos);

            if (adjacentState.getBlock() instanceof AbstractAqueductBlock) {
                Direction adjacentFacing = adjacentState.getValue(FACING);
                // 如果相邻水渠的方向与当前方向平行，则对齐
                if (adjacentFacing.getAxis() == dir.getAxis()) {
                    facing = adjacentFacing;
                    break;
                }
            }
        }

        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        boolean waterlogged = fluidState.getType() == Fluids.WATER;

        return this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(WATERLOGGED, waterlogged);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(hand);

        // 流体容器交互
        if (!heldItem.isEmpty() && FluidUtil.getFluidHandler(heldItem).isPresent()) {
            if (!level.isClientSide) {
                InteractionResult result = onBlockEntityUse(level, pos, be -> {
                    IFluidHandler tankHandler = be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER, null)
                            .orElse(null);
                    if (tankHandler == null) return InteractionResult.PASS;

                    // 尝试填充或抽取流体
                    boolean success = FluidUtil.interactWithFluidHandler(player, hand, tankHandler);
                    if (success) {
                        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                        return InteractionResult.SUCCESS;
                    }
                    return InteractionResult.PASS;
                });
                return result;
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (!level.isClientSide) {
            // 反转方向
            Direction currentFacing = state.getValue(FACING);
            Direction newFacing = currentFacing.getOpposite();
            level.setBlock(pos, state.setValue(FACING, newFacing), 3);

            // 通知网络更新
            withBlockEntityDo(level, pos, AbstractAqueductBlockEntity::notifyNetworkUpdate);

            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.5F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            // 红石信号检测
            boolean powered = level.hasNeighborSignal(pos);
            boolean locked = state.getValue(LOCKED);

            if (powered != locked) {
                level.setBlock(pos, state.setValue(LOCKED, powered), 3);
                withBlockEntityDo(level, pos, be -> be.setLocked(powered));
            }
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            // 通知网络管理器
            AqueductPropagator.onAqueductPlaced(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide) {
                // 通知网络管理器
                AqueductPropagator.onAqueductRemoved(level, pos);
            }
            // IBE的默认移除行为
            IBE.onRemove(state, level, pos, newState);
        }
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return getBlockEntityOptional(level, pos)
                .map(be -> (int)(15.0F * be.getFluidLevel()))
                .orElse(0);
    }
}