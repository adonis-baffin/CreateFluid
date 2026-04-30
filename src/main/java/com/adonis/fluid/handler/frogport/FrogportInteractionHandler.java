package com.adonis.fluid.handler.frogport;

import com.adonis.fluid.handler.BatonInteractionHandler;
import com.adonis.fluid.handler.EditModeManager;
import com.adonis.fluid.item.BatonItem;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.foundation.utility.RaycastHelper;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.adonis.fluid.CreateFluid.MOD_ID;

@EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class FrogportInteractionHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack heldItem = player.getMainHandItem();
        if (!(heldItem.getItem() instanceof BatonItem)) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);

        if (AllBlocks.PACKAGE_FROGPORT.has(state)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
            if (level.isClientSide) {
                if (EditModeManager.isValidInteraction(pos, state, be, level)) {
                    handleFrogportClick(pos, player, level, player.isShiftKeyDown());
                } else {
                    EditModeManager.exitMode(player, level);
                }
            }
        }
    }

    public static void handleFrogportClick(BlockPos pos, Player player, Level level, boolean sneaking) {
        BlockPos selectedFrogportPos = FrogportSelectionHandler.getSelectedFrogportPos();
        if (EditModeManager.getCurrentMode() == EditModeManager.EditMode.FROGPORT
                && selectedFrogportPos != null && selectedFrogportPos.equals(pos)) {
            if (FrogportSelectionHandler.hasPendingTarget()) {
                PacketDistributor.sendToServer(new com.adonis.fluid.packet.FrogportConnectionPacket(
                        pos,
                        FrogportSelectionHandler.getPendingLiftPos(),
                        FrogportSelectionHandler.getPendingChainPosition(),
                        FrogportSelectionHandler.getPendingConnection()));
                FrogportSelectionHandler.playSelectionSuccessEffect(Minecraft.getInstance(), pos);
                EditModeManager.exitMode(player, level, false);
            } else {
                EditModeManager.exitMode(player, level);
                sendPlayerMessage(player, "create_fluid.baton.frogport.deselected", ChatFormatting.GRAY);
            }
        } else {
            EditModeManager.enterMode(EditModeManager.EditMode.FROGPORT, pos, player, level);
            FrogportSelectionHandler.setSelection(pos);
            FrogportSelectionHandler.playSelectionSuccessEffect(Minecraft.getInstance(), pos);
            sendPlayerMessage(player, "create_fluid.baton.frogport.selected", ChatFormatting.GOLD);
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (EditModeManager.isInEditMode() && EditModeManager.getCurrentMode() == EditModeManager.EditMode.FROGPORT) {
            Minecraft mc = Minecraft.getInstance();
            BlockPos selectedFrogportPos = FrogportSelectionHandler.getSelectedFrogportPos();
            if (selectedFrogportPos != null
                    && mc.hitResult instanceof BlockHitResult hit
                    && hit.getType() == HitResult.Type.BLOCK
                    && selectedFrogportPos.equals(hit.getBlockPos())) {
                return;
            }
            event.setCanceled(true);
            event.setSwingHand(true);
            handleFrogportChainClick(null, mc.player, mc.level);
        }
    }

    public static void handleFrogportChainClick(BlockPos pos, Player player, Level level) {
        if (!level.isClientSide) return;
        if (!FrogportSelectionHandler.hasSelection()) return;

        BlockPos frogportPos = FrogportSelectionHandler.getSelectedFrogportPos();
        BlockPos selectedLift = null;
        float selectedChainPosition = 0.0F;
        BlockPos selectedConnection = null;
        Minecraft mc = Minecraft.getInstance();
        double range = mc.player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1.0;
        Vec3 from = mc.player.getEyePosition();
        Vec3 to = RaycastHelper.getTraceTarget(mc.player, range, from);
        net.minecraft.world.phys.HitResult hitResult = mc.hitResult;
        double bestDiff = Float.MAX_VALUE;
        if (hitResult != null && hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            bestDiff = hitResult.getLocation().distanceToSqr(from);
        } else {
            bestDiff = range * range;
        }

        var loadedChains = com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorInteractionHandler.loadedChains.get(level);
        if (loadedChains == null) {
            sendPlayerMessage(player, "create_fluid.baton.frogport.invalid_chain", 16736625);
            return;
        }

        for (var entry : loadedChains.asMap().entrySet()) {
            BlockPos liftPos = entry.getKey();
            for (com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorShape chainConveyorShape : entry.getValue()) {
                Vec3 liftVec = Vec3.atLowerCornerOf(liftPos);
                Vec3 intersect = chainConveyorShape.intersect(from.subtract(liftVec), to.subtract(liftVec));
                if (intersect != null) {
                    double distanceToSqr = intersect.add(liftVec).distanceToSqr(from);
                    if (!(distanceToSqr > bestDiff)) {
                        bestDiff = distanceToSqr;
                        selectedLift = liftPos;
                        selectedChainPosition = chainConveyorShape.getChainPosition(intersect);
                        if (chainConveyorShape instanceof com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorShape.ChainConveyorOBB obb) {
                            selectedConnection = ((com.adonis.fluid.mixin.accessor.ChainConveyorOBBAccessor) obb).createfluid$getConnection();
                        }
                    }
                }
            }
        }

        if (selectedLift == null) {
            EditModeManager.exitMode(player, level);
            CreateLang.builder().translate("create_fluid.baton.frogport.finished").style(ChatFormatting.GRAY).sendStatus(player);
        } else {
            Vec3 targetLocation = new PackagePortTarget.ChainConveyorFrogportTarget(
                    selectedLift.subtract(frogportPos), selectedChainPosition, selectedConnection, false)
                    .getExactTargetLocation(null, level, frogportPos);
            if (targetLocation != Vec3.ZERO
                    && targetLocation.closerThan(
                    Vec3.atBottomCenterOf(frogportPos),
                    (double) AllConfigs.server().logistics.packagePortRange.get())) {
                FrogportSelectionHandler.setPendingTarget(level, selectedLift, selectedChainPosition, selectedConnection, targetLocation);
                sendPlayerMessage(player, "create_fluid.baton.frogport.target_selected", 10416499);
                FrogportSelectionHandler.playSelectionSuccessEffect(mc, selectedLift);
            } else {
                sendPlayerMessage(player, "create_fluid.baton.frogport.too_far", 16736625);
            }
        }
    }

    private static void drawFrogportConnectionPreview(Minecraft mc) {
        if (mc.level != null && mc.player != null && mc.hitResult != null) {
            net.minecraft.world.phys.HitResult hitResult = mc.hitResult;
            if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                BlockPos pos = ((net.minecraft.world.phys.BlockHitResult) hitResult).getBlockPos();
                BlockState state = mc.level.getBlockState(pos);
                if (AllBlocks.PACKAGE_FROGPORT.has(state)) {
                    if (mc.level.getBlockEntity(pos) instanceof PackagePortBlockEntity ppbe) {
                        PackagePortTarget target = ppbe.target;
                        if (target instanceof PackagePortTarget.ChainConveyorFrogportTarget) {
                            Vec3 targetLocation = target.getExactTargetLocation(ppbe, mc.level, pos);
                            if (targetLocation != null && targetLocation != Vec3.ZERO) {
                                Vec3 frogportPos = Vec3.atCenterOf(pos);
                                int color = 10416499;
                                net.createmod.catnip.outliner.Outliner.getInstance()
                                        .chaseAABB("BatonFrogportConnectionTarget", new net.minecraft.world.phys.AABB(targetLocation, targetLocation))
                                        .colored(color)
                                        .lineWidth(0.16666667F)
                                        .disableLineNormals();
                                BatonInteractionHandler.animateConnection(mc, frogportPos, targetLocation, color);
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            if (EditModeManager.isInEditMode() && EditModeManager.getCurrentMode() == EditModeManager.EditMode.FROGPORT) {
                var ms = event.getPoseStack();
                ms.pushPose();
                var buffer = net.createmod.catnip.render.DefaultSuperRenderTypeBuffer.getInstance();
                Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                FrogportSelectionHandler.tickChainConveyor(Minecraft.getInstance());
                FrogportSelectionHandler.drawChainContour(ms, buffer, camera);
                buffer.draw();
                ms.popPose();
            }
        }
    }

    @SubscribeEvent
    public static void hideVanillaBlockSelection(RenderHighlightEvent.Block event) {
        if (com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorInteractionHandler.selectedLift != null
                && FrogportSelectionHandler.hasSelection()) {
            event.setCanceled(true);
        }
    }

    public static void render(Minecraft mc) {
        drawFrogportConnectionPreview(mc);
    }

    public static void sendPlayerMessage(Player player, String translationKey, ChatFormatting color) {
        CreateLang.builder().translate(translationKey).style(color).sendStatus(player);
    }

    public static void sendPlayerMessage(Player player, String translationKey, int color) {
        CreateLang.builder().translate(translationKey).color(color).sendStatus(player);
    }
}
