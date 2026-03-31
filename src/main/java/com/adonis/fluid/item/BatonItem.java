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

public class BatonItem extends Item {
    public BatonItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level world, BlockPos pos, Player player) {
        if (BatonInteractionHandler.isInSelectionMode()) {
            return false;
        }
        return true;
    }

    public boolean onBlockStartBreak(ItemStack itemstack, BlockPos pos, Player player) {
        if (BatonInteractionHandler.isInSelectionMode()) {
            return true;
        }
        return false;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (BatonInteractionHandler.isInSelectionMode()) {
            return 0.0F;
        }
        return super.getDestroySpeed(stack, state);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide && BatonInteractionHandler.isInSelectionMode()) {
            BatonInteractionHandler.cancelSelection();
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
    }
}