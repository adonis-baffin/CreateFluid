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
        // 不需要特殊处理，让事件监听器处理
        return InteractionResult.PASS;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level world, BlockPos pos, Player player) {
        // 在选择模式下，完全阻止破坏
        if (BatonInteractionHandler.isInSelectionMode()) {
            return false;
        }
        return true;
    }

    @Override
    public boolean onBlockStartBreak(ItemStack itemstack, BlockPos pos, Player player) {
        // 双重保险：在开始破坏时也阻止
        if (BatonInteractionHandler.isInSelectionMode()) {
            return true; // 返回true阻止破坏
        }
        return false;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, net.minecraft.world.entity.LivingEntity entity) {
        // 三重保险：在挖掘期间阻止
        if (BatonInteractionHandler.isInSelectionMode()) {
            return false;
        }
        return super.mineBlock(stack, level, state, pos, entity);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        // 在选择模式下，破坏速度为0
        if (BatonInteractionHandler.isInSelectionMode()) {
            return 0.0F;
        }
        return super.getDestroySpeed(stack, state);
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        // 不允许任何工具动作
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // 在空中右键退出选择模式
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