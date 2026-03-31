package com.adonis.fluid.block.Pipette;

import com.adonis.fluid.registry.CFBlockEntities;
import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.ItemInteractionResult;
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
    
    public static final MapCodec<PipetteBlock> CODEC = simpleCodec(PipetteBlock::new);
    public static final BooleanProperty CEILING = BooleanProperty.create("ceiling");

    public PipetteBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(CEILING, false));
    }

    @Override
    protected MapCodec<? extends KineticBlock> codec() {
        return CODEC;
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(CEILING) ? AllShapes.MECHANICAL_ARM_CEILING : AllShapes.MECHANICAL_ARM;
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(CEILING) ? AllShapes.MECHANICAL_ARM_CEILING : AllShapes.MECHANICAL_ARM;
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, world, pos, oldState, isMoving);
        this.withBlockEntityDo(world, pos, PipetteBlockEntity::redstoneUpdate);
    }

    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, 
                                   BlockPos fromPos, boolean isMoving) {
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
        return CFBlockEntities.PIPETTE.get();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, 
                                              Player player, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hit) {
        // 护目镜交互
        if (AllItems.GOGGLES.isIn(stack)) {
            return this.onBlockEntityUse(world, pos, (be) -> {
                if (be.goggles) {
                    return InteractionResult.PASS;
                } else {
                    be.goggles = true;
                    be.notifyUpdate();
                    return InteractionResult.SUCCESS;
                }
            }) == InteractionResult.SUCCESS ? 
                ItemInteractionResult.SUCCESS : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // 空桶交互 - 提取1000mb流体
        if (stack.getItem() == Items.BUCKET) {
            InteractionResult result = this.onBlockEntityUse(world, pos, (be) -> {
                if (!be.heldFluid.isEmpty() && be.heldFluid.getAmount() >= 1000) {
                    if (!world.isClientSide) {
                        if (player.isCreative()) {
                            // 创造模式：只清空流体
                            be.heldFluid.shrink(1000);

                            if (be.heldFluid.isEmpty()) {
                                be.phase = PipetteBlockEntity.Phase.SEARCH_INPUTS;
                            }

                            be.setChanged();
                            be.sendData();

                            world.playSound(null, pos, SoundEvents.BUCKET_EMPTY,
                                    SoundSource.BLOCKS, 1.0F, 1.0F);
                        } else {
                            // 生存模式：交换桶
                            ItemStack filledBucket = new ItemStack(be.heldFluid.getFluid().getBucket());

                            if (filledBucket.getItem() == Items.BUCKET) {
                                return InteractionResult.PASS;
                            }

                            be.heldFluid.shrink(1000);

                            if (be.heldFluid.isEmpty()) {
                                be.phase = PipetteBlockEntity.Phase.SEARCH_INPUTS;
                            }

                            be.setChanged();
                            be.sendData();

                            stack.shrink(1);
                            if (stack.isEmpty()) {
                                player.setItemInHand(hand, filledBucket);
                            } else if (!player.getInventory().add(filledBucket)) {
                                player.drop(filledBucket, false);
                            }

                            world.playSound(null, pos, SoundEvents.BUCKET_FILL,
                                    SoundSource.BLOCKS, 1.0F, 1.0F);
                        }
                    }
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.PASS;
            });
            
            return result == InteractionResult.SUCCESS ? 
                ItemInteractionResult.SUCCESS : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}