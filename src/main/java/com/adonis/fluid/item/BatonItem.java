package com.adonis.fluid.item;

import com.adonis.fluid.handler.BatonInteractionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.ToolAction;

import java.util.function.Consumer;

public class BatonItem extends Item {
    public BatonItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        // Check if this block can be an interaction point
        if (canBeInteractionPoint(level, pos, state)) {
            // Prevent placing the baton on interaction points
            return InteractionResult.SUCCESS;
        }

        // Check if it's an arm or pipette
        if (level.getBlockEntity(pos) instanceof com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity ||
                level.getBlockEntity(pos) instanceof com.adonis.fluid.block.Pipette.PipetteBlockEntity) {
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level world, BlockPos pos, Player player) {
        // In selection mode, completely prevent breaking
        if (BatonInteractionHandler.isInSelectionMode()) {
            return false;
        }
        return true;
    }

    @Override
    public boolean onBlockStartBreak(ItemStack itemstack, BlockPos pos, Player player) {
        // Double insurance: also prevent at break start
        if (BatonInteractionHandler.isInSelectionMode()) {
            return true; // Return true to prevent breaking
        }
        return false;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, net.minecraft.world.entity.LivingEntity entity) {
        // Triple insurance: prevent during mining
        if (BatonInteractionHandler.isInSelectionMode()) {
            return false;
        }
        return super.mineBlock(stack, level, state, pos, entity);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        // In selection mode, destroy speed is 0
        if (BatonInteractionHandler.isInSelectionMode()) {
            return 0.0F;
        }
        return super.getDestroySpeed(stack, state);
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        // Don't allow any tool actions
        return false;
    }

    private boolean canBeInteractionPoint(Level level, BlockPos pos, BlockState state) {
        // Check for arm interaction points
        if (com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint.isInteractable(level, pos, state)) {
            return true;
        }
        // Check for pipette interaction points
        if (com.adonis.fluid.content.pipette.FluidInteractionPoint.create(level, pos, state) != null) {
            return true;
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // Right-click in air to exit selection mode
        if (level.isClientSide && BatonInteractionHandler.isInSelectionMode()) {
            BatonInteractionHandler.cancelSelection();
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            public ItemStack getDefaultInstance() {
                return new ItemStack(BatonItem.this);
            }
        });
    }
}