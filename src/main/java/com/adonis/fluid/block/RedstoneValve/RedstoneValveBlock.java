package com.adonis.fluid.block.RedstoneValve;

import com.adonis.fluid.registry.CFBlockEntities;
import com.simibubi.create.AllShapes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.IAxisPipe;
import com.simibubi.create.foundation.block.IBE;
import com.mojang.serialization.MapCodec;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.TickPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.NotNull;

public class RedstoneValveBlock extends Block
        implements IAxisPipe, IBE<RedstoneValveBlockEntity>, IWrenchable {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty AXIS_ALONG_FIRST_COORDINATE =
            BooleanProperty.create("axis_along_first");
    public static final BooleanProperty ENABLED = BooleanProperty.create("enabled");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public static final MapCodec<RedstoneValveBlock> CODEC = simpleCodec(RedstoneValveBlock::new);

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    public RedstoneValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.UP)
                .setValue(AXIS_ALONG_FIRST_COORDINATE, false)
                .setValue(ENABLED, true) // 默认常开
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(FACING, AXIS_ALONG_FIRST_COORDINATE, ENABLED, WATERLOGGED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos,
                               CollisionContext context) {
        return AllShapes.FLUID_VALVE.get(getPipeAxis(state));
    }

    // ========== 朝向与管道轴逻辑（复刻自 FluidValveBlock / DirectionalAxisKineticBlock） ==========

    @NotNull
    public static Axis getPipeAxis(BlockState state) {
        if (!(state.getBlock() instanceof RedstoneValveBlock))
            throw new IllegalStateException("Provided BlockState is for a different block.");
        Direction facing = state.getValue(FACING);
        boolean alongFirst = !state.getValue(AXIS_ALONG_FIRST_COORDINATE);
        for (Axis axis : Iterate.axes) {
            if (axis == facing.getAxis())
                continue;
            if (!alongFirst) {
                alongFirst = true;
                continue;
            }
            return axis;
        }
        throw new IllegalStateException("Impossible axis.");
    }

    @Override
    public Axis getAxis(BlockState state) {
        return getPipeAxis(state);
    }

    public static boolean isOpenAt(BlockState state, Direction d) {
        return d.getAxis() == getPipeAxis(state);
    }

    // ========== 放置逻辑（复刻自 DirectionalAxisKineticBlock） ==========

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getNearestLookingDirection().getOpposite();
        BlockState state = defaultBlockState().setValue(FACING, facing);

        // 尝试根据相邻管道选择正确的管道轴取向
        state = pickCorrectAxis(state, context);

        // 红石检测
        boolean powered = context.getLevel().hasNeighborSignal(context.getClickedPos());
        state = state.setValue(ENABLED, !powered);

        // 含水检测
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        if (fluidState.getType() == Fluids.WATER) {
            state = state.setValue(WATERLOGGED, true);
        }

        return state;
    }

    /**
     * 根据管道连接偏好选择正确的 AXIS_ALONG_FIRST_COORDINATE
     */
    protected BlockState pickCorrectAxis(BlockState state, BlockPlaceContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();

        boolean originalValue = state.getValue(AXIS_ALONG_FIRST_COORDINATE);

        // 尝试两种取向，优先连接到管道的那个
        for (int i = 0; i < 2; i++) {
            state = state.setValue(AXIS_ALONG_FIRST_COORDINATE, i == 0);
            Axis pipeAxis = getPipeAxis(state);
            Direction d1 = Direction.get(Direction.AxisDirection.POSITIVE, pipeAxis);
            Direction d2 = Direction.get(Direction.AxisDirection.NEGATIVE, pipeAxis);

            boolean connect1 = prefersConnectionTo(world, pos, d1);
            boolean connect2 = prefersConnectionTo(world, pos, d2);

            if (connect1 || connect2)
                return state;
        }

        return state.setValue(AXIS_ALONG_FIRST_COORDINATE, originalValue);
    }

    protected boolean prefersConnectionTo(LevelReader reader, BlockPos pos, Direction facing) {
        BlockPos offset = pos.relative(facing);
        BlockState blockState = reader.getBlockState(offset);
        return FluidPipeBlock.canConnectTo(reader, offset, blockState, facing);
    }

    // ========== 红石控制 ==========

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block otherBlock,
                                BlockPos neighborPos, boolean isMoving) {
        if (world.isClientSide)
            return;

        // 红石信号检测
        boolean powered = world.hasNeighborSignal(pos);
        boolean currentlyEnabled = state.getValue(ENABLED);
        boolean shouldBeEnabled = !powered; // 有信号 → 关闭

        if (currentlyEnabled != shouldBeEnabled) {
            BlockState newState = state.setValue(ENABLED, shouldBeEnabled);
            world.setBlockAndUpdate(pos, newState);
            FluidPropagator.propagateChangedPipe(world, pos, newState);
        }

        // 管道邻居变化检测
        Direction d = FluidPropagator.validateNeighbourChange(state, world, pos, otherBlock, neighborPos, isMoving);
        if (d == null)
            return;
        if (!isOpenAt(state, d))
            return;
        world.scheduleTick(pos, this, 1, TickPriority.HIGH);
    }

    // ========== 管道生命周期 ==========

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        boolean blockTypeChanged = !state.is(newState.getBlock());
        if (blockTypeChanged && !world.isClientSide)
            FluidPropagator.propagateChangedPipe(world, pos, state);
        super.onRemove(state, world, pos, newState, isMoving);
    }

    // ========== 扳手交互 ==========

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        
        // 使用 IWrenchable 默认的旋转逻辑
        BlockState rotated = IWrenchable.super.getRotatedBlockState(state, context.getClickedFace());
        if (!rotated.canSurvive(level, pos))
            return InteractionResult.PASS;

        level.setBlockAndUpdate(pos, rotated);
        if (level.getBlockState(pos) != state)
            IWrenchable.playRotateSound(level, pos);

        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (!(world instanceof ServerLevel serverLevel))
            return InteractionResult.SUCCESS;

        // 触发方块破坏事件，允许其他模组取消
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(world, pos, world.getBlockState(pos), player);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled())
            return InteractionResult.SUCCESS;

        // 掉落物品
        if (player != null && !player.isCreative()) {
            Block.getDrops(state, serverLevel, pos, world.getBlockEntity(pos), player, context.getItemInHand())
                .forEach(itemStack -> player.getInventory().placeItemBackInInventory(itemStack));
        }

        state.spawnAfterBreak(serverLevel, pos, ItemStack.EMPTY, true);
        world.destroyBlock(pos, false);
        IWrenchable.playRemoveSound(world, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, world, pos, oldState, isMoving);
        if (world.isClientSide)
            return;
        if (state != oldState)
            world.scheduleTick(pos, this, 1, TickPriority.HIGH);
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource r) {
        FluidPropagator.propagateChangedPipe(world, pos, state);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return true;
    }

    // NeoForge 1.21: isPathfindable 方法签名已改变，不使用 @Override
    public boolean isPathfindable(BlockState state, BlockGetter reader, BlockPos pos, PathComputationType type) {
        return false;
    }

    // ========== BlockEntity ==========

    @Override
    public Class<RedstoneValveBlockEntity> getBlockEntityClass() {
        return RedstoneValveBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RedstoneValveBlockEntity> getBlockEntityType() {
        return CFBlockEntities.REDSTONE_VALVE.get();
    }

    // ========== 含水 ==========

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
                                  LevelAccessor world, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) {
            world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }
        return state;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }
}
