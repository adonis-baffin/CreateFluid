package com.adonis.fluid.block.FluidInterface;

import com.adonis.fluid.registry.CFBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.simibubi.create.foundation.fluid.FluidHelper.FluidExchange;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import javax.annotation.Nullable;

public class FluidInterfaceBlock extends HorizontalDirectionalBlock implements IBE<FluidInterfaceBlockEntity>, IWrenchable {

    public static final MapCodec<FluidInterfaceBlock> CODEC = simpleCodec(FluidInterfaceBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    // 定义形状
    private static final VoxelShape NORTH_LAYER_1 = Block.box(3, 3, 14, 13, 12.9, 16);
    private static final VoxelShape NORTH_LAYER_2 = Block.box(4, 4, 13, 12, 12, 14);
    private static final VoxelShape NORTH_LAYER_3 = Block.box(5, 5, 11, 11, 11, 13);
    private static final VoxelShape NORTH_SHAPE = Shapes.or(NORTH_LAYER_1, NORTH_LAYER_2, NORTH_LAYER_3);

    private static final VoxelShape SOUTH_LAYER_1 = Block.box(3, 3, 0, 13, 12.9, 2);
    private static final VoxelShape SOUTH_LAYER_2 = Block.box(4, 4, 2, 12, 12, 3);
    private static final VoxelShape SOUTH_LAYER_3 = Block.box(5, 5, 3, 11, 11, 5);
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(SOUTH_LAYER_1, SOUTH_LAYER_2, SOUTH_LAYER_3);

    private static final VoxelShape EAST_LAYER_1 = Block.box(0, 3, 3, 2, 12.9, 13);
    private static final VoxelShape EAST_LAYER_2 = Block.box(2, 4, 4, 3, 12, 12);
    private static final VoxelShape EAST_LAYER_3 = Block.box(3, 5, 5, 5, 11, 11);
    private static final VoxelShape EAST_SHAPE = Shapes.or(EAST_LAYER_1, EAST_LAYER_2, EAST_LAYER_3);

    private static final VoxelShape WEST_LAYER_1 = Block.box(14, 3, 3, 16, 12.9, 13);
    private static final VoxelShape WEST_LAYER_2 = Block.box(13, 4, 4, 14, 12, 12);
    private static final VoxelShape WEST_LAYER_3 = Block.box(11, 5, 5, 13, 11, 11);
    private static final VoxelShape WEST_SHAPE = Shapes.or(WEST_LAYER_1, WEST_LAYER_2, WEST_LAYER_3);

    public FluidInterfaceBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
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

    // 检查方块是否有流体存储能力 - 支持树叶和铜格栅
    private boolean hasFluidCapability(Level level, BlockPos pos, Direction fromDirection) {
        BlockState blockState = level.getBlockState(pos);

        // 检查是否是树叶或铜格栅（都可以作为无限水源）
        if (blockState.is(BlockTags.LEAVES) || isCopperGrate(blockState)) {
            return true;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return false;
        }

        // 尝试从指定方向获取流体能力
        IFluidHandler capability = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, fromDirection);
        if (capability != null && capability.getTanks() > 0) {
            return true;
        }

        // 尝试获取默认流体能力
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

        // 检查是否可以贴在这个方向
        if (hasFluidCapability(level, attachedPos, direction)) {
            return this.defaultBlockState().setValue(FACING, direction);
        }

        // 尝试其他水平方向
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos testPos = blockpos.relative(dir.getOpposite());
            if (hasFluidCapability(level, testPos, dir)) {
                return this.defaultBlockState().setValue(FACING, dir);
            }
        }

        return null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction direction = state.getValue(FACING);
        BlockPos attachedPos = pos.relative(direction.getOpposite());
        BlockState attachedState = level.getBlockState(attachedPos);

        // 如果背后是树叶或铜格栅，可以生存
        if (attachedState.is(BlockTags.LEAVES) || isCopperGrate(attachedState)) {
            return true;
        }

        // 如果是 Level 实例，检查流体能力
        if (level instanceof Level actualLevel) {
            return hasFluidCapability(actualLevel, attachedPos, direction);
        }

