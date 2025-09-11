package com.adonis.fluid.handler;

import com.adonis.fluid.block.Pipette.PipetteBlockEntity;
import com.adonis.fluid.content.pipette.FluidInteractionPoint;
import com.adonis.fluid.item.BatonItem;
import com.adonis.fluid.mixin.accessor.ArmBlockEntityAccessor;
import com.adonis.fluid.packet.PipetteFluidPlacementPacket;
import com.adonis.fluid.packet.QuartzLampTogglePacket;
import com.simibubi.create.AllPackets;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmPlacementPacket;
import com.simibubi.create.content.logistics.depot.EjectorBlockEntity;
import com.simibubi.create.content.logistics.depot.EjectorPlacementPacket;
import com.simibubi.create.content.logistics.depot.EntityLauncher;
import com.simibubi.create.content.redstone.RoseQuartzLampBlock;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.theme.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BatonInteractionHandler {

    // 当前选中的目标
    private static BlockEntity selectedTarget = null;
    private static BlockPos selectedTargetPos = null;

    // 动力臂相关
    private static List<ArmInteractionPoint> currentArmSelection = new ArrayList<>();

    // 移液器相关
    private static List<FluidInteractionPoint> currentPipetteSelection = new ArrayList<>();

    // 弹射置物台相关
    private static BlockPos selectedEjectorPos = null;
    private static BlockPos ejectorTargetPos = null;
    private static EntityLauncher launcher = null;

    // 选择模式类型
    private enum SelectionType {
        NONE, ARM, PIPETTE, EJECTOR
    }
    private static SelectionType selectionType = SelectionType.NONE;

    // 粒子效果计数器
    private static int particleCounter = 0;

    public static boolean isInSelectionMode() {
        return selectionType != SelectionType.NONE;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack heldItem = player.getMainHandItem();

        if (!(heldItem.getItem() instanceof BatonItem)) {
            return;
        }

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        boolean sneaking = player.isShiftKeyDown();

        // 特殊处理置物台 - 在客户端和服务端都要处理
        if (com.simibubi.create.AllBlocks.DEPOT.has(state)) {
            if (!sneaking) {
                // 普通右键：阻止放置
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);

                // 只在客户端处理交互点选择
                if (level.isClientSide && isInSelectionMode()) {
                    if (selectionType == SelectionType.ARM) {
                        handleArmPointInteraction(level, pos, state, player);
                    } else if (selectionType == SelectionType.PIPETTE) {
                        handlePipettePointInteraction(level, pos, state, player);
                    }
                }
                return;
            } else {
                // 潜行右键：允许正常放置
                return;
            }
        }

        // 后续的处理仍然只在客户端
        if (!level.isClientSide) {
            // 但是要检查是否需要在服务端取消某些交互
            BlockEntity be = level.getBlockEntity(pos);

            // 这些方块在服务端也要取消事件
            if (be instanceof ArmBlockEntity ||
                    be instanceof PipetteBlockEntity ||
                    be instanceof EjectorBlockEntity ||
                    state.getBlock() instanceof RoseQuartzLampBlock) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }

            // 检查是否可以作为交互点，服务端也要阻止
            if (canBeInteractionPoint(level, pos, state)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }

            return;
        }

        // 以下是客户端处理逻辑
        BlockEntity be = level.getBlockEntity(pos);

        // 处理石英灯
        if (state.getBlock() instanceof RoseQuartzLampBlock) {
            AllPackets.getChannel().sendToServer(new QuartzLampTogglePacket(pos));
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3f, 1.0f, false);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (state.getBlock() instanceof RoseQuartzLampBlock) {
            // 发送数据包到服务端处理
            AllPackets.getChannel().sendToServer(new QuartzLampTogglePacket(pos));

            // 播放音效
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3f, 1.0f, false);

            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        // 处理动力臂
        if (be instanceof ArmBlockEntity arm) {
            if (sneaking) {
                // 潜行右键：切换跳舞模式
                handleArmDanceToggle(arm, player, level);
            } else {
                // 普通右键：进入选择模式
                handleArmTargetClick(be, pos, player, level);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        // 处理移液器
        if (be instanceof PipetteBlockEntity) {
            if (!sneaking) {
                // 只有普通右键才有效果
                handlePipetteTargetClick(be, pos, player, level);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            // 潜行右键无效果
            return;
        }

        // 处理弹射置物台
        if (be instanceof EjectorBlockEntity) {
            handleEjectorClick(be, pos, player, level, sneaking);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        // 在弹射置物台选择模式下
        if (selectionType == SelectionType.EJECTOR && selectedEjectorPos != null) {
            if (!sneaking) {
                // 普通右键选择目标位置
                handleEjectorTargetSelection(pos, player, level);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
            // 潜行右键什么都不做
            return;
        }

        // 在其他选择模式下点击方块
        if (isInSelectionMode() && selectionType != SelectionType.EJECTOR) {
            if (!sneaking) {
                if (selectionType == SelectionType.ARM) {
                    handleArmPointInteraction(level, pos, state, player);
                } else if (selectionType == SelectionType.PIPETTE) {
                    handlePipettePointInteraction(level, pos, state, player);
                }

                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }

    private static boolean canBeInteractionPoint(Level level, BlockPos pos, BlockState state) {
        // 检查动力臂交互点
        if (com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint.isInteractable(level, pos, state)) {
            return true;
        }
        // 检查移液器交互点
        if (com.adonis.fluid.content.pipette.FluidInteractionPoint.create(level, pos, state) != null) {
            return true;
        }
        return false;
    }

    private static void handleArmDanceToggle(ArmBlockEntity arm, Player player, Level level) {
        ArmBlockEntityAccessor accessor = (ArmBlockEntityAccessor) arm;

        // 检查当前状态
        boolean isDancing = accessor.getPhase() == ArmBlockEntity.Phase.DANCING;

        if (isDancing) {
            // 停止跳舞
            accessor.setPhase(ArmBlockEntity.Phase.SEARCH_INPUTS);
            CreateLang.builder()
                    .translate("fluid.baton.arm.dance_stopped")
                    .style(ChatFormatting.YELLOW)
                    .sendStatus(player);
        } else {
            // 开始跳舞
            accessor.setPhase(ArmBlockEntity.Phase.DANCING);
            CreateLang.builder()
                    .translate("fluid.baton.arm.dance_started")
                    .style(ChatFormatting.GREEN)
                    .sendStatus(player);
        }

        // 播放音效
        level.playLocalSound(arm.getBlockPos().getX() + 0.5, arm.getBlockPos().getY() + 0.5,
                arm.getBlockPos().getZ() + 0.5,
                SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.BLOCKS, 0.5f, isDancing ? 0.8f : 1.2f, false);

        // 创建粒子效果
        createDanceToggleParticles(level, arm.getBlockPos());
    }

    private static void handleEjectorClick(BlockEntity be, BlockPos pos, Player player, Level level, boolean sneaking) {
        if (selectionType == SelectionType.EJECTOR && selectedEjectorPos != null && selectedEjectorPos.equals(pos)) {
            // 再次点击同一个弹射置物台，确认并发送设置
            flushEjectorSettings(pos, player, level);
        } else {
            // 进入弹射置物台选择模式
            cancelSelection();
            selectionType = SelectionType.EJECTOR;
            selectedEjectorPos = pos;
            launcher = null;

            EjectorBlockEntity ejector = (EjectorBlockEntity) be;
            BlockPos targetPos = ejector.getTargetPosition();
            if (!targetPos.equals(pos)) {
                ejectorTargetPos = targetPos;
            } else {
                ejectorTargetPos = null;
            }

            CreateLang.builder()
                    .translate("fluid.baton.ejector.click_to_set")
                    .style(ChatFormatting.GOLD)
                    .sendStatus(player);

            // 播放音效和粒子效果
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5f, 1.0f, false);
            createSelectionSuccessParticles(level, pos);
        }
    }

    private static void handleEjectorTargetSelection(BlockPos pos, Player player, Level level) {
        ejectorTargetPos = pos;
        launcher = null; // 重置launcher让它重新计算

        CreateLang.builder()
                .translate("fluid.baton.ejector.target_set")
                .style(ChatFormatting.GOLD)
                .sendStatus(player);

        // 播放音效
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.3f, 1.5f, false);
    }

    private static Direction getValidTargetDirection(BlockPos ejectorPos, BlockPos targetPos) {
        if (targetPos == null) {
            return null;
        }

        if (VecHelper.onSameAxis(ejectorPos, targetPos, Direction.Axis.Y)) {
            return null;
        }

        int xDiff = targetPos.getX() - ejectorPos.getX();
        int zDiff = targetPos.getZ() - ejectorPos.getZ();
        int max = AllConfigs.server().kinetics.maxEjectorDistance.get();

        if (Math.abs(xDiff) > max || Math.abs(zDiff) > max) {
            return null;
        }

        if (xDiff == 0) {
            return Direction.get(zDiff < 0 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE, Direction.Axis.Z);
        } else if (zDiff == 0) {
            return Direction.get(xDiff < 0 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE, Direction.Axis.X);
        }

        return null;
    }

    private static void flushEjectorSettings(BlockPos ejectorPos, Player player, Level level) {
        if (ejectorTargetPos == null) {
            CreateLang.builder()
                    .translate("fluid.baton.ejector.no_target")
                    .style(ChatFormatting.RED)
                    .sendStatus(player);
            cancelSelection();
            return;
        }

        Direction validDirection = getValidTargetDirection(ejectorPos, ejectorTargetPos);

        if (validDirection == null) {
            CreateLang.builder()
                    .translate("fluid.baton.ejector.invalid_target")
                    .style(ChatFormatting.RED)
                    .sendStatus(player);
            cancelSelection();
            return;
        }

        int xDiff = ejectorTargetPos.getX() - ejectorPos.getX();
        int zDiff = ejectorTargetPos.getZ() - ejectorPos.getZ();
        int yDiff = ejectorTargetPos.getY() - ejectorPos.getY();

        int horizontalDistance = Math.max(Math.abs(xDiff), Math.abs(zDiff));

        // 发送数据包到服务器
        AllPackets.getChannel().sendToServer(new EjectorPlacementPacket(horizontalDistance, yDiff, ejectorPos, validDirection));

        CreateLang.builder()
                .translate("weighted_ejector.targeting", ejectorTargetPos.getX(), ejectorTargetPos.getY(), ejectorTargetPos.getZ())
                .style(ChatFormatting.GREEN)
                .sendStatus(player);

        // 播放完成音效
        level.playLocalSound(ejectorPos.getX() + 0.5, ejectorPos.getY() + 0.5, ejectorPos.getZ() + 0.5,
                SoundEvents.NOTE_BLOCK_CHIME.get(), SoundSource.BLOCKS, 0.8f, 1.0f, false);

        cancelSelection();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        ItemStack heldItem = player.getMainHandItem();

        if (!(heldItem.getItem() instanceof BatonItem)) {
            return;
        }

        if (isInSelectionMode()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);

            if (event.getLevel().isClientSide) {
                BlockPos pos = event.getPos();

                if (selectionType == SelectionType.EJECTOR) {
                    // 左键点击目标位置取消选择
                    if (pos.equals(ejectorTargetPos)) {
                        ejectorTargetPos = null;
                        launcher = null;
                        CreateLang.builder()
                                .translate("fluid.baton.ejector.target_cleared")
                                .style(ChatFormatting.RED)
                                .sendStatus(player);
                    }
                } else {
                    // 原有的移除交互点逻辑
                    boolean removed = false;
                    if (selectionType == SelectionType.ARM) {
                        int sizeBefore = currentArmSelection.size();
                        removeArm(pos);
                        removed = currentArmSelection.size() < sizeBefore;

                        if (removed) {
                            CreateLang.builder()
                                    .translate("fluid.baton.interaction_point_removed")
                                    .style(ChatFormatting.RED)
                                    .sendStatus(player);
                            event.getLevel().playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                    SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 0.8f, false);
                        }
                    } else if (selectionType == SelectionType.PIPETTE) {
                        int sizeBefore = currentPipetteSelection.size();
                        removePipette(pos);
                        removed = currentPipetteSelection.size() < sizeBefore;

                        if (removed) {
                            CreateLang.builder()
                                    .translate("fluid.baton.interaction_point_removed")
                                    .style(ChatFormatting.RED)
                                    .sendStatus(player);
                            event.getLevel().playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                    SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 0.8f, false);
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        ItemStack heldItem = player.getMainHandItem();
        if (!(heldItem.getItem() instanceof BatonItem)) {
            if (isInSelectionMode()) {
                cancelSelection();
            }
            return;
        }

        if (isInSelectionMode()) {
            particleCounter++;

            if (selectedTargetPos != null) {
                createContinuousParticles(Minecraft.getInstance().level, selectedTargetPos);
            } else if (selectedEjectorPos != null) {
                createContinuousParticles(Minecraft.getInstance().level, selectedEjectorPos);
            }

            if (selectionType == SelectionType.ARM) {
                drawArmOutlines(currentArmSelection);
            } else if (selectionType == SelectionType.PIPETTE) {
                drawPipetteOutlines(currentPipetteSelection);
            } else if (selectionType == SelectionType.EJECTOR) {
                drawEjectorOutlines();
                drawEjectorArc();
            }
        }
    }

    private static void drawEjectorOutlines() {
        if (ejectorTargetPos != null) {
            Level level = Minecraft.getInstance().level;
            BlockState state = level.getBlockState(ejectorTargetPos);
            VoxelShape shape = state.getShape(level, ejectorTargetPos);

            if (!shape.isEmpty()) {
                // 使用原版的颜色 16763764 (0xFFBA34)
                Outliner.getInstance()
                        .showAABB("ejector_target", shape.bounds().move(ejectorTargetPos))
                        .colored(16763764)
                        .lineWidth(0.0625F);
            }
        }
    }

    private static void drawEjectorArc() {
        if (selectedEjectorPos == null || ejectorTargetPos == null) {
            return;
        }

        int xDiff = ejectorTargetPos.getX() - selectedEjectorPos.getX();
        int zDiff = ejectorTargetPos.getZ() - selectedEjectorPos.getZ();
        int yDiff = ejectorTargetPos.getY() - selectedEjectorPos.getY();

        // 计算有效的位置（必须在一条直线上）
        int validX = Math.abs(zDiff) > Math.abs(xDiff) ? 0 : xDiff;
        int validZ = Math.abs(zDiff) < Math.abs(xDiff) ? 0 : zDiff;

        // 判断是否有效（是否在一条直线上）
        boolean isValid = (xDiff == validX && zDiff == validZ);

        // 使用原版的颜色值 - 有效时绿色，无效时红色
        int color = isValid ? 0x9EF173 : 0xFF6171;  // 使用十六进制表示更清晰

        // 获取方向
        Direction validDirection = getValidTargetDirection(selectedEjectorPos, ejectorTargetPos);
        if (validDirection == null) {
            // 如果方向无效，选择一个默认方向用于显示轨迹
            if (Math.abs(validX) >= Math.abs(validZ)) {
                validDirection = validX != 0 ? (validX > 0 ? Direction.EAST : Direction.WEST) : Direction.NORTH;
            } else {
                validDirection = validZ != 0 ? (validZ > 0 ? Direction.SOUTH : Direction.NORTH) : Direction.NORTH;
            }
        }

        // 创建或更新launcher
        if (launcher == null) {
            int horizontalDistance = Math.max(Math.abs(validX), Math.abs(validZ));
            if (horizontalDistance == 0) horizontalDistance = 1;
            launcher = new EntityLauncher(horizontalDistance, yDiff);
        }

        // 绘制抛物线轨迹
        double totalFlyingTicks = launcher.getTotalFlyingTicks() + 3.0;
        int segments = (int)(totalFlyingTicks / 3) + 1;
        double tickOffset = totalFlyingTicks / segments;

        Vector3f colorVec = new Color(color).asVectorF();
        DustParticleOptions data = new DustParticleOptions(colorVec, 1.0F);
        ClientLevel world = Minecraft.getInstance().level;

        // 绘制有效框（如果位置不在一条直线上）
        if (!isValid) {
            BlockPos validPos = selectedEjectorPos.offset(validX - xDiff, -yDiff, validZ - zDiff);
            AABB bb = new AABB(0, 0, 0, 1, 0, 1).move(validPos);
            Outliner.getInstance()
                    .chaseAABB("valid", bb)
                    .colored(color)
                    .lineWidth(0.0625F);
        }

        for (int i = 0; i < segments; i++) {
            double ticks = (AnimationTickHolder.getRenderTime() / 3.0F) % tickOffset + i * tickOffset;
            Vec3 vec = launcher.getGlobalPos(ticks, validDirection.getOpposite(), selectedEjectorPos);

            // 如果位置无效，添加偏移
            if (!isValid) {
                vec = vec.add(xDiff - validX, 0, zDiff - validZ);
            }

            world.addParticle(data, vec.x, vec.y, vec.z, 0, 0, 0);
        }
    }

    private static void handleArmTargetClick(BlockEntity be, BlockPos pos, Player player, Level level) {
        if (selectedTarget == be && selectedTargetPos.equals(pos)) {
            createSelectionSuccessParticles(level, pos);
            flushArmSettings(pos);
            return;
        }

        cancelSelection();

        selectedTarget = be;
        selectedTargetPos = pos;
        selectionType = SelectionType.ARM;

        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5f, 1.0f, false);

        createSelectionSuccessParticles(level, pos);

        ArmBlockEntity arm = (ArmBlockEntity) be;
        currentArmSelection.clear();

        ArmBlockEntityAccessor accessor = (ArmBlockEntityAccessor) arm;
        List<ArmInteractionPoint> inputs = accessor.getInputs();
        List<ArmInteractionPoint> outputs = accessor.getOutputs();

        currentArmSelection.addAll(inputs);
        currentArmSelection.addAll(outputs);

        CreateLang.builder()
                .translate("create.mechanical_arm.summary", inputs.size(), outputs.size())
                .style(ChatFormatting.WHITE)
                .sendStatus(player);
    }

    private static void handlePipetteTargetClick(BlockEntity be, BlockPos pos, Player player, Level level) {
        if (selectedTarget == be && selectedTargetPos.equals(pos)) {
            createSelectionSuccessParticles(level, pos);
            flushPipetteSettings(pos);
            return;
        }

        cancelSelection();

        selectedTarget = be;
        selectedTargetPos = pos;
        selectionType = SelectionType.PIPETTE;

        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5f, 1.0f, false);

        createSelectionSuccessParticles(level, pos);

        PipetteBlockEntity pipette = (PipetteBlockEntity) be;
        currentPipetteSelection.clear();

        currentPipetteSelection.addAll(pipette.inputs);
        currentPipetteSelection.addAll(pipette.outputs);

        CreateLang.builder()
                .translate("fluid.mechanical_pipette.summary",
                        pipette.inputs.size(), pipette.outputs.size())
                .style(ChatFormatting.WHITE)
                .sendStatus(player);
    }

    private static void handleArmPointInteraction(Level level, BlockPos pos, BlockState state, Player player) {
        ArmInteractionPoint selected = getSelectedArm(pos);
        if (selected == null) {
            ArmInteractionPoint point = ArmInteractionPoint.create(level, pos, state);
            if (point == null) {
                return;
            }
            selected = point;
            putArm(point);
            playSelectionEffects(level, pos, true);
        }

        selected.cycleMode();

        ArmInteractionPoint.Mode mode = selected.getMode();
        CreateLang.builder()
                .translate(mode.getTranslationKey(),
                        CreateLang.blockName(state).style(ChatFormatting.WHITE))
                .color(mode.getColor())
                .sendStatus(player);

        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3f, 2.0f, false);
    }

    private static void flushArmSettings(BlockPos armPos) {
        int removed = 0;
        Iterator<ArmInteractionPoint> iterator = currentArmSelection.iterator();
        while (iterator.hasNext()) {
            ArmInteractionPoint point = iterator.next();
            if (!point.getPos().closerThan(armPos, ArmBlockEntity.getRange())) {
                iterator.remove();
                removed++;
            }
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (removed > 0) {
            CreateLang.builder()
                    .translate("create.mechanical_arm.points_outside_range", removed)
                    .style(ChatFormatting.RED)
                    .sendStatus(player);
        } else {
            int inputs = 0;
            int outputs = 0;
            for (ArmInteractionPoint point : currentArmSelection) {
                if (point.getMode() == ArmInteractionPoint.Mode.DEPOSIT) {
                    outputs++;
                } else {
                    inputs++;
                }
            }

            if (inputs + outputs > 0) {
                CreateLang.builder()
                        .translate("create.mechanical_arm.summary", inputs, outputs)
                        .style(ChatFormatting.WHITE)
                        .sendStatus(player);
            }
        }

        AllPackets.getChannel().sendToServer(new ArmPlacementPacket(currentArmSelection, armPos));

        Minecraft.getInstance().level.playLocalSound(armPos.getX() + 0.5, armPos.getY() + 0.5, armPos.getZ() + 0.5,
                SoundEvents.NOTE_BLOCK_CHIME.get(), SoundSource.BLOCKS, 0.8f, 1.0f, false);

        cancelSelection();
    }

    private static void flushPipetteSettings(BlockPos pipettePos) {
        int removed = 0;
        Iterator<FluidInteractionPoint> iterator = currentPipetteSelection.iterator();
        while (iterator.hasNext()) {
            FluidInteractionPoint point = iterator.next();
            if (!point.getPos().closerThan(pipettePos, PipetteBlockEntity.getRange())) {
                iterator.remove();
                removed++;
            }
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (removed > 0) {
            CreateLang.builder()
                    .translate("fluid.mechanical_pipette.points_outside_range", removed)
                    .style(ChatFormatting.RED)
                    .sendStatus(player);
        } else {
            int inputs = 0;
            int outputs = 0;
            for (FluidInteractionPoint point : currentPipetteSelection) {
                if (point.getMode() == FluidInteractionPoint.Mode.DEPOSIT) {
                    outputs++;
                } else {
                    inputs++;
                }
            }

            if (inputs + outputs > 0) {
                CreateLang.builder()
                        .translate("fluid.mechanical_pipette.summary", inputs, outputs)
                        .style(ChatFormatting.WHITE)
                        .sendStatus(player);
            }
        }

        AllPackets.getChannel().sendToServer(new PipetteFluidPlacementPacket(currentPipetteSelection, pipettePos));

        Minecraft.getInstance().level.playLocalSound(pipettePos.getX() + 0.5, pipettePos.getY() + 0.5, pipettePos.getZ() + 0.5,
                SoundEvents.NOTE_BLOCK_CHIME.get(), SoundSource.BLOCKS, 0.8f, 1.0f, false);

        cancelSelection();
    }

    private static void handlePipettePointInteraction(Level level, BlockPos pos, BlockState state, Player player) {
        FluidInteractionPoint selected = getSelectedPipette(pos);

        if (selected == null) {
            FluidInteractionPoint point = FluidInteractionPoint.create(level, pos, state);
            if (point == null) {
                return;
            }
            selected = point;
            putPipette(point);
            playSelectionEffects(level, pos, true);
        }

        selected.cycleMode();

        FluidInteractionPoint.Mode mode = selected.getMode();
        CreateLang.builder()
                .translate(mode.getTranslationKey(),
                        CreateLang.blockName(state).style(ChatFormatting.WHITE))
                .color(mode.getColor())
                .sendStatus(player);

        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3f, 2.0f, false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (player != null) {
            ItemStack heldItem = player.getMainHandItem();
            if (heldItem.getItem() instanceof BatonItem && isInSelectionMode()) {
                event.setCanceled(true);
            }
        }
    }

    // 辅助方法保持不变...
    private static void playSelectionEffects(Level level, BlockPos pos, boolean isNew) {
        if (isNew) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.3f, 1.5f, false);
        }

        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
            double y = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
            level.addParticle(new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 1.0F),
                    x, y, z, 0, 0, 0);
        }
    }

    private static void createSelectionSuccessParticles(Level level, BlockPos pos) {
        Random random = new Random();
        for (int i = 0; i < 20; i++) {
            double angle = (Math.PI * 2) * i / 20;
            double radius = 0.7;
            double x = pos.getX() + 0.5 + Math.cos(angle) * radius;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5 + Math.sin(angle) * radius;
            level.addParticle(new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 1.5F),
                    x, y, z, 0, 0.05, 0);
        }
    }

    private static void createDanceToggleParticles(Level level, BlockPos pos) {
        Random random = new Random();
        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2) * i / 8;
            double radius = 1.0;
            double x = pos.getX() + 0.5 + Math.cos(angle) * radius;
            double y = pos.getY() + 1.0 + random.nextDouble() * 0.5;
            double z = pos.getZ() + 0.5 + Math.sin(angle) * radius;
            level.addParticle(ParticleTypes.NOTE,
                    x, y, z, random.nextDouble(), 0, 0);
        }
    }

    private static void createContinuousParticles(Level level, BlockPos pos) {
        Random random = new Random();

        if (particleCounter % 10 == 0) {
            for (int i = 0; i < 6; i++) {
                double angle = (Math.PI * 2) * i / 6;
                double radius = 0.7 + random.nextDouble() * 0.2;
                double x = pos.getX() + 0.5 + Math.cos(angle) * radius;
                double y = pos.getY() + 0.5 + random.nextDouble() * 0.8;
                double z = pos.getZ() + 0.5 + Math.sin(angle) * radius;

                level.addParticle(new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 0.6F),
                        x, y, z, 0, 0.01, 0);
            }
        }
    }

    private static void putArm(ArmInteractionPoint point) {
        currentArmSelection.add(point);
    }

    private static void putPipette(FluidInteractionPoint point) {
        currentPipetteSelection.add(point);
    }

    private static ArmInteractionPoint removeArm(BlockPos pos) {
        ArmInteractionPoint result = getSelectedArm(pos);
        if (result != null) {
            currentArmSelection.remove(result);
        }
        return result;
    }

    private static FluidInteractionPoint removePipette(BlockPos pos) {
        FluidInteractionPoint result = getSelectedPipette(pos);
        if (result != null) {
            currentPipetteSelection.remove(result);
        }
        return result;
    }

    private static ArmInteractionPoint getSelectedArm(BlockPos pos) {
        for (ArmInteractionPoint point : currentArmSelection) {
            if (point.getPos().equals(pos)) {
                return point;
            }
        }
        return null;
    }

    private static FluidInteractionPoint getSelectedPipette(BlockPos pos) {
        for (FluidInteractionPoint point : currentPipetteSelection) {
            if (point.getPos().equals(pos)) {
                return point;
            }
        }
        return null;
    }

    public static void cancelSelection() {
        selectedTarget = null;
        selectedTargetPos = null;
        selectedEjectorPos = null;
        ejectorTargetPos = null;
        launcher = null;
        selectionType = SelectionType.NONE;
        currentArmSelection.clear();
        currentPipetteSelection.clear();
    }

    private static void drawArmOutlines(List<ArmInteractionPoint> selection) {
        Iterator<ArmInteractionPoint> iterator = selection.iterator();
        while (iterator.hasNext()) {
            ArmInteractionPoint point = iterator.next();
            if (!point.isValid()) {
                iterator.remove();
                continue;
            }

            Level level = point.getLevel();
            BlockPos pos = point.getPos();
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getShape(level, pos);

            if (!shape.isEmpty()) {
                int color = point.getMode().getColor();
                Outliner.getInstance()
                        .showAABB(point, shape.bounds().move(pos))
                        .colored(color)
                        .lineWidth(0.0625F);
            }
        }
    }

    private static void drawPipetteOutlines(List<FluidInteractionPoint> selection) {
        Iterator<FluidInteractionPoint> iterator = selection.iterator();
        while (iterator.hasNext()) {
            FluidInteractionPoint point = iterator.next();
            if (!point.isValid()) {
                iterator.remove();
                continue;
            }

            Level level = point.getLevel();
            BlockPos pos = point.getPos();
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getShape(level, pos);

            if (!shape.isEmpty()) {
                int color = point.getMode().getColor();
                Outliner.getInstance()
                        .showAABB(point, shape.bounds().move(pos))
                        .colored(color)
                        .lineWidth(0.0625F);
            }
        }
    }
}