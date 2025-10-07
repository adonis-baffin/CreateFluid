package com.adonis.fluid.block.FluidInterface;

import com.adonis.fluid.registry.CFBlock;
import com.adonis.fluid.registry.CFBlockEntity;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.simibubi.create.foundation.fluid.FluidHelper.FluidExchange;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import javax.annotation.Nullable;

public class FluidInterfaceBlock extends HorizontalDirectionalBlock implements IBE<FluidInterfaceBlockEntity>, IWrenchable {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    // 定义正确方向的形状，第一层上边缘下降0.1
    // NORTH: 向北伸出（贴在南边方块上）
    private static final VoxelShape NORTH_LAYER_1 = Block.box(3, 3, 14, 13, 12.9, 16);
    private static final VoxelShape NORTH_LAYER_2 = Block.box(4, 4, 13, 12, 12, 14);
    private static final VoxelShape NORTH_LAYER_3 = Block.box(5, 5, 11, 11, 11, 13);
    private static final VoxelShape NORTH_SHAPE = Shapes.or(NORTH_LAYER_1, NORTH_LAYER_2, NORTH_LAYER_3);

    // SOUTH: 向南伸出（贴在北边方块上）
    private static final VoxelShape SOUTH_LAYER_1 = Block.box(3, 3, 0, 13, 12.9, 2);
    private static final VoxelShape SOUTH_LAYER_2 = Block.box(4, 4, 2, 12, 12, 3);
    private static final VoxelShape SOUTH_LAYER_3 = Block.box(5, 5, 3, 11, 11, 5);
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(SOUTH_LAYER_1, SOUTH_LAYER_2, SOUTH_LAYER_3);

    // EAST: 向东伸出（贴在西边方块上）
    private static final VoxelShape EAST_LAYER_1 = Block.box(0, 3, 3, 2, 12.9, 13);
    private static final VoxelShape EAST_LAYER_2 = Block.box(2, 4, 4, 3, 12, 12);
    private static final VoxelShape EAST_LAYER_3 = Block.box(3, 5, 5, 5, 11, 11);
    private static final VoxelShape EAST_SHAPE = Shapes.or(EAST_LAYER_1, EAST_LAYER_2, EAST_LAYER_3);

    // WEST: 向西伸出（贴在东边方块上）
    private static final VoxelShape WEST_LAYER_1 = Block.box(14, 3, 3, 16, 12.9, 13);
    private static final VoxelShape WEST_LAYER_2 = Block.box(13, 4, 4, 14, 12, 12);
    private static final VoxelShape WEST_LAYER_3 = Block.box(11, 5, 5, 13, 11, 11);
    private static final VoxelShape WEST_SHAPE = Shapes.or(WEST_LAYER_1, WEST_LAYER_2, WEST_LAYER_3);

    public FluidInterfaceBlock(Properties properties) {
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

    // 辅助方法：检查方块是否有流体存储能力 - 支持树叶
    private boolean hasFluidCapability(LevelReader level, BlockPos pos, Direction fromDirection) {
        BlockState blockState = level.getBlockState(pos);

        // 检查是否是树叶（可以作为无限水源）
        if (blockState.is(BlockTags.LEAVES)) {
            return true;
        }

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
        BlockState attachedState = level.getBlockState(attachedPos);

        // 如果背后是树叶，可以生存
        if (attachedState.is(BlockTags.LEAVES)) {
            return true;
        }

        // 需要背后的方块有流体存储能力才能存活
        return hasFluidCapability(level, attachedPos, direction);
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

    // 流体交互功能
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide())
            return InteractionResult.SUCCESS;

        if (player instanceof FakePlayer)
            return InteractionResult.PASS;

        ItemStack stack = player.getItemInHand(hand);

        // 检查是否可以进行流体交互
        if (!GenericItemEmptying.canItemBeEmptied(level, stack) && !GenericItemFilling.canItemBeFilled(level, stack))
            return InteractionResult.PASS;

        // 关键修复：获取流体接口贴附的方块（在接口的相反方向）
        Direction attachedDirection = state.getValue(FACING).getOpposite();
        BlockPos targetPos = pos.relative(attachedDirection);
        BlockState targetState = level.getBlockState(targetPos);

        IFluidHandler tankCapability;

        // 特殊处理：如果是含水树叶，创建虚拟的无限水源处理器
        if (targetState.is(BlockTags.LEAVES) &&
                targetState.hasProperty(BlockStateProperties.WATERLOGGED) &&
                targetState.getValue(BlockStateProperties.WATERLOGGED)) {
            tankCapability = new WaterloggedBlockFluidHandler();
        } else {
            // 正常获取流体能力
            BlockEntity targetBlockEntity = level.getBlockEntity(targetPos);
            if (targetBlockEntity == null)
                return InteractionResult.FAIL;

            // 尝试从目标方块获取流体处理能力
            tankCapability = targetBlockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, state.getValue(FACING)).orElse(null);

            // 如果没有，尝试获取默认的流体处理能力
            if (tankCapability == null)
                tankCapability = targetBlockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
        }

