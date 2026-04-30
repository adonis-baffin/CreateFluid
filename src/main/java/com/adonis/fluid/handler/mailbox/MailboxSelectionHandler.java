package com.adonis.fluid.handler.mailbox;

import com.adonis.fluid.handler.EditModeManager;
import com.adonis.fluid.handler.BatonInteractionHandler;
import com.adonis.fluid.packet.MailboxStationConnectionPacket;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget.TrainStationFrogportTarget;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.awt.Color;

public class MailboxSelectionHandler {
    private static BlockPos selectedMailboxPos = null;
    private static BlockPos pendingStationPos = null;
    private static boolean hasSelection = false;
    private static int particleCounter = 0;
    private static final Color MAILBOX_HIGHLIGHT_COLOR = new Color(14532966);
    private static final Color STATION_HIGHLIGHT_COLOR = new Color(10416499);
    private static final Color STATION_CONNECTED_COLOR = new Color(16736625);
    private static final Color PREVIEW_COLOR = new Color(10416499);

    public static boolean hasSelection() {
        return hasSelection && selectedMailboxPos != null;
    }

    public static BlockPos getSelectedMailboxPos() {
        return selectedMailboxPos;
    }

    public static void setSelection(BlockPos pos) {
        selectedMailboxPos = pos;
        hasSelection = true;
        loadExistingTarget();
    }

    public static void clearSelection() {
        selectedMailboxPos = null;
        pendingStationPos = null;
        hasSelection = false;
    }

