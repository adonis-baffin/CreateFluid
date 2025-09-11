package com.adonis.fluid.block.Pipette;

import com.adonis.fluid.registry.CFBlockEntity;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PipetteBlock extends KineticBlock implements IBE<PipetteBlockEntity>, ICogWheel {
    public static final BooleanProperty CEILING = BooleanProperty.create("ceiling");

    public PipetteBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(CEILING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(CEILING));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(CEILING, ctx.getClickedFace() == Direction.DOWN);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(CEILING) ? AllShapes.MECHANICAL_ARM_CEILING : AllShapes.MECHANICAL_ARM;
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, world, pos, oldState, isMoving);
        this.withBlockEntityDo(world, pos, PipetteBlockEntity::redstoneUpdate);
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        this.withBlockEntityDo(world, pos, PipetteBlockEntity::redstoneUpdate);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Axis.Y;
    }

    @Override
    public Class<PipetteBlockEntity> getBlockEntityClass() {
        return PipetteBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PipetteBlockEntity> getBlockEntityType() {
        return CFBlockEntity.PIPETTE.get();
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(hand);

        // 护目镜交互
        if (AllItems.GOGGLES.isIn(heldItem)) {
            InteractionResult gogglesResult = this.onBlockEntityUse(world, pos, (be) -> {
                if (be.goggles) {
                    return InteractionResult.PASS;
                } else {
                    be.goggles = true;
                    be.notifyUpdate();
                    return InteractionResult.SUCCESS;
                }
            });
            if (gogglesResult.consumesAction()) {
                return gogglesResult;
            }
        }

        // 空桶交互 - 提取1000mb流体
        if (heldItem.getItem() == Items.BUCKET) {
            return this.onBlockEntityUse(world, pos, (be) -> {
                if (!be.heldFluid.isEmpty() && be.heldFluid.getAmount() >= 1000) {
                    if (!world.isClientSide) {
                        if (player.isCreative()) {
                            // 创造模式：只清空流体，不给物品
                            be.heldFluid.shrink(1000);

                            // 如果流体空了，切换到搜索输入状态
                            if (be.heldFluid.isEmpty()) {
                                be.phase = PipetteBlockEntity.Phase.SEARCH_INPUTS;
                            }

                            // 更新方块实体
                            be.setChanged();
                            be.sendData();

                            // 播放音效
                            world.playSound(null, pos, SoundEvents.BUCKET_EMPTY,
                                    SoundSource.BLOCKS, 1.0F, 1.0F);
                        } else {
                            // 生存模式：正常交换桶
                            ItemStack filledBucket = new ItemStack(be.heldFluid.getFluid().getBucket());

                            // 如果流体没有对应的桶物品，则无法提取
                            if (filledBucket.getItem() == Items.BUCKET) {
                                return InteractionResult.PASS;
                            }

                            // 减少流体
                            be.heldFluid.shrink(1000);

                            // 如果流体空了，切换到搜索输入状态
                            if (be.heldFluid.isEmpty()) {
                                be.phase = PipetteBlockEntity.Phase.SEARCH_INPUTS;
                            }

                            // 更新方块实体
                            be.setChanged();
                            be.sendData();

                            // 处理物品栏
                            heldItem.shrink(1);
                            if (heldItem.isEmpty()) {
                                player.setItemInHand(hand, filledBucket);
                            } else if (!player.getInventory().add(filledBucket)) {
                                player.drop(filledBucket, false);
                            }

                            // 播放音效
                            world.playSound(null, pos, SoundEvents.BUCKET_FILL,
                                    SoundSource.BLOCKS, 1.0F, 1.0F);
                        }
                    }
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.PASS;
            });
        }

        return InteractionResult.PASS;
    }
}