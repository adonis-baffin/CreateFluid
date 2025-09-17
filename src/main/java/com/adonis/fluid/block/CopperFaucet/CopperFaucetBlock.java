package com.adonis.fluid.block.CopperFaucet;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.block.IBE;
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
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.capability.IFluidHandler;

import net.minecraft.world.item.context.UseOnContext;

import javax.annotation.Nullable;

public class CopperFaucetBlock extends HorizontalDirectionalBlock implements IBE<CopperFaucetBlockEntity>, IWrenchable {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OPEN = net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN;

    // 根据模型定义碰撞箱
    // NORTH: 龙头朝向北方（贴在南边的方块上）
    private static final VoxelShape NORTH_OUTLET = Block.box(5, 5, 15, 11, 11, 16);  // 出水口
    private static final VoxelShape NORTH_PIPE = Block.box(6, 6, 6, 10, 10, 15);     // 主管道
    private static final VoxelShape NORTH_DROP = Block.box(6, 4, 6, 10, 6, 10);      // 底部滴水部分
    private static final VoxelShape NORTH_BASE = Block.box(5, 5, 11, 11, 11, 13);    // 连接基座
    private static final VoxelShape NORTH_VALVE_TOP = Block.box(5, 12, 9, 11, 13, 15); // 阀门顶盖
    private static final VoxelShape NORTH_VALVE_HANDLE = Block.box(7, 11, 11, 9, 12, 13); // 阀门把手
    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            NORTH_OUTLET, NORTH_PIPE, NORTH_DROP, NORTH_BASE, NORTH_VALVE_TOP, NORTH_VALVE_HANDLE
    );

    // SOUTH: 龙头朝向南方（贴在北边的方块上）
    private static final VoxelShape SOUTH_OUTLET = Block.box(5, 5, 0, 11, 11, 1);
    private static final VoxelShape SOUTH_PIPE = Block.box(6, 6, 1, 10, 10, 10);
    private static final VoxelShape SOUTH_DROP = Block.box(6, 4, 6, 10, 6, 10);
    private static final VoxelShape SOUTH_BASE = Block.box(5, 5, 3, 11, 11, 5);
    private static final VoxelShape SOUTH_VALVE_TOP = Block.box(5, 12, 1, 11, 13, 7);
    private static final VoxelShape SOUTH_VALVE_HANDLE = Block.box(7, 11, 3, 9, 12, 5);
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            SOUTH_OUTLET, SOUTH_PIPE, SOUTH_DROP, SOUTH_BASE, SOUTH_VALVE_TOP, SOUTH_VALVE_HANDLE
    );

    // EAST: 龙头朝向东方（贴在西边的方块上）
    private static final VoxelShape EAST_OUTLET = Block.box(0, 5, 5, 1, 11, 11);
    private static final VoxelShape EAST_PIPE = Block.box(1, 6, 6, 10, 10, 10);
    private static final VoxelShape EAST_DROP = Block.box(6, 4, 6, 10, 6, 10);
    private static final VoxelShape EAST_BASE = Block.box(3, 5, 5, 5, 11, 11);
    private static final VoxelShape EAST_VALVE_TOP = Block.box(1, 12, 5, 7, 13, 11);
    private static final VoxelShape EAST_VALVE_HANDLE = Block.box(3, 11, 7, 5, 12, 9);
    private static final VoxelShape EAST_SHAPE = Shapes.or(
            EAST_OUTLET, EAST_PIPE, EAST_DROP, EAST_BASE, EAST_VALVE_TOP, EAST_VALVE_HANDLE
    );

    // WEST: 龙头朝向西方（贴在东边的方块上）
    private static final VoxelShape WEST_OUTLET = Block.box(15, 5, 5, 16, 11, 11);
    private static final VoxelShape WEST_PIPE = Block.box(6, 6, 6, 15, 10, 10);
    private static final VoxelShape WEST_DROP = Block.box(6, 4, 6, 10, 6, 10);
    private static final VoxelShape WEST_BASE = Block.box(11, 5, 5, 13, 11, 11);
    private static final VoxelShape WEST_VALVE_TOP = Block.box(9, 12, 5, 15, 13, 11);
    private static final VoxelShape WEST_VALVE_HANDLE = Block.box(11, 11, 7, 13, 12, 9);
    private static final VoxelShape WEST_SHAPE = Shapes.or(
            WEST_OUTLET, WEST_PIPE, WEST_DROP, WEST_BASE, WEST_VALVE_TOP, WEST_VALVE_HANDLE
    );

    public CopperFaucetBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
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

    // 辅助方法：检查方块是否有流体存储能力
    private boolean hasFluidCapability(LevelReader level, BlockPos pos, Direction fromDirection) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return false;
        }

        // 首先尝试从指定方向获取流体能力
        IFluidHandler capability = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, fromDirection).orElse(null);
        if (capability != null && capability.getTanks() > 0) {
            return true;
        }

        // 如果没有，尝试获取默认的流体能力
        capability = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
        return capability != null && capability.getTanks() > 0;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getHorizontalDirection().getOpposite();
        BlockPos blockpos = context.getClickedPos();
        BlockPos attachedPos = blockpos.relative(direction.getOpposite());

        // 检查是否可以贴在这个方向（需要有流体存储能力）
        if (hasFluidCapability(context.getLevel(), attachedPos, direction)) {
            return this.defaultBlockState().setValue(FACING, direction);
        }

        // 如果不能直接贴，尝试其他水平方向
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos testPos = blockpos.relative(dir.getOpposite());
            if (hasFluidCapability(context.getLevel(), testPos, dir)) {
                return this.defaultBlockState().setValue(FACING, dir);
            }
        }

        return null; // 如果周围都没有有流体存储能力的方块，则不能放置
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction direction = state.getValue(FACING);
        BlockPos attachedPos = pos.relative(direction.getOpposite());
        // 需要背后的方块有流体存储能力才能存活
        return hasFluidCapability(level, attachedPos, direction);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
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

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide())
            return InteractionResult.SUCCESS;

        ItemStack heldItem = player.getItemInHand(hand);

        // 如果手持扳手，也可以开关
        if (AllItems.WRENCH.isIn(heldItem)) {
            toggleFaucet(state, level, pos);
            return InteractionResult.SUCCESS;
        }

        // 空手右键开关
        if (heldItem.isEmpty()) {
            toggleFaucet(state, level, pos);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        // 扳手右键可以开关
        if (!context.getLevel().isClientSide()) {
            toggleFaucet(state, context.getLevel(), context.getClickedPos());
        }
        return InteractionResult.SUCCESS;
    }

    private void toggleFaucet(BlockState state, Level level, BlockPos pos) {
        boolean isOpen = state.getValue(OPEN);
        level.setBlockAndUpdate(pos, state.setValue(OPEN, !isOpen));

        // 播放开关声音
        level.playSound(null, pos, isOpen ?
                        net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_CLOSE :
                        net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_OPEN,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<CopperFaucetBlockEntity> getBlockEntityClass() {
        return CopperFaucetBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CopperFaucetBlockEntity> getBlockEntityType() {
        return com.adonis.fluid.registry.CFBlockEntity.COPPER_FAUCET.get();
    }
}