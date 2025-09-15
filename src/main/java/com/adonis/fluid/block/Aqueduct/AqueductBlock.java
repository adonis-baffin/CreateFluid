package com.adonis.fluid.block.Aqueduct;

import com.adonis.fluid.registry.CFBlockEntity;
import com.simibubi.create.foundation.block.IBE;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

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

        if (!level.isClientSide && !oldState.is(state.getBlock())) {
            // 通知水渠方块它被放置了
            withBlockEntityDo(level, pos, be -> {
                if (be instanceof AqueductBlockEntity aqueduct) {
                    aqueduct.onBlockPlaced();
                }
            });

            // 通知相邻水渠更新
            notifyNeighborAqueducts(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide) {
                // 通知水渠方块它将被移除
                withBlockEntityDo(level, pos, be -> {
                    if (be instanceof AqueductBlockEntity aqueduct) {
                        aqueduct.onBlockRemoved();
                    }
                });

                // 通知相邻水渠更新
                notifyNeighborAqueducts(level, pos);
            }

            // 调用IBE的默认移除逻辑
            IBE.onRemove(state, level, pos, newState);
        }
    }

    private void notifyNeighborAqueducts(Level level, BlockPos pos) {
        // 通知前后的水渠更新状态
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);

            if (neighborState.getBlock() instanceof AbstractAqueductBlock) {
                // 触发邻居水渠的重新检查
                level.sendBlockUpdated(neighborPos, neighborState, neighborState, 3);

                // 如果邻居是水渠，让它重新检查流体源
                withBlockEntityDo(level, neighborPos, be -> {
                    if (be instanceof AqueductBlockEntity neighbor) {
                        neighbor.checkFluidSource();
                    }
                });
            }
        }

        // 触发系统重新检查
        AqueductBlockEntity.triggerSystemRecheck();
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
            Direction facing = state.getValue(FACING);

            // 只允许在水渠的前后方向放置
            Direction[] validDirections = new Direction[] { facing, facing.getOpposite() };

            for (Direction dir : validDirections) {
                BlockPos targetPos = pos.relative(dir);
                BlockState targetState = world.getBlockState(targetPos);

                if (targetState.canBeReplaced()) {
                    // 返回与现有水渠相同朝向的放置
                    return PlacementOffset.success(targetPos,
                            s -> s.setValue(FACING, facing));
                }
            }

            return PlacementOffset.fail();
        }
    }
}