        // 回退：至少检查背后有方块
        return !attachedState.isAir();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (direction.getOpposite() == state.getValue(FACING) && !state.canSurvive(level, currentPos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide())
            return ItemInteractionResult.SUCCESS;

        if (player instanceof FakePlayer)
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        // 检查是否可以进行流体交互
        if (!GenericItemEmptying.canItemBeEmptied(level, stack) && !GenericItemFilling.canItemBeFilled(level, stack))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        // 获取流体接口贴附的方块
        Direction attachedDirection = state.getValue(FACING).getOpposite();
        BlockPos targetPos = pos.relative(attachedDirection);
        BlockState targetState = level.getBlockState(targetPos);

        IFluidHandler tankCapability;

        // 特殊处理：如果是含水树叶或含水铜格栅，创建虚拟的无限水源处理器
        if ((targetState.is(BlockTags.LEAVES) || isCopperGrate(targetState)) &&
                targetState.hasProperty(BlockStateProperties.WATERLOGGED) &&
                targetState.getValue(BlockStateProperties.WATERLOGGED)) {
            tankCapability = new WaterloggedBlockFluidHandler();
        } else {
            // 正常获取流体能力
            tankCapability = level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, state.getValue(FACING));
            if (tankCapability == null)
                tankCapability = level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, null);
        }

        if (tankCapability == null)
            return ItemInteractionResult.FAIL;

        FluidExchange exchange;
        FluidStack fluidStack;

        // 尝试倒入或取出流体
        if (!(fluidStack = tryEmptyItem(level, player, hand, stack, targetPos, tankCapability)).isEmpty()) {
            exchange = FluidExchange.ITEM_TO_TANK;
        } else if (!(fluidStack = tryFillItem(level, player, hand, stack, targetPos, tankCapability)).isEmpty()) {
            exchange = FluidExchange.TANK_TO_ITEM;
        } else {
            return ItemInteractionResult.FAIL;
        }

        // 播放声音
        SoundEvent soundevent = switch (exchange) {
            case ITEM_TO_TANK -> FluidHelper.getEmptySound(fluidStack);
            case TANK_TO_ITEM -> FluidHelper.getFillSound(fluidStack);
        };

        if (soundevent != null) {
            float pitch = Mth.clamp(1 - (fluidStack.getAmount() / 16000f), 0, 1);
            pitch /= 1.5f;
            pitch += .5f;
            pitch += (level.random.nextFloat() - .5f) / 4f;
            level.playSound(null, pos, soundevent, SoundSource.BLOCKS, .5f, pitch);
        }

        return ItemInteractionResult.SUCCESS;
    }

    private FluidStack tryEmptyItem(Level level, Player player, InteractionHand hand, ItemStack stack,
                                    BlockPos targetPos, IFluidHandler capability) {
        if (!GenericItemEmptying.canItemBeEmptied(level, stack))
            return FluidStack.EMPTY;

        Pair<FluidStack, ItemStack> result = GenericItemEmptying.emptyItem(level, stack, true);
        FluidStack fluidStack = result.getFirst();

        if (fluidStack.isEmpty())
            return FluidStack.EMPTY;

        int filled = capability.fill(fluidStack, FluidAction.SIMULATE);
        if (filled != fluidStack.getAmount())
            return FluidStack.EMPTY;

        if (level.isClientSide)
            return fluidStack;

        ItemStack copy = stack.copy();
        result = GenericItemEmptying.emptyItem(level, copy, false);
        ItemStack resultItem = result.getSecond();

        capability.fill(fluidStack.copy(), FluidAction.EXECUTE);

        BlockEntity targetBlockEntity = level.getBlockEntity(targetPos);
        if (targetBlockEntity != null) {
            targetBlockEntity.setChanged();
            if (level instanceof ServerLevel serverLevel)
                serverLevel.getChunkSource().blockChanged(targetPos);
        }

        if (!player.isCreative() && !(targetBlockEntity instanceof CreativeFluidTankBlockEntity)) {
            if (copy.isEmpty()) {
                player.setItemInHand(hand, resultItem);
            } else {
                player.setItemInHand(hand, copy);
                player.getInventory().placeItemBackInInventory(resultItem);
            }
        }

        return fluidStack;
    }

    private FluidStack tryFillItem(Level level, Player player, InteractionHand hand, ItemStack stack,
                                   BlockPos targetPos, IFluidHandler capability) {
        if (!GenericItemFilling.canItemBeFilled(level, stack))
            return FluidStack.EMPTY;

        BlockEntity targetBlockEntity = level.getBlockEntity(targetPos);

        for (int i = 0; i < capability.getTanks(); i++) {
            FluidStack fluidStack = capability.getFluidInTank(i);
            if (fluidStack.isEmpty())
                continue;

            int requiredAmount = GenericItemFilling.getRequiredAmountForItem(level, stack, fluidStack.copy());
            if (requiredAmount == -1 || requiredAmount > fluidStack.getAmount())
                continue;

            if (level.isClientSide)
                return fluidStack;

            ItemStack fillStack = stack;
            if (player.isCreative() || targetBlockEntity instanceof CreativeFluidTankBlockEntity)
                fillStack = stack.copy();

            ItemStack result = GenericItemFilling.fillItem(level, requiredAmount, fillStack, fluidStack.copy());

            FluidStack drainFluid = fluidStack.copy();
            drainFluid.setAmount(requiredAmount);
            capability.drain(drainFluid, FluidAction.EXECUTE);

            if (!player.isCreative()) {
                player.getInventory().placeItemBackInInventory(result);
            }

            if (targetBlockEntity != null) {
                targetBlockEntity.setChanged();
                if (level instanceof ServerLevel serverLevel)
                    serverLevel.getChunkSource().blockChanged(targetPos);
            }

            return fluidStack.copyWithAmount(requiredAmount);
        }

        return FluidStack.EMPTY;
    }

    /**
     * 内部类：模拟含水方块（树叶/铜格栅）作为无限水源
     */
    private static class WaterloggedBlockFluidHandler implements IFluidHandler {
        private static final FluidStack WATER = new FluidStack(Fluids.WATER, 1000);

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return WATER.copy();
        }

        @Override
        public int getTankCapacity(int tank) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return false; // 不能往含水方块里填充流体
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0; // 不能往含水方块里填充流体
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.getFluid() == Fluids.WATER) {
                return new FluidStack(Fluids.WATER, Math.min(resource.getAmount(), 1000));
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return new FluidStack(Fluids.WATER, Math.min(maxDrain, 1000));
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<FluidInterfaceBlockEntity> getBlockEntityClass() {
        return FluidInterfaceBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FluidInterfaceBlockEntity> getBlockEntityType() {
        return CFBlockEntities.FLUID_INTERFACE.get();
    }
}