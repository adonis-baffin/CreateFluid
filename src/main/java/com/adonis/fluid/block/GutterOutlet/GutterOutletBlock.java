package com.adonis.fluid.block.GutterOutlet;

import com.adonis.fluid.registry.CFBlockEntity;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.simibubi.create.foundation.fluid.FluidHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 集水器方块
 * 类似梯形碗状容器，可收集雨雪，接受工作盆输出
 */
public class GutterOutletBlock extends Block implements IBE<GutterOutletBlockEntity>, IWrenchable {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // 碰撞箱 - 简化的外形
    private static final VoxelShape SHAPE_NS = Shapes.or(
            Block.box(0, 0, 3, 16, 5, 13),      // 底座
            Block.box(1, 5, 2, 15, 14, 14)       // 主体
    );

    private static final VoxelShape SHAPE_EW = Shapes.or(
            Block.box(3, 0, 0, 13, 5, 16),      // 底座
            Block.box(2, 5, 1, 14, 14, 15)       // 主体
    );

    public GutterOutletBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACING, Direction.NORTH));
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
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // 窄面对着玩家，即 FACING 设为玩家面向方向的垂直方向
        Direction facing = ctx.getHorizontalDirection();
        // 旋转90度，让窄面朝向玩家
        Direction rotated = facing.getClockWise();
        return this.defaultBlockState().setValue(FACING, rotated);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        // 南北朝向时，东西是窄面
        if (facing == Direction.NORTH || facing == Direction.SOUTH) {
            return SHAPE_NS;
        } else {
            return SHAPE_EW;
        }
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
    public void onRemove(BlockState state, net.minecraft.world.level.Level world, BlockPos pos,
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
     * 窄面是相对于朝向的左右两侧
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
     * 判断指定方向是否为宽面（南北面，不连接流体）
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