package com.adonis.fluid.handler;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.block.LogisticsJunction.LogisticsJunctionBlockEntity;
import com.adonis.fluid.packet.LogisticsJunctionPlacementPacket;
import com.adonis.fluid.registry.CFBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.Random;

@EventBusSubscriber(value = Dist.CLIENT, modid = CreateFluid.MOD_ID)
public class LogisticsJunctionSelectionHandler {
    private static ItemStack currentItem;
    private static JunctionTarget currentSelection;
    private static long lastBlockPos = -1L;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        tick();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void rightClickingBlocksSelectsThem(PlayerInteractEvent.RightClickBlock event) {
        if (!CFBlocks.LOGISTICS_JUNCTION.isIn(event.getItemStack()))
            return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        Direction face = event.getFace();
        if (face == null)
            return;

        if (level.isClientSide) {
            Player player = event.getEntity();
            if (player == null || player.isSpectator())
                return;
            if (!LogisticsJunctionBlockEntity.isValidTarget(level, pos, face))
                return;

            if (currentItem == null || !ItemStack.matches(currentItem, event.getItemStack()))
                currentItem = event.getItemStack();

            currentSelection = new JunctionTarget(pos, face);
            playSelectionEffects(level, pos);
            CreateLang.builder()
                    .translate("create.fluid.logistics_junction.target_set",
                            CreateLang.blockName(level.getBlockState(pos)).style(ChatFormatting.WHITE))
                    .style(ChatFormatting.WHITE)
                    .sendStatus(player);

            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        } else if (LogisticsJunctionBlockEntity.isValidTarget(level, pos, face)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void leftClickingBlocksDeselectsThem(PlayerInteractEvent.LeftClickBlock event) {
        if (currentItem == null || !CFBlocks.LOGISTICS_JUNCTION.isIn(currentItem))
            return;
        if (!event.getLevel().isClientSide)
            return;
        if (currentSelection != null && currentSelection.pos().equals(event.getPos())) {
            currentSelection = null;
            event.setCanceled(true);
        }
    }

    public static void flushSettings(BlockPos junctionPos) {
        if (currentSelection == null) {
            currentItem = null;
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (!LogisticsJunctionBlockEntity.isTargetInRange(junctionPos, currentSelection.pos())) {
            if (player != null) {
                CreateLang.builder()
                        .translate("create.fluid.logistics_junction.link_too_far")
                        .style(ChatFormatting.RED)
                        .sendStatus(player);
            }
        } else {
            PacketDistributor.sendToServer(LogisticsJunctionPlacementPacket.set(junctionPos, currentSelection.pos(),
                    currentSelection.face()));
            if (player != null) {
                CreateLang.builder()
                        .translate("create.fluid.logistics_junction.summary",
                                CreateLang.blockName(Minecraft.getInstance().level.getBlockState(currentSelection.pos()))
                                        .style(ChatFormatting.WHITE))
                        .style(ChatFormatting.WHITE)
                        .sendStatus(player);
            }
        }

        currentSelection = null;
        currentItem = null;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null)
            return;

        ItemStack heldItemMainhand = player.getMainHandItem();
        if (!CFBlocks.LOGISTICS_JUNCTION.isIn(heldItemMainhand)) {
            currentItem = null;
        } else {
            if (heldItemMainhand != currentItem) {
                currentItem = heldItemMainhand;
                currentSelection = null;
            }
            drawCurrentSelection(mc.level);
        }

        checkForWrench(heldItemMainhand);
    }

    private static void checkForWrench(ItemStack heldItem) {
        if (!AllItems.WRENCH.isIn(heldItem))
            return;

        Minecraft mc = Minecraft.getInstance();
        HitResult hitResult = mc.hitResult;
        if (!(hitResult instanceof BlockHitResult blockHitResult))
            return;

        BlockPos pos = blockHitResult.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (!(be instanceof LogisticsJunctionBlockEntity junction)) {
            lastBlockPos = -1L;
            return;
        }

        if (lastBlockPos == -1L || lastBlockPos != pos.asLong())
            lastBlockPos = pos.asLong();

        drawTargetOutline(junction.getFlexibleTargetPos(), junction.getFlexibleTargetFace(), mc.level, 0xDDBB66);
    }

    private static void drawCurrentSelection(Level level) {
        if (currentSelection == null)
            return;
        if (!LogisticsJunctionBlockEntity.isValidTarget(level, currentSelection.pos(), currentSelection.face())) {
            currentSelection = null;
            return;
        }
        drawTargetOutline(currentSelection.pos(), currentSelection.face(), level, 0xDDBB66);
    }

    private static void drawTargetOutline(BlockPos pos, Direction face, Level level, int color) {
        if (pos == null || face == null)
            return;

        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty())
            return;

        String key = "logistics_junction_target_" + pos.asLong() + "_" + face.get3DDataValue();
        Outliner.getInstance()
                .showAABB(key, shape.bounds().move(pos))
                .colored(color)
                .lineWidth(1 / 16f);
    }

    private static void playSelectionEffects(Level level, BlockPos pos) {
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.3f, 1.5f, false);

        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
            double y = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
            level.addParticle(new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 1.0F), x, y, z, 0, 0, 0);
        }
    }

    private record JunctionTarget(BlockPos pos, Direction face) {
    }
}
