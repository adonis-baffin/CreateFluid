package com.adonis.fluid.handler;

import com.adonis.fluid.block.Pipette.PipetteBlockEntity;
import com.adonis.fluid.packet.PipettePlacementPacket;
import com.adonis.fluid.registry.CFBlock;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllPackets;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint.Mode;
import com.simibubi.create.foundation.utility.CreateLang;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber({Dist.CLIENT})
public class PipetteInteractionPointHandler {
    static List<ArmInteractionPoint> currentSelection = new ArrayList<>();
    static ItemStack currentItem;
    static long lastBlockPos = -1L;

    public PipetteInteractionPointHandler() {
    }

    @SubscribeEvent
    public static void rightClickingBlocksSelectsThem(PlayerInteractEvent.RightClickBlock event) {
        if (currentItem != null) {
            BlockPos pos = event.getPos();
            Level world = event.getLevel();
            if (world.isClientSide) {
                Player player = event.getEntity();
                if (player == null || !player.isSpectator()) {
                    ArmInteractionPoint selected = getSelected(pos);
                    BlockState state = world.getBlockState(pos);
                    if (selected == null) {
                        ArmInteractionPoint point = ArmInteractionPoint.create(world, pos, state);
                        if (point == null) {
                            return;
                        }

                        selected = point;
                        put(point);
                    }

                    selected.cycleMode();
                    if (player != null) {
                        ArmInteractionPoint.Mode mode = selected.getMode();
                        CreateLang.builder().translate(mode.getTranslationKey(), 
                            CreateLang.blockName(state).style(ChatFormatting.WHITE))
                            .color(mode.getColor()).sendStatus(player);
                    }

                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                }
            }
        }
    }

    @SubscribeEvent
    public static void leftClickingBlocksDeselectsThem(PlayerInteractEvent.LeftClickBlock event) {
        if (currentItem != null) {
            if (event.getLevel().isClientSide) {
                BlockPos pos = event.getPos();
                if (remove(pos) != null) {
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                }
            }
        }
    }

    public static void flushSettings(BlockPos pos) {
        if (currentSelection != null) {
            int removed = 0;
            Iterator<ArmInteractionPoint> iterator = currentSelection.iterator();

            while(iterator.hasNext()) {
                ArmInteractionPoint point = iterator.next();
                if (!point.getPos().closerThan(pos, PipetteBlockEntity.getRange())) {
                    iterator.remove();
                    ++removed;
                }
            }

            LocalPlayer player = Minecraft.getInstance().player;
            if (removed > 0) {
                CreateLang.builder().translate("mechanical_arm.points_outside_range", removed)
                    .style(ChatFormatting.RED).sendStatus(player);
            } else {
                int inputs = 0;
                int outputs = 0;
                for (ArmInteractionPoint armInteractionPoint : currentSelection) {
                    if (armInteractionPoint.getMode() == Mode.DEPOSIT) {
                        ++outputs;
                    } else {
                        ++inputs;
                    }
                }

                if (inputs + outputs > 0) {
                    CreateLang.builder().translate("mechanical_arm.summary", inputs, outputs)
                        .style(ChatFormatting.WHITE).sendStatus(player);
                }
            }

            AllPackets.getChannel().sendToServer(new PipettePlacementPacket(currentSelection, pos));
            currentSelection.clear();
            currentItem = null;
        }
    }

    public static void tick() {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            ItemStack heldItemMainhand = player.getMainHandItem();
            if (!CFBlock.PIPETTE.isIn(heldItemMainhand)) {
                currentItem = null;
            } else {
                if (heldItemMainhand != currentItem) {
                    currentSelection.clear();
                    currentItem = heldItemMainhand;
                }

                drawOutlines(currentSelection);
            }

            checkForWrench(heldItemMainhand);
        }
    }

    private static void checkForWrench(ItemStack heldItem) {
        if (AllItems.WRENCH.isIn(heldItem)) {
            HitResult objectMouseOver = Minecraft.getInstance().hitResult;
            if (objectMouseOver instanceof BlockHitResult) {
                BlockHitResult result = (BlockHitResult)objectMouseOver;
                BlockPos pos = result.getBlockPos();
                BlockEntity be = Minecraft.getInstance().level.getBlockEntity(pos);
                if (!(be instanceof PipetteBlockEntity)) {
                    lastBlockPos = -1L;
                    currentSelection.clear();
                } else {
                    if (lastBlockPos == -1L || lastBlockPos != pos.asLong()) {
                        currentSelection.clear();
                        PipetteBlockEntity pipette = (PipetteBlockEntity)be;
                        pipette.inputs.forEach(PipetteInteractionPointHandler::put);
                        pipette.outputs.forEach(PipetteInteractionPointHandler::put);
                        lastBlockPos = pos.asLong();
                    }

                    if (lastBlockPos != -1L) {
                        drawOutlines(currentSelection);
                    }
                }
            }
        }
    }

    private static void drawOutlines(Collection<ArmInteractionPoint> selection) {
        Iterator<ArmInteractionPoint> iterator = selection.iterator();

        while(iterator.hasNext()) {
            ArmInteractionPoint point = iterator.next();
            if (!point.isValid()) {
                iterator.remove();
            } else {
                Level level = point.getLevel();
                BlockPos pos = point.getPos();
                BlockState state = level.getBlockState(pos);
                VoxelShape shape = state.getShape(level, pos);
                if (!shape.isEmpty()) {
                    int color = point.getMode().getColor();
                    Outliner.getInstance().showAABB(point, shape.bounds().move(pos))
                        .colored(color).lineWidth(0.0625F);
                }
            }
        }
    }

    private static void put(ArmInteractionPoint point) {
        currentSelection.add(point);
    }

    private static ArmInteractionPoint remove(BlockPos pos) {
        ArmInteractionPoint result = getSelected(pos);
        if (result != null) {
            currentSelection.remove(result);
        }
        return result;
    }

    private static ArmInteractionPoint getSelected(BlockPos pos) {
        for (ArmInteractionPoint point : currentSelection) {
            if (point.getPos().equals(pos)) {
                return point;
            }
        }
        return null;
    }
}