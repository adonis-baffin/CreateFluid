package com.adonis.fluid.block.SmartFluidInterface;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class SmartFluidInterfaceBlock extends HorizontalDirectionalBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    // 定义正确方向的形状，第一层上边缘下降0.1
    // NORTH: 向北伸出（贴在南边方块上）
    private static final VoxelShape NORTH_LAYER_1 = Block.box(3, 3, 14, 13, 12.9, 16);  // 上边缘从13降到12.9
    private static final VoxelShape NORTH_LAYER_2 = Block.box(4, 4, 13, 12, 12, 14);    // 中间层保持不变
    private static final VoxelShape NORTH_LAYER_3 = Block.box(5, 5, 11, 11, 11, 13);    // 伸出最远的层保持不变
    private static final VoxelShape NORTH_SHAPE = Shapes.or(NORTH_LAYER_1, NORTH_LAYER_2, NORTH_LAYER_3);

    // SOUTH: 向南伸出（贴在北边方块上）
    private static final VoxelShape SOUTH_LAYER_1 = Block.box(3, 3, 0, 13, 12.9, 2);    // 上边缘从13降到12.9
    private static final VoxelShape SOUTH_LAYER_2 = Block.box(4, 4, 2, 12, 12, 3);      // 中间层保持不变
    private static final VoxelShape SOUTH_LAYER_3 = Block.box(5, 5, 3, 11, 11, 5);      // 伸出最远的层保持不变
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(SOUTH_LAYER_1, SOUTH_LAYER_2, SOUTH_LAYER_3);

    // EAST: 向东伸出（贴在西边方块上）
    private static final VoxelShape EAST_LAYER_1 = Block.box(0, 3, 3, 2, 12.9, 13);     // 上边缘从13降到12.9
    private static final VoxelShape EAST_LAYER_2 = Block.box(2, 4, 4, 3, 12, 12);       // 中间层保持不变
    private static final VoxelShape EAST_LAYER_3 = Block.box(3, 5, 5, 5, 11, 11);       // 伸出最远的层保持不变
    private static final VoxelShape EAST_SHAPE = Shapes.or(EAST_LAYER_1, EAST_LAYER_2, EAST_LAYER_3);

    // WEST: 向西伸出（贴在东边方块上）
    private static final VoxelShape WEST_LAYER_1 = Block.box(14, 3, 3, 16, 12.9, 13);   // 上边缘从13降到12.9
    private static final VoxelShape WEST_LAYER_2 = Block.box(13, 4, 4, 14, 12, 12);     // 中间层保持不变
    private static final VoxelShape WEST_LAYER_3 = Block.box(11, 5, 5, 13, 11, 11);     // 伸出最远的层保持不变
    private static final VoxelShape WEST_SHAPE = Shapes.or(WEST_LAYER_1, WEST_LAYER_2, WEST_LAYER_3);

    public SmartFluidInterfaceBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    // 辅助方法：检查方块是否有碰撞箱
    private boolean hasCollisionShape(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        VoxelShape collisionShape = state.getCollisionShape(level, pos);
        return !collisionShape.isEmpty();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getHorizontalDirection().getOpposite();
        BlockPos blockpos = context.getClickedPos();
        BlockPos attachedPos = blockpos.relative(direction.getOpposite());

        // 检查是否可以贴在这个方向（需要有碰撞箱）
        if (hasCollisionShape(context.getLevel(), attachedPos)) {
            return this.defaultBlockState().setValue(FACING, direction);
        }

        // 如果不能直接贴，尝试其他水平方向
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos testPos = blockpos.relative(dir.getOpposite());
            if (hasCollisionShape(context.getLevel(), testPos)) {
                return this.defaultBlockState().setValue(FACING, dir);
            }
        }

        return null; // 如果周围都没有有碰撞箱的方块，则不能放置
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction direction = state.getValue(FACING);
        BlockPos attachedPos = pos.relative(direction.getOpposite());
        // 需要背后的方块有碰撞箱才能存活
        return hasCollisionShape(level, attachedPos);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (direction.getOpposite() == state.getValue(FACING) && !state.canSurvive(level, currentPos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }
}