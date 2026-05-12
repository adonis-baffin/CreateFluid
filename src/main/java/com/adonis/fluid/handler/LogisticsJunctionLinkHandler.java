package com.adonis.fluid.handler;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.block.LogisticsJunction.LogisticsJunctionBlockEntity;
import com.adonis.fluid.item.BatonItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = CreateFluid.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class LogisticsJunctionLinkHandler {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide)
            return;
        if (event.getHand() != InteractionHand.MAIN_HAND)
            return;

        Player player = event.getEntity();
        ItemStack heldItem = player.getMainHandItem();
        if (!(heldItem.getItem() instanceof BatonItem))
            return;

        Level level = event.getLevel();
        if (level.getBlockEntity(event.getPos()) instanceof LogisticsJunctionBlockEntity
                || LogisticsJunctionBlockEntity.isValidTarget(level, event.getPos(), event.getFace())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