        if (tankCapability == null)
            return InteractionResult.FAIL;

        FluidExchange exchange = null;
        FluidStack fluidStack = FluidStack.EMPTY;

        // 尝试将物品中的流体倒入容器
        if (GenericItemEmptying.canItemBeEmptied(level, stack)) {
            fluidStack = tryEmptyItem(level, player, hand, stack, targetPos, tankCapability);
            if (!fluidStack.isEmpty()) {
                exchange = FluidExchange.ITEM_TO_TANK;
            }
        }

        // 如果倒入失败，尝试从容器中取出流体
        if (exchange == null && GenericItemFilling.canItemBeFilled(level, stack)) {
            fluidStack = tryFillItem(level, player, hand, stack, targetPos, tankCapability);
            if (!fluidStack.isEmpty()) {
                exchange = FluidExchange.TANK_TO_ITEM;
            }
        }

        if (exchange == null)
            return InteractionResult.FAIL;

        // 播放声音效果
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

        return InteractionResult.SUCCESS;
    }

    private FluidStack tryEmptyItem(Level level, Player player, InteractionHand hand, ItemStack stack,
                                    BlockPos targetPos, IFluidHandler capability) {
        if (!GenericItemEmptying.canItemBeEmptied(level, stack))
            return FluidStack.EMPTY;

        // 模拟倒入
        ItemStack copy = stack.copy();
        var result = GenericItemEmptying.emptyItem(level, copy, true);
        FluidStack fluidStack = result.getFirst();

        if (fluidStack.isEmpty())
            return FluidStack.EMPTY;

        // 检查容器是否能接受这些流体
        int filled = capability.fill(fluidStack, FluidAction.SIMULATE);
        if (filled != fluidStack.getAmount())
            return FluidStack.EMPTY;

        // 如果是客户端，返回
        if (level.isClientSide)
            return fluidStack;

        // 实际执行倒入
        copy = stack.copy();
        result = GenericItemEmptying.emptyItem(level, copy, false);
        ItemStack resultItem = result.getSecond();

        capability.fill(fluidStack.copy(), FluidAction.EXECUTE);

        BlockEntity targetBlockEntity = level.getBlockEntity(targetPos);
        if (targetBlockEntity != null) {
            targetBlockEntity.setChanged();
            if (level instanceof ServerLevel serverLevel)
                serverLevel.getChunkSource().blockChanged(targetPos);
        }

        // 更新玩家物品
        if (!player.isCreative() && !(targetBlockEntity instanceof CreativeFluidTankBlockEntity)) {
            if (stack.getCount() == 1) {
                player.setItemInHand(hand, resultItem);
            } else {
                stack.shrink(1);
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

        // 遍历所有储罐
        for (int i = 0; i < capability.getTanks(); i++) {
            FluidStack fluidStack = capability.getFluidInTank(i);
            if (fluidStack.isEmpty())
                continue;

            // 检查物品需要多少流体
            int requiredAmount = GenericItemFilling.getRequiredAmountForItem(level, stack, fluidStack.copy());
            if (requiredAmount == -1 || requiredAmount > fluidStack.getAmount())
                continue;

            // 如果是客户端，返回
            if (level.isClientSide)
                return fluidStack;

            // 准备要填充的物品
            ItemStack fillStack = stack;
            if (player.isCreative() || targetBlockEntity instanceof CreativeFluidTankBlockEntity)
                fillStack = stack.copy();

            // 填充物品
            ItemStack result = GenericItemFilling.fillItem(level, requiredAmount, fillStack, fluidStack.copy());

            // 从容器中抽取流体
            FluidStack drainFluid = fluidStack.copy();
            drainFluid.setAmount(requiredAmount);
            capability.drain(drainFluid, FluidAction.EXECUTE);

            // 更新玩家物品
            if (!player.isCreative()) {
                if (stack.getCount() == 1 && result.getCount() == 1) {
                    player.setItemInHand(hand, result);
                } else {
                    stack.shrink(1);
                    player.getInventory().placeItemBackInInventory(result);
                }
            }

            if (targetBlockEntity != null) {
                targetBlockEntity.setChanged();
                if (level instanceof ServerLevel serverLevel)
                    serverLevel.getChunkSource().blockChanged(targetPos);
            }

            return drainFluid;
        }

        return FluidStack.EMPTY;
    }

    /**
     * 内部类：模拟含水方块（树叶）作为无限水源
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
        return CFBlockEntity.FLUID_INTERFACE.get();
    }
}