package com.adonis.fluid.handler.mailbox;

import com.adonis.fluid.handler.EditModeManager;
import com.adonis.fluid.item.BatonItem;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.adonis.fluid.CreateFluid.MODID;

@Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MailboxInteractionHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack heldItem = player.getMainHandItem();
        if (!(heldItem.getItem() instanceof BatonItem)) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);

        if (be instanceof PostboxBlockEntity) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (level.isClientSide) {
                MailboxSelectionHandler.handleMailboxClick(pos, player, level);
            }
        } else if (EditModeManager.getCurrentMode() == EditModeManager.EditMode.MAILBOX
                && be instanceof StationBlockEntity) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (level.isClientSide) {
                MailboxSelectionHandler.handleStationClick(pos, player, level);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        ItemStack heldItem = player.getMainHandItem();
        if (!(heldItem.getItem() instanceof BatonItem)) return;

        if (EditModeManager.getCurrentMode() == EditModeManager.EditMode.MAILBOX) {
            event.setCanceled(true);
            event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
            event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);
            if (event.getLevel().isClientSide) {
                MailboxSelectionHandler.handleStationLeftClick(event.getPos(), player, event.getLevel());
            }
        }
    }
}
