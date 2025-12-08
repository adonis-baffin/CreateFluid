package com.adonis.fluid.block.GutterOutlet;

import com.adonis.fluid.registry.CFBlockEntity;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.fluid.FluidHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 智能集水器方块
 * 完整方块外形，只有顶部开口
 * 添加流体过滤和红石控制功能
 */
public class SmartGutterOutletBlock extends Block implements IBE<SmartGutterOutletBlockEntity>, IWrenchable, SimpleWaterloggedBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    // 智能集水器的碰撞箱：完整方块底部 + 顶部开口边框
    private static final VoxelShape SMART_SHAPE_NS;
    private static final VoxelShape SMART_SHAPE_EW;

    static {
        // FACING = NORTH/SOUTH: 窄边在Z轴（南北方向）
        VoxelShape baseNS = Block.box(0, 0, 0, 16, 14, 16);
        VoxelShape topOuterNS = Block.box(0, 14, 0, 16, 16, 16);
        VoxelShape topInnerNS = Block.box(1, 14, 3, 15, 16, 13);
        VoxelShape topNS = Shapes.join(topOuterNS, topInnerNS, BooleanOp.ONLY_FIRST);
        SMART_SHAPE_NS = Shapes.or(baseNS, topNS);

        // FACING = EAST/WEST: 窄边在X轴（东西方向）
        VoxelShape baseEW = Block.box(0, 0, 0, 16, 14, 16);
        VoxelShape topOuterEW = Block.box(0, 14, 0, 16, 16, 16);
        VoxelShape topInnerEW = Block.box(3, 14, 1, 13, 16, 15);
        VoxelShape topEW = Shapes.join(topOuterEW, topInnerEW, BooleanOp.ONLY_FIRST);
        SMART_SHAPE_EW = Shapes.or(baseEW, topEW);
    }

    public SmartGutterOutletBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false)
                .setValue(POWERED, false));
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(hand);

        return onBlockEntityUse(world, pos, be -> {
            if (!heldItem.isEmpty()) {
                if (FluidHelper.tryEmptyItemIntoBE(world, player, hand, heldItem, be))
                    return InteractionResult.sidedSuccess(world.isClientSide);
                if (FluidHelper.tryFillItemFromBE(world, player, hand, heldItem, be))
                    return InteractionResult.sidedSuccess(world.isClientSide);

                if (GenericItemEmptying.canItemBeEmptied(world, heldItem)
                        || GenericItemFilling.canItemBeFilled(world, heldItem))
                    return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        FluidState fluidState = ctx.getLevel().getFluidState(ctx.getClickedPos());
        Direction facing = ctx.getHorizontalDirection();
        Direction rotated = facing.getClockWise();
        return this.defaultBlockState()
                .setValue(FACING, rotated)
                .setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER)
                .setValue(POWERED, ctx.getLevel().hasNeighborSignal(ctx.getClickedPos()));
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide)
            return;
        if (!level.getBlockTicks().willTickThisTick(pos, this))
            level.scheduleTick(pos, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean previouslyPowered = state.getValue(POWERED);
        if (previouslyPowered != level.hasNeighborSignal(pos))
            level.setBlock(pos, state.cycle(POWERED), 2);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        if (facing == Direction.NORTH || facing == Direction.SOUTH) {
            return SMART_SHAPE_NS;  // 改为 NS
        } else {
            return SMART_SHAPE_EW;  // 改为 EW
        }
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        IBE.onRemove(state, world, pos, newState);
    }

    @Override
    public Class<SmartGutterOutletBlockEntity> getBlockEntityClass() {
        return SmartGutterOutletBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SmartGutterOutletBlockEntity> getBlockEntityType() {
        return CFBlockEntity.SMART_GUTTER_OUTLET.get();
    }

    /**
     * 判断指定方向是否为窄面（可连接流体）
     */
    public static boolean isNarrowSide(BlockState state, Direction side) {
        if (side.getAxis() == Direction.Axis.Y) {
            return false;
        }
        Direction facing = state.getValue(FACING);
        return side.getAxis() != facing.getAxis();
    }

    /**
     * 判断指定方向是否为宽面（不连接流体，但显示过滤器）
     */
    public static boolean isWideSide(BlockState state, Direction side) {
        if (side.getAxis() == Direction.Axis.Y) {
            return false;
        }
        Direction facing = state.getValue(FACING);
        return side.getAxis() == facing.getAxis();
    }
}