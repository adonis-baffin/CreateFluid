package com.adonis.fluid.block.LogisticsJunction;

import com.adonis.fluid.item.BrassBoxItem;
import com.adonis.fluid.item.CopperCanItem;
import com.adonis.fluid.registry.CFBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.logistics.box.PackageItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;

public class LogisticsJunctionBlock extends BaseEntityBlock {
	public static final MapCodec<LogisticsJunctionBlock> CODEC = simpleCodec(LogisticsJunctionBlock::new);
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

	public LogisticsJunctionBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	public static boolean isCardboardPackage(ItemStack stack) {
		return PackageItem.isPackage(stack) && !BrassBoxItem.isBrassBox(stack) && !CopperCanItem.isCopperCan(stack);
	}

	public static boolean acceptsPackage(ItemStack stack) {
		return BrassBoxItem.isBrassBox(stack) || CopperCanItem.isCopperCan(stack) || isCardboardPackage(stack);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction selected = Direction.NORTH;
		for (Direction direction : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
			BlockPos target = context.getClickedPos().relative(direction);
			if (hasAnyContainer(context.getLevel(), target)) {
				selected = direction;
				break;
			}
		}
		return defaultBlockState().setValue(FACING, selected);
	}

	private boolean hasAnyContainer(Level level, BlockPos pos) {
		return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null
			|| level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null) != null;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (!acceptsPackage(stack))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (!(level.getBlockEntity(pos) instanceof LogisticsJunctionBlockEntity junction))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (!junction.tryInsert(stack, player))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		return ItemInteractionResult.SUCCESS;
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new LogisticsJunctionBlockEntity(CFBlockEntities.LOGISTICS_JUNCTION.get(), pos, state);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return (l, p, s, be) -> {
			if (be instanceof LogisticsJunctionBlockEntity junction)
				junction.tickServer();
		};
	}
}
