package com.adonis.fluid.block.aqueduct;

import com.adonis.fluid.registry.CFBlockEntity;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.function.Predicate;

public class AqueductBlock extends AbstractAqueductBlock {

    private static final int placementHelperId = PlacementHelpers.register(new AqueductPlacementHelper());

    public AqueductBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<AbstractAqueductBlockEntity> getBlockEntityClass() {
        return (Class<AbstractAqueductBlockEntity>) (Class<?>) AqueductBlockEntity.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public BlockEntityType<? extends AbstractAqueductBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends AbstractAqueductBlockEntity>) CFBlockEntity.AQUEDUCT.get();
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player,
                                 net.minecraft.world.InteractionHand hand, BlockHitResult ray) {
        ItemStack heldItem = player.getItemInHand(hand);

        // 放置辅助
        IPlacementHelper placementHelper = PlacementHelpers.get(placementHelperId);
        if (placementHelper.matchesItem(heldItem)) {
            return placementHelper.getOffset(player, world, state, pos, ray)
                    .placeInWorld(world, (BlockItem) heldItem.getItem(), player, hand, ray);
        }

        return super.use(state, world, pos, player, hand, ray);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);

        if (!level.isClientSide) {
            checkForWaterSource(level, pos, state);
        }
    }

    private void checkForWaterSource(Level level, BlockPos pos, BlockState state) {
        Direction inputDir = state.getValue(FACING).getOpposite();
        BlockPos sourcePos = pos.relative(inputDir);

        if (level.getBlockState(sourcePos).getBlock() == Fluids.WATER.defaultFluidState().createLegacyBlock().getBlock()) {
            withBlockEntityDo(level, pos, be -> {
                if (be instanceof AqueductBlockEntity) {
                    ((AqueductBlockEntity) be).setHasWaterSource(true);
                }
            });
        }
    }

    @MethodsReturnNonnullByDefault
    private static class AqueductPlacementHelper implements IPlacementHelper {

        @Override
        public Predicate<ItemStack> getItemPredicate() {
            return stack -> stack.getItem() instanceof BlockItem &&
                    ((BlockItem) stack.getItem()).getBlock() instanceof AbstractAqueductBlock;
        }

        @Override
        public Predicate<BlockState> getStatePredicate() {
            return state -> state.getBlock() instanceof AbstractAqueductBlock;
        }

        @Override
        public PlacementOffset getOffset(Player player, Level world, BlockState state, BlockPos pos,
                                         BlockHitResult ray) {
            // 获取现有水渠的朝向
            Direction facing = state.getValue(FACING);

            // 优先在水渠的输入或输出方向放置
            List<Direction> directions = IPlacementHelper.orderedByDistanceExceptAxis(
                    pos, ray.getLocation(), Direction.Axis.Y,
                    dir -> {
                        // 只允许在水渠的前后方向放置
                        if (dir != facing && dir != facing.getOpposite()) {
                            return false;
                        }
                        return world.getBlockState(pos.relative(dir)).canBeReplaced();
                    }
            );

            if (directions.isEmpty()) {
                return PlacementOffset.fail();
            }

            return PlacementOffset.success(pos.relative(directions.get(0)),
                    s -> s.setValue(FACING, facing)); // 保持相同朝向
        }
    }
}