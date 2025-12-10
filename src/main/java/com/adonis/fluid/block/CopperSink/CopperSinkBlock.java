// src/main/java/com/adonis/fluid/block/CopperSink/CopperSinkBlock.java
package com.adonis.fluid.block.CopperSink;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.fluid.FluidHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CopperSinkBlock extends HorizontalDirectionalBlock implements IBE<CopperSinkBlockEntity>, IWrenchable {

    public CopperSinkBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(BlockStateProperties.WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BlockStateProperties.WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection().getOpposite(); // 面向玩家
        FluidState fluid = ctx.getLevel().getFluidState(ctx.getClickedPos());
        boolean waterlogged = fluid.getType() == Fluids.WATER;

        return this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(BlockStateProperties.WATERLOGGED, waterlogged);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(BlockStateProperties.WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return state;
    }

    // 完整碰撞箱
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext ctx) {
        return Shapes.block();
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
    public Class<CopperSinkBlockEntity> getBlockEntityClass() {
        return CopperSinkBlockEntity.class;
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntityType<? extends CopperSinkBlockEntity> getBlockEntityType() {
        return com.adonis.fluid.registry.CFBlockEntity.COPPER_SINK.get();
    }

    // 扳手右键旋转
    @Override
    public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}