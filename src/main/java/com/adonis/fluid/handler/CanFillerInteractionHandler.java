package com.adonis.fluid.handler;

import com.adonis.fluid.item.BatonItem;
import com.adonis.fluid.packet.CanFillerClearAddressPacket;
import com.adonis.fluid.packet.CanFillerTogglePacket;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.repackager.RepackagerBlock;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import static com.adonis.fluid.CreateFluid.MOD_ID;

@EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class CanFillerInteractionHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack heldItem = player.getMainHandItem();

        if (!(heldItem.getItem() instanceof BatonItem)) {
            return;
        }

        // 避免与ARM模式冲突
        if (BatonInteractionHandler.isInSelectionMode() && BatonInteractionHandler.getSelectionType() == BatonInteractionHandler.SelectionType.ARM) {
            return;
        }

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        boolean sneaking = player.isShiftKeyDown();

        if (AllBlocks.PACKAGER.has(state) || AllBlocks.REPACKAGER.has(state)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));

            if (level.isClientSide) {
                handlePackagerClick(state, pos, player, level, sneaking);
            }
        }
    }

    private static void handlePackagerClick(BlockState state, BlockPos pos, Player player, Level level, boolean sneaking) {
        if (sneaking) {
            // Shift+右键：清除地址
            PacketDistributor.sendToServer(new CanFillerClearAddressPacket(pos));
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 1.0F, false);
            createPackagerToggleParticles(level, pos);
        } else {
            // 普通右键：切换红石状态
            PacketDistributor.sendToServer(new CanFillerTogglePacket(pos));
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, 1.0F, false);

            boolean isPowered = state.getValue(PackagerBlock.POWERED);
            boolean isRepackager = state.getBlock() instanceof RepackagerBlock;
            String translationKey = isRepackager
                    ? (isPowered ? "create_fluid.baton.repackager.powered_off" : "create_fluid.baton.repackager.powered_on")
                    : (isPowered ? "create_fluid.baton.can_filler.powered_off" : "create_fluid.baton.can_filler.powered_on");

            CreateLang.builder()
                    .translate(translationKey)
                    .style(isPowered ? ChatFormatting.WHITE : ChatFormatting.RED)
                    .sendStatus(player);

            createPackagerToggleParticles(level, pos);
        }
    }

    private static void createPackagerToggleParticles(Level level, BlockPos pos) {
        for (int i = 0; i < 20; i++) {
            double angle = (Math.PI * 2) * (double) i / 20.0;
            double x = (double) pos.getX() + 0.5 + Math.cos(angle) * 0.7;
            double y = (double) pos.getY() + 0.5;
            double z = (double) pos.getZ() + 0.5 + Math.sin(angle) * 0.7;
            level.addParticle(new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 1.5F), x, y, z, 0.0, 0.05, 0.0);
        }
    }
}
