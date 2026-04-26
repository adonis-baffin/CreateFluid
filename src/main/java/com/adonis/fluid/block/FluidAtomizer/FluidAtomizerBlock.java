package com.adonis.fluid.block.FluidAtomizer;

import com.adonis.fluid.registry.CFBlockEntities;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.fluid.FluidHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.InteractionHand;

public class FluidAtomizerBlock extends DirectionalKineticBlock implements IBE<FluidAtomizerBlockEntity> {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public FluidAtomizerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction preferredFacing = getPreferredFacing(context);
        if (preferredFacing == null) {
            preferredFacing = context.getNearestLookingDirection();
        }
        Direction facing = context.getPlayer() != null && context.getPlayer().isShiftKeyDown()
                ? preferredFacing
                : preferredFacing.getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level world, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = onBlockEntityUse(world, pos, be -> {
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

        if (result.consumesAction()) {
            return ItemInteractionResult.sidedSuccess(world.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public Class<FluidAtomizerBlockEntity> getBlockEntityClass() {
        return FluidAtomizerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FluidAtomizerBlockEntity> getBlockEntityType() {
        return CFBlockEntities.FLUID_ATOMIZER.get();
    }
}
