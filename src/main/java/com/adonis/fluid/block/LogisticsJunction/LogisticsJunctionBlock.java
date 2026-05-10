package com.adonis.fluid.block.LogisticsJunction;

import com.adonis.fluid.registry.CFBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.fluid.FluidHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;

public class LogisticsJunctionBlock extends KineticBlock implements IBE<LogisticsJunctionBlockEntity> {
	public static final MapCodec<LogisticsJunctionBlock> CODEC = simpleCodec(LogisticsJunctionBlock::new);
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

	public LogisticsJunctionBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	public static boolean isCardboardPackage(ItemStack stack) {
		return com.simibubi.create.content.logistics.box.PackageItem.isPackage(stack)
			&& !com.adonis.fluid.item.BrassBoxItem.isBrassBox(stack)
			&& !com.adonis.fluid.item.CopperCanItem.isCopperCan(stack);
	}

	public static boolean acceptsPackage(ItemStack stack) {
		return com.adonis.fluid.item.BrassBoxItem.isBrassBox(stack)
			|| com.adonis.fluid.item.CopperCanItem.isCopperCan(stack)
			|| isCardboardPackage(stack);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
		builder.add(FACING);
		super.createBlockStateDefinition(builder);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction selected = context.getHorizontalDirection().getOpposite();
		for (Direction direction : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
			BlockPos target = context.getClickedPos().relative(direction);
			if (hasAnyContainer(context.getLevel(), target)) {
				selected = direction;
				break;
			}
		}
		return defaultBlockState().setValue(FACING, selected);
	}

	@Override
	public Direction.Axis getRotationAxis(BlockState state) {
		return Direction.Axis.Y;
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face == Direction.DOWN;
	}

	private boolean hasAnyContainer(Level level, BlockPos pos) {
		return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null
			|| level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null) != null;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		InteractionResult result = onBlockEntityUse(level, pos, be -> {
			if (!stack.isEmpty()) {
				if (FluidHelper.tryEmptyItemIntoBE(level, player, hand, stack, be))
					return InteractionResult.sidedSuccess(level.isClientSide);
				if (FluidHelper.tryFillItemFromBE(level, player, hand, stack, be))
					return InteractionResult.sidedSuccess(level.isClientSide);

				if (GenericItemEmptying.canItemBeEmptied(level, stack)
					|| GenericItemFilling.canItemBeFilled(level, stack))
					return InteractionResult.SUCCESS;

				if (acceptsPackage(stack) && be.tryInsert(stack, player))
					return InteractionResult.sidedSuccess(level.isClientSide);
			}
			return InteractionResult.PASS;
		});

		if (result.consumesAction())
			return ItemInteractionResult.sidedSuccess(level.isClientSide);
		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	public Class<LogisticsJunctionBlockEntity> getBlockEntityClass() {
		return LogisticsJunctionBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends LogisticsJunctionBlockEntity> getBlockEntityType() {
		return CFBlockEntities.LOGISTICS_JUNCTION.get();
	}

	@Override
	protected MapCodec<? extends KineticBlock> codec() {
		return CODEC;
	}
}
