package com.adonis.fluid.handler;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.packet.ClipboardSetAddressPacket;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.adonis.fluid.CreateFluid.MODID;

@Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
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
                com.simibubi.create.AllPackets.getChannel().sendToServer(new ClipboardSetAddressPacket(pos));
            }
        }
    }
}
