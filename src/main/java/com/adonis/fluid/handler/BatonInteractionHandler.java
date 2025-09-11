package com.adonis.fluid.handler;

import com.adonis.fluid.block.Pipette.PipetteBlockEntity;
import com.adonis.fluid.content.pipette.FluidInteractionPoint;
import com.adonis.fluid.item.BatonItem;
import com.adonis.fluid.mixin.accessor.ArmBlockEntityAccessor;
import com.adonis.fluid.packet.PipetteFluidPlacementPacket;
import com.simibubi.create.AllPackets;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmPlacementPacket;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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

    // 当前选中的目标（动力臂或移液器）
    private static BlockEntity selectedTarget = null;
    private static BlockPos selectedTargetPos = null;

    // 动力臂相关 - 直接存储交互点
    private static List<ArmInteractionPoint> currentArmSelection = new ArrayList<>();

    // 移液器相关
    private static List<FluidInteractionPoint> currentPipetteSelection = new ArrayList<>();

    // 选择模式类型
    private enum SelectionType {
        NONE, ARM, PIPETTE
    }
    private static SelectionType selectionType = SelectionType.NONE;

    // 粒子效果计数器
    private static int particleCounter = 0;

    public static boolean isInSelectionMode() {
        return selectionType != SelectionType.NONE && selectedTarget != null;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack heldItem = player.getMainHandItem();

        // 检查是否手持指挥棒
        if (!(heldItem.getItem() instanceof BatonItem)) {
            return;
        }

        Level level = event.getLevel();
        if (!level.isClientSide) return;

        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);

        // 如果点击的是动力臂或移液器
        if (be instanceof ArmBlockEntity || be instanceof PipetteBlockEntity) {
            handleTargetClick(be, pos, player, level);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        // 如果在选择模式下点击其他方块
        if (isInSelectionMode()) {
            BlockState state = level.getBlockState(pos);

            if (selectionType == SelectionType.ARM) {
                // 模仿原版的行为
                ArmInteractionPoint selected = getSelectedArm(pos);
                if (selected == null) {
                    ArmInteractionPoint point = ArmInteractionPoint.create(level, pos, state);
                    if (point == null) {
                        return;
                    }
                    selected = point;
                    putArm(point);
                    // 播放音效和粒子效果
                    playSelectionEffects(level, pos, true);
                }

                selected.cycleMode();

                // 显示消息
                ArmInteractionPoint.Mode mode = selected.getMode();
                CreateLang.builder()
                        .translate(mode.getTranslationKey(),
                                CreateLang.blockName(state).style(ChatFormatting.WHITE))
                        .color(mode.getColor())
                        .sendStatus(player);

                // 播放切换音效
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3f, 2.0f, false);

            } else if (selectionType == SelectionType.PIPETTE) {
                handlePipettePointInteraction(level, pos, state, player);
            }

            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        ItemStack heldItem = player.getMainHandItem();

        if (!(heldItem.getItem() instanceof BatonItem)) {
            return;
        }

        if (isInSelectionMode()) {
            // 在客户端和服务端都取消
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);

            // 只在客户端处理移除逻辑
            if (event.getLevel().isClientSide) {
                BlockPos pos = event.getPos();

                // 移除交互点
                boolean removed = false;
                if (selectionType == SelectionType.ARM) {
                    int sizeBefore = currentArmSelection.size();
                    removeArm(pos);
                    removed = currentArmSelection.size() < sizeBefore;

                    if (removed) {
                        CreateLang.builder()
                                .text("Interaction point removed")
                                .style(ChatFormatting.RED)
                                .sendStatus(player);
                        // 播放移除音效
                        event.getLevel().playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 0.8f, false);
                    }
                } else if (selectionType == SelectionType.PIPETTE) {
                    int sizeBefore = currentPipetteSelection.size();
                    removePipette(pos);
                    removed = currentPipetteSelection.size() < sizeBefore;

                    if (removed) {
                        CreateLang.builder()
                                .text("Interaction point removed")
                                .style(ChatFormatting.RED)
                                .sendStatus(player);
                        // 播放移除音效
                        event.getLevel().playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 0.8f, false);
                    }
                }
            }
        }
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

    private static void handleTargetClick(BlockEntity be, BlockPos pos, Player player, Level level) {
        // 如果已经选中了这个目标，保存并退出
        if (selectedTarget == be && selectedTargetPos.equals(pos)) {
            // 创建确认时的粒子效果（与初选时相同）
            createSelectionSuccessParticles(level, pos);
            flushSettings(pos);
            return;
        }

        // 进入新的选择模式
        cancelSelection();

        selectedTarget = be;
        selectedTargetPos = pos;

        // 播放进入选择模式的音效
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5f, 1.0f, false);

        // 创建选择成功的粒子效果
        createSelectionSuccessParticles(level, pos);

        if (be instanceof ArmBlockEntity arm) {
            selectionType = SelectionType.ARM;
            currentArmSelection.clear();

            // 加载现有交互点
            ArmBlockEntityAccessor accessor = (ArmBlockEntityAccessor) arm;
            List<ArmInteractionPoint> inputs = accessor.getInputs();
            List<ArmInteractionPoint> outputs = accessor.getOutputs();

            // 添加现有点到选择中
            currentArmSelection.addAll(inputs);
            currentArmSelection.addAll(outputs);

            // 显示信息
            CreateLang.builder()
                    .translate("create.mechanical_arm.summary", inputs.size(), outputs.size())
                    .style(ChatFormatting.WHITE)
                    .sendStatus(player);

        } else if (be instanceof PipetteBlockEntity pipette) {
            selectionType = SelectionType.PIPETTE;
            currentPipetteSelection.clear();

            currentPipetteSelection.addAll(pipette.inputs);
            currentPipetteSelection.addAll(pipette.outputs);

            // 显示信息
            CreateLang.builder()
                    .translate("fluid.mechanical_pipette.summary",
                            pipette.inputs.size(), pipette.outputs.size())
                    .style(ChatFormatting.WHITE)
                    .sendStatus(player);
        }
    }

    private static void flushSettings(BlockPos armPos) {
        if (selectionType == SelectionType.ARM) {
            // 检查范围
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

            // 发送到服务器
            AllPackets.getChannel().sendToServer(new ArmPlacementPacket(currentArmSelection, armPos));

        } else if (selectionType == SelectionType.PIPETTE) {
            // 检查范围
            int removed = 0;
            Iterator<FluidInteractionPoint> iterator = currentPipetteSelection.iterator();
            while (iterator.hasNext()) {
                FluidInteractionPoint point = iterator.next();
                if (!point.getPos().closerThan(armPos, PipetteBlockEntity.getRange())) {
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

            AllPackets.getChannel().sendToServer(new PipetteFluidPlacementPacket(currentPipetteSelection, armPos));
        }

        // 播放完成音效
        Minecraft.getInstance().level.playLocalSound(armPos.getX() + 0.5, armPos.getY() + 0.5, armPos.getZ() + 0.5,
                SoundEvents.NOTE_BLOCK_CHIME.get(), SoundSource.BLOCKS, 0.8f, 1.0f, false);

        // 清理
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
            // 播放音效和粒子效果
            playSelectionEffects(level, pos, true);
        }

        selected.cycleMode();

        // 显示消息
        FluidInteractionPoint.Mode mode = selected.getMode();
        CreateLang.builder()
                .translate(mode.getTranslationKey(),
                        CreateLang.blockName(state).style(ChatFormatting.WHITE))
                .color(mode.getColor())
                .sendStatus(player);

        // 播放切换音效
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3f, 2.0f, false);
    }

    private static void playSelectionEffects(Level level, BlockPos pos, boolean isNew) {
        if (isNew) {
            // 播放添加音效
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.3f, 1.5f, false);
        }

        // 创建白色红石粉粒子
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
        // 创建环形粒子效果
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
        selectionType = SelectionType.NONE;
        currentArmSelection.clear();
        currentPipetteSelection.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        // 检查是否仍然手持指挥棒
        ItemStack heldItem = player.getMainHandItem();
        if (!(heldItem.getItem() instanceof BatonItem)) {
            if (isInSelectionMode()) {
                cancelSelection();
            }
            return;
        }

        // 在选择模式下绘制轮廓和粒子效果
        if (isInSelectionMode()) {
            particleCounter++;

            // 每tick都调用，由createContinuousParticles内部控制频率
            if (selectedTargetPos != null) {
                createContinuousParticles(Minecraft.getInstance().level, selectedTargetPos);
            }

            if (selectionType == SelectionType.ARM) {
                drawArmOutlines(currentArmSelection);
            } else if (selectionType == SelectionType.PIPETTE) {
                drawPipetteOutlines(currentPipetteSelection);
            }
        }
    }

    private static void createContinuousParticles(Level level, BlockPos pos) {
        Random random = new Random();

        // 只保留环绕粒子，提高频率
        if (particleCounter % 10 == 0) { // 每0.5秒一次
            for (int i = 0; i < 6; i++) { // 产生6个粒子
                double angle = (Math.PI * 2) * i / 6;
                double radius = 0.7 + random.nextDouble() * 0.2; // 略微随机的半径
                double x = pos.getX() + 0.5 + Math.cos(angle) * radius;
                double y = pos.getY() + 0.5 + random.nextDouble() * 0.8; // 在方块高度范围内
                double z = pos.getZ() + 0.5 + Math.sin(angle) * radius;

                // 使用白色红石粉粒子，略微向上飘动
                level.addParticle(new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 0.6F),
                        x, y, z, 0, 0.01, 0);
            }
        }
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