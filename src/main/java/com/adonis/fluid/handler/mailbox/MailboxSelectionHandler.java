package com.adonis.fluid.handler.mailbox;

import com.adonis.fluid.handler.EditModeManager;
import com.adonis.fluid.packet.MailboxStationConnectionPacket;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget.TrainStationFrogportTarget;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.awt.Color;

public class MailboxSelectionHandler {
    private static BlockPos selectedMailboxPos = null;
    private static boolean hasSelection = false;
    private static final Color MAILBOX_HIGHLIGHT_COLOR = new Color(14532966);
    private static final Color STATION_HIGHLIGHT_COLOR = new Color(7376301);
    private static final Color STATION_CONNECTED_COLOR = new Color(16736625);
    private static final Color PREVIEW_COLOR = new Color(10416499);
    private static final Color PREVIEW_ERROR_COLOR = new Color(16736625);

    public static boolean hasSelection() {
        return hasSelection && selectedMailboxPos != null;
    }

    public static BlockPos getSelectedMailboxPos() {
        return selectedMailboxPos;
    }

    public static void setSelection(BlockPos pos) {
        selectedMailboxPos = pos;
        hasSelection = true;
    }

    public static void clearSelection() {
        selectedMailboxPos = null;
        hasSelection = false;
    }

    public static void handleMailboxClick(BlockPos pos, Player player, Level level) {
        if (hasSelection() && selectedMailboxPos.equals(pos)) {
            EditModeManager.exitMode(player, level);
            return;
        }
        EditModeManager.enterMode(EditModeManager.EditMode.MAILBOX, pos, player, level);
        setSelection(pos);
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 1.0F, false);
        CreateLang.builder().translate("create_fluid.baton.mailbox.selected").style(ChatFormatting.GOLD).sendStatus(player);
    }

    public static void handleStationClick(BlockPos pos, Player player, Level level) {
        if (!hasSelection()) return;

        BlockPos mailboxPos = selectedMailboxPos;

        // Check if already connected to same station
        if (level.getBlockEntity(mailboxPos) instanceof PostboxBlockEntity postbox) {
            if (postbox.target instanceof TrainStationFrogportTarget stationTarget && pos.equals(mailboxPos.offset(stationTarget.relativePos))) {
                CreateLang.builder().translate("create_fluid.baton.mailbox.already_connected").style(ChatFormatting.RED).sendStatus(player);
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.NOTE_BLOCK_CHIME.get(), SoundSource.BLOCKS, 0.8F, 1.0F, false);
                return;
            }
        }

        double distance = Math.sqrt(mailboxPos.distSqr(pos));
        if (distance > 65.0) {
            CreateLang.builder().translate("create_fluid.baton.mailbox.too_far").style(ChatFormatting.RED).sendStatus(player);
            return;
        }

        com.simibubi.create.AllPackets.getChannel().sendToServer(new MailboxStationConnectionPacket(mailboxPos, pos));
        CreateLang.builder().translate("create_fluid.baton.mailbox.connected").style(ChatFormatting.GREEN).sendStatus(player);
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.NOTE_BLOCK_CHIME.get(), SoundSource.BLOCKS, 0.8F, 1.0F, false);
        EditModeManager.exitMode(player, level);
    }

    public static void handleStationLeftClick(BlockPos pos, Player player, Level level) {
        if (!hasSelection()) return;

        BlockPos mailboxPos = selectedMailboxPos;

        // Only allow disconnecting the currently connected station
        if (level.getBlockEntity(mailboxPos) instanceof PostboxBlockEntity postbox) {
            if (postbox.target instanceof TrainStationFrogportTarget stationTarget && pos.equals(mailboxPos.offset(stationTarget.relativePos))) {
                com.simibubi.create.AllPackets.getChannel().sendToServer(new com.adonis.fluid.packet.MailboxStationDisconnectPacket(mailboxPos));
                CreateLang.builder().translate("create_fluid.baton.mailbox.disconnected").style(ChatFormatting.RED).sendStatus(player);
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 0.8f, false);
                // 不在此处调用 exitMode，保持 MAILBOX 模式
                // 与 CreateBaton 中 EJECTOR/ARM 模式的行为一致
                // 玩家可通过再次右键邮箱、右键空气或切换手持物品来退出
            }
        }
    }

    public static void render(Minecraft mc) {
        if (!hasSelection()) return;

        Level level = mc.level;
        if (level == null) return;

        BlockPos pos = selectedMailboxPos;
        if (level.isLoaded(pos)) {
            VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
            if (!shape.isEmpty()) {
                Outliner.getInstance()
                        .showAABB("BatonMailboxHighlight", shape.bounds().move(pos))
                        .colored(MAILBOX_HIGHLIGHT_COLOR.getRGB())
                        .lineWidth(0.0625F);
            }
        }

        // Detect station under crosshair
        if (mc.hitResult != null && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            net.minecraft.world.phys.BlockHitResult blockHit = (net.minecraft.world.phys.BlockHitResult) mc.hitResult;
            BlockPos hitPos = blockHit.getBlockPos();
            if (level.getBlockEntity(hitPos) instanceof StationBlockEntity) {
                boolean alreadyConnected = false;
                if (level.getBlockEntity(pos) instanceof PostboxBlockEntity postbox) {
                    alreadyConnected = postbox.target instanceof TrainStationFrogportTarget stationTarget && hitPos.equals(pos.offset(stationTarget.relativePos));
                }
                int color = alreadyConnected ? STATION_CONNECTED_COLOR.getRGB() : STATION_HIGHLIGHT_COLOR.getRGB();
                VoxelShape shape = level.getBlockState(hitPos).getShape(level, hitPos);
                if (!shape.isEmpty()) {
                    Outliner.getInstance()
                            .showAABB("BatonStationHighlight", shape.bounds().move(hitPos))
                            .colored(color)
                            .lineWidth(0.0625F);
                }
            }
        }
    }
}