    public static void handleMailboxClick(BlockPos pos, Player player, Level level) {
        if (hasSelection() && selectedMailboxPos.equals(pos)) {
            flushMailboxConnection(player, level);
            return;
        }
        EditModeManager.enterMode(EditModeManager.EditMode.MAILBOX, pos, player, level);
        setSelection(pos);
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 1.0F, false);
        createSelectionSuccessParticles(level, pos);
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
                        SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.8F, 1.0F, false);
                return;
            }
        }

        double distance = Math.sqrt(mailboxPos.distSqr(pos));
        if (distance > 65.0) {
            CreateLang.builder().translate("create_fluid.baton.mailbox.too_far").style(ChatFormatting.RED).sendStatus(player);
            return;
        }

        pendingStationPos = pos;
        CreateLang.builder().translate("create_fluid.baton.mailbox.target_selected").style(ChatFormatting.GOLD).sendStatus(player);
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.4F, 1.4F, false);
        createPointParticles(level, pos, new Vector3f(0.40F, 1.00F, 0.30F), 12);
    }

    private static void flushMailboxConnection(Player player, Level level) {
        if (!hasSelection())
            return;

        BlockPos mailboxPos = selectedMailboxPos;
        BlockPos stationPos = pendingStationPos;

        if (stationPos != null) {
            double distance = Math.sqrt(mailboxPos.distSqr(stationPos));
            if (distance > 65.0) {
                CreateLang.builder().translate("create_fluid.baton.mailbox.too_far").style(ChatFormatting.RED).sendStatus(player);
                return;
            }
        }

        // stationPos 为 null 时，用 mailboxPos 自身作为断开信号
        PacketDistributor.sendToServer(new MailboxStationConnectionPacket(mailboxPos, stationPos != null ? stationPos : mailboxPos));
        if (stationPos != null) {
            CreateLang.builder().translate("create_fluid.baton.mailbox.connected").style(ChatFormatting.GREEN).sendStatus(player);
            level.playLocalSound(mailboxPos.getX() + 0.5, mailboxPos.getY() + 0.5, mailboxPos.getZ() + 0.5,
                    SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.8F, 1.0F, false);
            createSelectionSuccessParticles(level, mailboxPos);
            createPointParticles(level, stationPos, new Vector3f(0.40F, 1.00F, 0.30F), 16);
        } else {
            CreateLang.builder().translate("create_fluid.baton.mailbox.target_cleared").style(ChatFormatting.GRAY).sendStatus(player);
            level.playLocalSound(mailboxPos.getX() + 0.5, mailboxPos.getY() + 0.5, mailboxPos.getZ() + 0.5,
                    SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 0.8f, false);
        }
        EditModeManager.exitMode(player, level, false);
    }

    public static void handleStationLeftClick(BlockPos pos, Player player, Level level) {
        if (!hasSelection()) return;

        BlockPos mailboxPos = selectedMailboxPos;
        if (pendingStationPos != null && pendingStationPos.equals(pos)) {
            pendingStationPos = null;
            CreateLang.builder().translate("create_fluid.baton.mailbox.target_cleared").style(ChatFormatting.GRAY).sendStatus(player);
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 0.8f, false);
            return;
        }

        // Only allow disconnecting the currently connected station
        if (level.getBlockEntity(mailboxPos) instanceof PostboxBlockEntity postbox) {
            if (postbox.target instanceof TrainStationFrogportTarget stationTarget && pos.equals(mailboxPos.offset(stationTarget.relativePos))) {
                pendingStationPos = null;
                CreateLang.builder().translate("create_fluid.baton.mailbox.target_cleared").style(ChatFormatting.GRAY).sendStatus(player);
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 0.8f, false);
                // 不在此处调用 exitMode，保持 MAILBOX 模式
                // 与 CreateBaton 中 EJECTOR/ARM 模式的行为一致
                // 玩家可通过再次右键邮箱、右键空气或切换手持物品来退出
            }
        }
    }

    private static void loadExistingTarget() {
        pendingStationPos = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || selectedMailboxPos == null)
            return;
        if (!(mc.level.getBlockEntity(selectedMailboxPos) instanceof PostboxBlockEntity postbox))
            return;
        if (postbox.target instanceof TrainStationFrogportTarget stationTarget)
            pendingStationPos = selectedMailboxPos.offset(stationTarget.relativePos);
    }

    public static void render(Minecraft mc) {
        if (!hasSelection()) return;

        Level level = mc.level;
        if (level == null) return;

        BlockPos pos = selectedMailboxPos;
        createContinuousParticles(level, pos);
        if (level.isLoaded(pos)) {
            VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
            if (!shape.isEmpty()) {
                Outliner.getInstance()
                        .showAABB("BatonMailboxHighlight", shape.bounds().move(pos))
                        .colored(MAILBOX_HIGHLIGHT_COLOR.getRGB())
                        .lineWidth(0.0625F);
            }
        }

        if (pendingStationPos != null) {
            renderStationOutline(level, pendingStationPos, PREVIEW_COLOR.getRGB(), "BatonPendingStationHighlight");
            BatonInteractionHandler.animateConnection(mc, Vec3.atCenterOf(pos), Vec3.atCenterOf(pendingStationPos), PREVIEW_COLOR.getRGB());
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
                renderStationOutline(level, hitPos, color, "BatonStationHighlight");
            }
        }
    }

    private static void renderStationOutline(Level level, BlockPos pos, int color, Object key) {
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        if (!shape.isEmpty()) {
            Outliner.getInstance()
                    .showAABB(key, shape.bounds().move(pos))
                    .colored(color)
                    .lineWidth(0.0625F);
        }
    }

    private static void createContinuousParticles(Level level, BlockPos pos) {
        if (level == null || pos == null)
            return;

        particleCounter = (particleCounter + 1) % 1000;
        if (particleCounter % 10 != 0)
            return;

        for (int i = 0; i < 6; i++) {
            double angle = (Math.PI * 2) * i / 6.0;
            double radius = 0.55;
            double x = pos.getX() + 0.5 + Math.cos(angle) * radius;
            double y = pos.getY() + 1.15;
            double z = pos.getZ() + 0.5 + Math.sin(angle) * radius;
            level.addParticle(new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 0.65F),
                    x, y, z, 0.0, 0.015, 0.0);
        }
    }

    private static void createSelectionSuccessParticles(Level level, BlockPos pos) {
        createPointParticles(level, pos, new Vector3f(1.0F, 1.0F, 1.0F), 20);
    }

    private static void createPointParticles(Level level, BlockPos pos, Vector3f color, int count) {
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2) * i / count;
            double radius = 0.7;
            double x = pos.getX() + 0.5 + Math.cos(angle) * radius;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5 + Math.sin(angle) * radius;
            level.addParticle(new DustParticleOptions(color, 1.5F), x, y, z, 0.0, 0.05, 0.0);
        }
    }
}
