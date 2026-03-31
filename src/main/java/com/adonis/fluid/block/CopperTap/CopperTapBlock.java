package com.adonis.fluid.block.CopperTap;

import com.adonis.fluid.registry.CFBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.content.equipment.wrench.WrenchItem;

import javax.annotation.Nullable;

public class CopperTapBlock extends HorizontalDirectionalBlock implements IBE<CopperTapBlockEntity>, IWrenchable {

    public static final MapCodec<CopperTapBlock> CODEC = simpleCodec(CopperTapBlock::new);

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    // VoxelShapes
    private static final VoxelShape NORTH_OUTLET = Block.box(3, 3, 15, 13, 12.9, 16);
    private static final VoxelShape NORTH_PIPE = Block.box(6, 6, 6, 10, 10, 15);
    private static final VoxelShape NORTH_DROP = Block.box(6, 4, 6, 10, 6, 10);
    private static final VoxelShape NORTH_BASE = Block.box(5, 5, 11, 11, 11, 13);
    private static final VoxelShape NORTH_VALVE_TOP = Block.box(5, 13, 9, 11, 14, 15);
    private static final VoxelShape NORTH_VALVE_HANDLE = Block.box(7, 11, 11, 9, 13, 13);
    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            NORTH_OUTLET, NORTH_PIPE, NORTH_DROP, NORTH_BASE, NORTH_VALVE_TOP, NORTH_VALVE_HANDLE
    );

    private static final VoxelShape SOUTH_OUTLET = Block.box(3, 3, 0, 13, 12.9, 1);
    private static final VoxelShape SOUTH_PIPE = Block.box(6, 6, 1, 10, 10, 10);
    private static final VoxelShape SOUTH_DROP = Block.box(6, 4, 6, 10, 6, 10);
    private static final VoxelShape SOUTH_BASE = Block.box(5, 5, 3, 11, 11, 5);
    private static final VoxelShape SOUTH_VALVE_TOP = Block.box(5, 13, 1, 11, 14, 7);
    private static final VoxelShape SOUTH_VALVE_HANDLE = Block.box(7, 11, 3, 9, 13, 5);
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            SOUTH_OUTLET, SOUTH_PIPE, SOUTH_DROP, SOUTH_BASE, SOUTH_VALVE_TOP, SOUTH_VALVE_HANDLE
    );

    private static final VoxelShape EAST_OUTLET = Block.box(0, 3, 3, 1, 12.9, 13);
    private static final VoxelShape EAST_PIPE = Block.box(1, 6, 6, 10, 10, 10);
    private static final VoxelShape EAST_DROP = Block.box(6, 4, 6, 10, 6, 10);
    private static final VoxelShape EAST_BASE = Block.box(3, 5, 5, 5, 11, 11);
    private static final VoxelShape EAST_VALVE_TOP = Block.box(1, 13, 5, 7, 14, 11);
    private static final VoxelShape EAST_VALVE_HANDLE = Block.box(3, 11, 7, 5, 13, 9);
    private static final VoxelShape EAST_SHAPE = Shapes.or(
            EAST_OUTLET, EAST_PIPE, EAST_DROP, EAST_BASE, EAST_VALVE_TOP, EAST_VALVE_HANDLE
    );

    private static final VoxelShape WEST_OUTLET = Block.box(15, 3, 3, 16, 12.9, 13);
    private static final VoxelShape WEST_PIPE = Block.box(6, 6, 6, 15, 10, 10);
    private static final VoxelShape WEST_DROP = Block.box(6, 4, 6, 10, 6, 10);
    private static final VoxelShape WEST_BASE = Block.box(11, 5, 5, 13, 11, 11);
    private static final VoxelShape WEST_VALVE_TOP = Block.box(9, 13, 5, 15, 14, 11);
    private static final VoxelShape WEST_VALVE_HANDLE = Block.box(11, 11, 7, 13, 13, 9);
    private static final VoxelShape WEST_SHAPE = Shapes.or(
            WEST_OUTLET, WEST_PIPE, WEST_DROP, WEST_BASE, WEST_VALVE_TOP, WEST_VALVE_HANDLE
    );

    public CopperTapBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, POWERED);
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

    // 检查是否是铜格栅（所有变种）
    private boolean isCopperGrate(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.COPPER_GRATE ||
                block == Blocks.EXPOSED_COPPER_GRATE ||
                block == Blocks.WEATHERED_COPPER_GRATE ||
                block == Blocks.OXIDIZED_COPPER_GRATE ||
                block == Blocks.WAXED_COPPER_GRATE ||
                block == Blocks.WAXED_EXPOSED_COPPER_GRATE ||
                block == Blocks.WAXED_WEATHERED_COPPER_GRATE ||
                block == Blocks.WAXED_OXIDIZED_COPPER_GRATE;
    }

    // 使用 useItemOn 代替 use，仿照渔业模组
    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hitResult) {
        // 处理扳手交互
        if (stack.getItem() instanceof WrenchItem) {
            UseOnContext wrenchContext = new UseOnContext(level, player, hand, stack, hitResult);
            InteractionResult result = onWrenched(state, wrenchContext);
            if (result != InteractionResult.PASS) {
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        // 处理普通右键交互
        if (state.getValue(POWERED)) {
            if (!level.isClientSide) {
                level.playSound(null, pos,
                        net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_CLOSE,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.3f, 2.0f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        // 检查是否应该阻止切换
        if (!stack.isEmpty() && GenericItemFilling.canItemBeFilled(level, stack)) {
            if (!(stack.getItem() instanceof net.minecraft.world.item.BucketItem)) {
                BlockPos belowPos = pos.below();
                BlockEntity belowEntity = level.getBlockEntity(belowPos);
                if (belowEntity != null && isDepot(belowEntity)) {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
            }
        }

        // 切换开关状态
        if (!level.isClientSide) {
            toggleTap(state, level, pos);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private boolean hasFluidCapability(Level level, BlockPos pos, Direction fromDirection) {
        BlockState blockState = level.getBlockState(pos);

        // 检查是否是树叶或铜格栅
        if (blockState.is(BlockTags.LEAVES) || isCopperGrate(blockState)) {
            return true;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return false;
        }

        IFluidHandler capability = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, fromDirection);
        if (capability != null && capability.getTanks() > 0) {
            return true;
        }

        capability = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        return capability != null && capability.getTanks() > 0;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getHorizontalDirection().getOpposite();
        BlockPos blockpos = context.getClickedPos();
        BlockPos attachedPos = blockpos.relative(direction.getOpposite());
        Level level = context.getLevel();

        if (hasFluidCapability(level, attachedPos, direction)) {
            boolean powered = level.hasNeighborSignal(blockpos);
            return this.defaultBlockState()
                    .setValue(FACING, direction)
                    .setValue(POWERED, powered)
                    .setValue(OPEN, powered);
        }

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos testPos = blockpos.relative(dir.getOpposite());
            if (hasFluidCapability(level, testPos, dir)) {
                boolean powered = level.hasNeighborSignal(blockpos);
                return this.defaultBlockState()
                        .setValue(FACING, dir)
                        .setValue(POWERED, powered)
                        .setValue(OPEN, powered);
            }
        }

        return null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction direction = state.getValue(FACING);
        BlockPos attachedPos = pos.relative(direction.getOpposite());
        BlockState attachedState = level.getBlockState(attachedPos);

        // 如果背后是树叶或铜格栅，可以生存
        if (attachedState.is(BlockTags.LEAVES) || isCopperGrate(attachedState)) {
            return true;
        }

        if (level instanceof Level actualLevel) {
            return hasFluidCapability(actualLevel, attachedPos, direction);
        }

        return false;
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
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            Direction facing = state.getValue(FACING);
            BlockPos attachedPos = pos.relative(facing.getOpposite());

            if (fromPos.equals(attachedPos) && !canSurvive(state, level, pos)) {
                level.destroyBlock(pos, true);
                return;
            }

            if (!level.getBlockTicks().willTickThisTick(pos, this)) {
                level.scheduleTick(pos, this, 1);
            }

            if (fromPos.equals(pos.below()) && state.getValue(OPEN)) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof CopperTapBlockEntity tapBE) {
                    tapBE.onTargetChanged();
                }
            }
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel worldIn, BlockPos pos, RandomSource random) {
        boolean previouslyPowered = state.getValue(POWERED);
        boolean currentlyPowered = worldIn.hasNeighborSignal(pos);

        if (previouslyPowered != currentlyPowered) {
            BlockState newState = state.setValue(POWERED, currentlyPowered);

            if (currentlyPowered) {
                if (!state.getValue(OPEN)) {
                    newState = newState.setValue(OPEN, true);
                    worldIn.playSound(null, pos,
                            net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_OPEN,
                            net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f);
                }
            } else {
                if (state.getValue(OPEN) && previouslyPowered) {
                    newState = newState.setValue(OPEN, false);
                    worldIn.playSound(null, pos,
                            net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_CLOSE,
                            net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f);
                }
            }

            worldIn.setBlock(pos, newState, 2);
        }
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (state.getValue(POWERED)) {
            if (!level.isClientSide) {
                level.playSound(null, context.getClickedPos(),
                        net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_CLOSE,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.3f, 2.0f);
            }
            return InteractionResult.SUCCESS;
        }

        if (!level.isClientSide) {
            toggleTap(state, level, context.getClickedPos());
        }
        return InteractionResult.SUCCESS;
    }

    private void toggleTap(BlockState state, Level level, BlockPos pos) {
        boolean isOpen = state.getValue(OPEN);
        level.setBlockAndUpdate(pos, state.setValue(OPEN, !isOpen));

        level.playSound(null, pos, isOpen ?
                        net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_CLOSE :
                        net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_OPEN,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f);
    }

    private boolean isDepot(BlockEntity entity) {
        return entity.getClass().getSimpleName().toLowerCase().contains("depot");
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
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<CopperTapBlockEntity> getBlockEntityClass() {
        return CopperTapBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CopperTapBlockEntity> getBlockEntityType() {
        return CFBlockEntities.COPPER_TAP.get();
    }
}