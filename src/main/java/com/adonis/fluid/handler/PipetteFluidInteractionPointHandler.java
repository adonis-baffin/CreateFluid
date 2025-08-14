package com.adonis.fluid.handler;

import com.adonis.fluid.block.Pipette.PipetteBlockEntity;
import com.adonis.fluid.content.pipette.FluidInteractionPoint;
import com.adonis.fluid.packet.PipetteFluidPlacementPacket;
import com.adonis.fluid.registry.CFBlock;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllPackets;
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
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.FORGE)
public class PipetteFluidInteractionPointHandler {
    static List<FluidInteractionPoint> currentSelection = new ArrayList<>();
    static ItemStack currentItem;
    static long lastBlockPos = -1L;

    // 提高优先级，确保在置物台处理之前执行
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void rightClickingBlocksSelectsThem(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) {
            System.out.println("Right click event: item = " + event.getItemStack().getItem());
            System.out.println("Is pipette item? " + CFBlock.PIPETTE.isIn(event.getItemStack()));
            System.out.println("Current item: " + currentItem);
        }
        // 检查是否手持移液器方块物品
        if (!CFBlock.PIPETTE.isIn(event.getItemStack())) {
            return;
        }

        // 将 currentItem 赋值移到这里
        if (currentItem == null || !ItemStack.matches(currentItem, event.getItemStack())) {
            currentItem = event.getItemStack();
        }

        BlockPos pos = event.getPos();
        Level world = event.getLevel();

        if (world.isClientSide) {
            Player player = event.getEntity();
            if (player != null && !player.isSpectator()) {
                BlockState state = world.getBlockState(pos);

                // 检查是否可以创建流体交互点
                FluidInteractionPoint point = FluidInteractionPoint.create(world, pos, state);
                if (point == null) {
                    return;
                }

                FluidInteractionPoint selected = getSelected(pos);
                if (selected == null) {
                    selected = point;
                    put(point);
                }

                selected.cycleMode();

                // 发送状态消息
                FluidInteractionPoint.Mode mode = selected.getMode();
                CreateLang.builder().translate(mode.getTranslationKey(),
                                CreateLang.blockName(state).style(ChatFormatting.WHITE))
                        .color(mode.getColor()).sendStatus(player);

                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        } else {
            // 服务端也要取消事件，防止置物台处理
            BlockState state = world.getBlockState(pos);
            if (FluidInteractionPoint.create(world, pos, state) != null) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }

    @SubscribeEvent
    public static void leftClickingBlocksDeselectsThem(PlayerInteractEvent.LeftClickBlock event) {
        if (currentItem != null && CFBlock.PIPETTE.isIn(currentItem)) {
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
            Iterator<FluidInteractionPoint> iterator = currentSelection.iterator();

            while(iterator.hasNext()) {
                FluidInteractionPoint point = iterator.next();
                if (!point.getPos().closerThan(pos, PipetteBlockEntity.getRange())) {
                    iterator.remove();
                    ++removed;
                }
            }

            LocalPlayer player = Minecraft.getInstance().player;
            if (removed > 0) {
                CreateLang.builder().translate("fluid.mechanical_pipette.points_outside_range", removed)
                        .style(ChatFormatting.RED).sendStatus(player);
            } else {
                int inputs = 0;
                int outputs = 0;
                for (FluidInteractionPoint point : currentSelection) {
                    if (point.getMode() == FluidInteractionPoint.Mode.DEPOSIT) {
                        ++outputs;
                    } else {
                        ++inputs;
                    }
                }

                if (inputs + outputs > 0) {
                    CreateLang.builder().translate("fluid.mechanical_pipette.summary", inputs, outputs)
                            .style(ChatFormatting.WHITE).sendStatus(player);
                }
            }

            AllPackets.getChannel().sendToServer(new PipetteFluidPlacementPacket(currentSelection, pos));
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
                currentSelection.clear();
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
                        pipette.inputs.forEach(PipetteFluidInteractionPointHandler::put);
                        pipette.outputs.forEach(PipetteFluidInteractionPointHandler::put);
                        lastBlockPos = pos.asLong();
                    }

                    if (lastBlockPos != -1L) {
                        drawOutlines(currentSelection);
                    }
                }
            }
        } else {
            if (lastBlockPos != -1L) {
                lastBlockPos = -1L;
                currentSelection.clear();
            }
        }
    }

    private static void drawOutlines(Collection<FluidInteractionPoint> selection) {
        Iterator<FluidInteractionPoint> iterator = selection.iterator();

        while(iterator.hasNext()) {
            FluidInteractionPoint point = iterator.next();
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

    public static void put(FluidInteractionPoint point) {
        currentSelection.add(point);
    }

    private static FluidInteractionPoint remove(BlockPos pos) {
        FluidInteractionPoint result = getSelected(pos);
        if (result != null) {
            currentSelection.remove(result);
        }
        return result;
    }

    public static FluidInteractionPoint getSelected(BlockPos pos) {
        for (FluidInteractionPoint point : currentSelection) {
            if (point.getPos().equals(pos)) {
                return point;
            }
        }
        return null;
    }
}