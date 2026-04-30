package com.adonis.fluid.item;

import com.adonis.fluid.registry.CFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * 流沙桶 - 右键放置流沙方块，同时可作为流体容器
 */
public class QuicksandBucketItem extends Item {

    public QuicksandBucketItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        if (level.getBlockState(pos).canBeReplaced()) {
            level.setBlock(pos, CFBlocks.QUICKSAND.getDefaultState(), 3);
            Player player = context.getPlayer();
            if (player != null && !player.isCreative()) {
                player.setItemInHand(context.getHand(), new ItemStack(Items.BUCKET));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useOn(context);
    }
}
