package com.adonis.fluid.handler;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.packet.ClipboardSetAddressPacket;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = CreateFluid.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class ClipboardPasteHandler {
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (AllBlocks.CLIPBOARD.isIn(stack)) {
            Level level = event.getLevel();
            BlockPos pos = event.getPos();
            if (level.getBlockEntity(pos) instanceof PackagerBlockEntity) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.CONSUME);
                event.getEntity().swing(event.getHand());
                CatnipServices.NETWORK.sendToServer(new ClipboardSetAddressPacket(pos));
            }
        }
    }
}
