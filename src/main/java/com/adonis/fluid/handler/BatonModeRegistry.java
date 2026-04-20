package com.adonis.fluid.handler;

import com.adonis.fluid.handler.frogport.FrogportSelectionHandler;
import com.adonis.fluid.handler.mailbox.MailboxSelectionHandler;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import static com.adonis.fluid.CreateFluid.MODID;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class BatonModeRegistry {

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Register FROGPORT mode handler
            EditModeManager.registerHandler(EditModeManager.EditMode.FROGPORT, new EditModeManager.ModeHandler() {
                @Override
                public void onEnter(BlockPos pos, Player player, Level level) {}

                @Override
                public void onExit(Player player, Level level) {
                    onExit(player, level, true);
                }

                @Override
                public void onExit(Player player, Level level, boolean showMessage) {
                    FrogportSelectionHandler.clearSelection();
                }

                @Override
                public boolean canExit(BlockPos pos, Player player, Level level) {
                    return true;
                }

                @Override
                public void onTick(Player player, Level level) {
                    FrogportSelectionHandler.render(Minecraft.getInstance());
                }

                @Override
                public boolean isValidInteraction(BlockPos pos, BlockState state, BlockEntity be, Level level) {
                    return AllBlocks.PACKAGE_FROGPORT.has(state) || AllBlocks.CHAIN_CONVEYOR.has(state);
                }
            });

            // Register MAILBOX mode handler
            EditModeManager.registerHandler(EditModeManager.EditMode.MAILBOX, new EditModeManager.ModeHandler() {
                @Override
                public void onEnter(BlockPos pos, Player player, Level level) {}

                @Override
                public void onExit(Player player, Level level) {
                    onExit(player, level, true);
                }

                @Override
                public void onExit(Player player, Level level, boolean showMessage) {
                    MailboxSelectionHandler.clearSelection();
                }

                @Override
                public boolean canExit(BlockPos pos, Player player, Level level) {
                    return true;
                }

                @Override
                public void onTick(Player player, Level level) {
                    MailboxSelectionHandler.render(Minecraft.getInstance());
                }

                @Override
                public boolean isValidInteraction(BlockPos pos, BlockState state, BlockEntity be, Level level) {
                    return be instanceof StationBlockEntity;
                }
            });


        });
    }
}
