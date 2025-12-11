package com.adonis.fluid.block.GutterOutlet;

import com.adonis.fluid.registry.CFBlockEntity;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.fluid.FluidHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * 集水器方块
 * 类似梯形碗状容器，可收集雨雪，接受工作盆输出
 *
 * 形状说明（FACING=NORTH时，窄边在X轴/东西方向）：
 * - 顶部开口：16x16，向下延伸7像素（Y: 9-16）
 * - 内陷：10x14x2，短边对应窄边
 * - 底座：宽16x窄10，向上延伸5像素（Y: 0-5）
 * - 中间是斜面连接（Y: 5-9）
 */
public class GutterOutletBlock extends Block implements IBE<GutterOutletBlockEntity>, IWrenchable, SimpleWaterloggedBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    // FACING=NORTH/SOUTH 时：窄边在X轴（东西），宽边在Z轴（南北）
    // 碰撞箱组成：
    // 1. 底座：X方向窄(3-13=10像素)，Z方向宽(0-16=16像素)，Y: 0-5
    // 2. 顶部外框：完整16x16，Y: 9-16
    // 3. 顶部内陷挖空：X方向窄(3-13=10像素)，Z方向(1-15=14像素)，Y: 14-16（2像素深）

    private static final VoxelShape SHAPE_NS;
    private static final VoxelShape SHAPE_EW;

    static {
        // FACING = NORTH/SOUTH: 窄边在X轴
        // 底座：窄10(X: 3-13) x 宽16(Z: 0-16) x 高5(Y: 0-5)
        VoxelShape baseNS = Block.box(3, 0, 0, 13, 5, 16);

        // 顶部外框：16x16，Y: 9-16
        VoxelShape topOuterNS = Block.box(0, 9, 0, 16, 16, 16);

        // 顶部内陷（要挖空的部分）：窄10(X: 3-13) x 14(Z: 1-15) x 深2(Y: 14-16)
        VoxelShape topInnerNS = Block.box(3, 14, 1, 13, 16, 15);

        // 顶部 = 外框 - 内陷
        VoxelShape topNS = Shapes.join(topOuterNS, topInnerNS, BooleanOp.ONLY_FIRST);

        // 中间斜面部分简化为方块：窄边逐渐扩展
        // 为了碰撞简单，用几个方块近似斜面，Y: 5-9
        VoxelShape middleNS = Shapes.or(
                Block.box(2, 5, 0, 14, 7, 16),   // Y 5-7: 稍微扩展
                Block.box(1, 7, 0, 15, 9, 16)    // Y 7-9: 继续扩展
        );

        SHAPE_NS = Shapes.or(baseNS, middleNS, topNS);

        // FACING = EAST/WEST: 窄边在Z轴（旋转90度）
        // 底座：宽16(X: 0-16) x 窄10(Z: 3-13) x 高5(Y: 0-5)
        VoxelShape baseEW = Block.box(0, 0, 3, 16, 5, 13);

        // 顶部外框：16x16，Y: 9-16
        VoxelShape topOuterEW = Block.box(0, 9, 0, 16, 16, 16);

        // 顶部内陷（要挖空的部分）：14(X: 1-15) x 窄10(Z: 3-13) x 深2(Y: 14-16)
        VoxelShape topInnerEW = Block.box(1, 14, 3, 15, 16, 13);

        // 顶部 = 外框 - 内陷
        VoxelShape topEW = Shapes.join(topOuterEW, topInnerEW, BooleanOp.ONLY_FIRST);

        // 中间斜面部分
        VoxelShape middleEW = Shapes.or(
                Block.box(0, 5, 2, 16, 7, 14),
                Block.box(0, 7, 1, 16, 9, 15)
        );

        SHAPE_EW = Shapes.or(baseEW, middleEW, topEW);
    }

    public GutterOutletBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false));
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
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        FluidState fluidState = ctx.getLevel().getFluidState(ctx.getClickedPos());
        Direction facing = ctx.getHorizontalDirection();
        // 旋转90度，让窄面朝向玩家
//        Direction rotated = facing.getClockWise();
        Direction rotated = facing.getOpposite();
        return this.defaultBlockState()
                .setValue(FACING, rotated)
                .setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        // NORTH/SOUTH: 窄边在Z轴（南北方向）
        // EAST/WEST: 窄边在X轴（东西方向）
        if (facing == Direction.NORTH || facing == Direction.SOUTH) {
            return SHAPE_EW;  // 改这里
        } else {
            return SHAPE_NS;  // 改这里
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
    public Class<GutterOutletBlockEntity> getBlockEntityClass() {
        return GutterOutletBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends GutterOutletBlockEntity> getBlockEntityType() {
        return CFBlockEntity.GUTTER_OUTLET.get();
    }

    /**
     * 判断指定方向是否为窄面（可连接流体）
     * 窄面是垂直于 FACING 的水平方向
     *
     * FACING=NORTH/SOUTH 时，窄面在 EAST/WEST
     * FACING=EAST/WEST 时，窄面在 NORTH/SOUTH
     */
    public static boolean isNarrowSide(BlockState state, Direction side) {
        if (side.getAxis() == Direction.Axis.Y) {
            return false;
        }
        Direction facing = state.getValue(FACING);
        // 窄面是垂直于朝向的水平方向
        return side.getAxis() != facing.getAxis();
    }

    /**
     * 判断指定方向是否为宽面（不连接流体）
     * 宽面是平行于 FACING 的方向
     */
    public static boolean isWideSide(BlockState state, Direction side) {
        if (side.getAxis() == Direction.Axis.Y) {
            return false;
        }
        Direction facing = state.getValue(FACING);
        // 宽面是平行于朝向的方向
        return side.getAxis() == facing.getAxis();
    }
}