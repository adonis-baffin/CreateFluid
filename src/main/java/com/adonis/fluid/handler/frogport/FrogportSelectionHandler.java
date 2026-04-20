package com.adonis.fluid.handler.frogport;

import com.adonis.fluid.handler.EditModeManager;
import com.adonis.fluid.mixin.accessor.ChainConveyorOBBAccessor;
import com.adonis.fluid.mixin.accessor.ChainConveyorShapeAccessor;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorInteractionHandler;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorShape;
import com.simibubi.create.foundation.utility.RaycastHelper;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class FrogportSelectionHandler {
    private static BlockPos selectedFrogportPos = null;
    private static boolean hasSelection = false;
    private static int statusUpdateCounter = 0;
    private static final Color FROGPORT_HIGHLIGHT_COLOR = new Color(14532966);
    private static final Color CONNECTION_PREVIEW_COLOR = new Color(10416499);
    private static final Color CHAIN_SELECTION_COLOR = new Color(16777215);

    public static boolean hasSelection() {
        return hasSelection && selectedFrogportPos != null;
    }

    public static BlockPos getSelectedFrogportPos() {
        return selectedFrogportPos;
    }

    public static void setSelection(BlockPos pos) {
        selectedFrogportPos = pos;
        hasSelection = true;
    }

    public static void clearSelection() {
        selectedFrogportPos = null;
        hasSelection = false;
        clearChainSelection();
    }

    private static void clearChainSelection() {
        ChainConveyorInteractionHandler.selectedLift = null;
        ChainConveyorInteractionHandler.selectedShape = null;
        ChainConveyorInteractionHandler.selectedConnection = null;
        ChainConveyorInteractionHandler.selectedBakedPosition = null;
        Outliner.getInstance().remove("BatonChainPointSelection");
    }

    public static void tickChainConveyor(Minecraft mc) {
        if (mc.level != null && mc.player != null) {
            if (!hasSelection()) {
                clearChainSelection();
            } else {
                double range = mc.gameMode.getPickRange() + 1.0;
                Vec3 from = mc.player.getEyePosition();
                Vec3 to = RaycastHelper.getTraceTarget(mc.player, range, from);
                HitResult hitResult = mc.hitResult;
                double bestDiff = Float.MAX_VALUE;
                if (hitResult != null && hitResult.getType() == Type.BLOCK) {
                    bestDiff = hitResult.getLocation().distanceToSqr(from);
                } else {
                    bestDiff = range * range;
                }

                BlockPos bestLift = null;
                ChainConveyorShape bestShape = null;
                float bestChainPosition = 0.0F;

                var loadedChains = ChainConveyorInteractionHandler.loadedChains.get(mc.level);
                if (loadedChains != null) {
                    for (Map.Entry<BlockPos, List<ChainConveyorShape>> entry : loadedChains.asMap().entrySet()) {
                        BlockPos liftPos = entry.getKey();
                        for (ChainConveyorShape chainConveyorShape : entry.getValue()) {
                            Vec3 liftVec = Vec3.atLowerCornerOf(liftPos);
                            Vec3 intersect = chainConveyorShape.intersect(from.subtract(liftVec), to.subtract(liftVec));
                            if (intersect != null) {
                                double distanceToSqr = intersect.add(liftVec).distanceToSqr(from);
                                if (!(distanceToSqr > bestDiff)) {
                                    bestDiff = distanceToSqr;
                                    bestLift = liftPos;
                                    bestShape = chainConveyorShape;
                                    bestChainPosition = chainConveyorShape.getChainPosition(intersect);
                                }
                            }
                        }
                    }

                    ChainConveyorInteractionHandler.selectedLift = bestLift;
                    ChainConveyorInteractionHandler.selectedChainPosition = bestChainPosition;
                    ChainConveyorInteractionHandler.selectedConnection = null;
                    if (bestShape instanceof ChainConveyorShape.ChainConveyorOBB obb) {
                        ChainConveyorInteractionHandler.selectedConnection = ((ChainConveyorOBBAccessor) obb).createfluid$getConnection();
                    }

                    if (bestLift == null) {
                        Outliner.getInstance().remove("BatonChainPointSelection");
                    } else {
                        ChainConveyorInteractionHandler.selectedShape = bestShape;
                        ChainConveyorInteractionHandler.selectedBakedPosition = bestShape.getVec(bestLift, bestChainPosition);
                        Outliner.getInstance()
                                .chaseAABB(
                                        "BatonChainPointSelection",
                                        new AABB(ChainConveyorInteractionHandler.selectedBakedPosition, ChainConveyorInteractionHandler.selectedBakedPosition)
                                )
                                .colored(CHAIN_SELECTION_COLOR.getRGB())
                                .lineWidth(0.16666667F)
                                .disableLineNormals();
                    }
                }
            }
        } else {
            clearChainSelection();
        }
    }

    public static void drawChainContour(com.mojang.blaze3d.vertex.PoseStack ms, MultiBufferSource buffer, Vec3 camera) {
        if (ChainConveyorInteractionHandler.selectedLift != null) {
            Minecraft mc = Minecraft.getInstance();
            var loadedChains = ChainConveyorInteractionHandler.loadedChains.get(mc.level);
            if (loadedChains != null) {
                var vb = buffer.getBuffer(RenderType.lines());
                Set<BlockPos> visited = new HashSet<>();
                List<BlockPos> toVisit = new ArrayList<>();
                toVisit.add(ChainConveyorInteractionHandler.selectedLift);

                while (!toVisit.isEmpty()) {
                    BlockPos current = toVisit.remove(toVisit.size() - 1);
                    if (!visited.contains(current)) {
                        visited.add(current);
                        List<ChainConveyorShape> shapes = loadedChains.getIfPresent(current);
                        if (shapes != null && !shapes.isEmpty()) {
                            for (ChainConveyorShape shape : shapes) {
                                ms.pushPose();
                                ms.translate((double) current.getX() - camera.x, (double) current.getY() - camera.y, (double) current.getZ() - camera.z);
                                ((ChainConveyorShapeAccessor) shape).createfluid$invokeDrawOutline(current, ms, vb);
                                ms.popPose();
                            }

                            Optional<ChainConveyorBlockEntity> beOpt = mc.level.getBlockEntity(current, AllBlockEntityTypes.CHAIN_CONVEYOR.get());
                            beOpt.ifPresent(be -> {
                                for (BlockPos connection : be.connections) {
                                    BlockPos connectedPos = current.offset(connection);
                                    if (!visited.contains(connectedPos) && mc.level.getBlockState(connectedPos).is((Block) AllBlocks.CHAIN_CONVEYOR.get())) {
                                        toVisit.add(connectedPos);
                                    }
                                }
                            });
                        }
                    }
                }
            }
        }
    }

    public static void render(Minecraft mc) {
        if (!hasSelection()) {
            clearOutlines();
        } else {
            renderFrogportHighlight(mc);
            if (ChainConveyorInteractionHandler.selectedLift != null) {
                renderChainPreview(mc);
                updateDistanceStatus(mc);
            }
        }
    }

    public static void updateDistanceStatus(Minecraft mc) {
        if (mc.player != null) {
            Vec3 end = ChainConveyorInteractionHandler.selectedBakedPosition;
            if (end != null) {
                statusUpdateCounter++;
                if (statusUpdateCounter >= 5) {
                    statusUpdateCounter = 0;
                    BlockPos frogportPos = selectedFrogportPos;
                    boolean outOfRange = !end.closerThan(
                            Vec3.atBottomCenterOf(frogportPos), (double) AllConfigs.server().logistics.packagePortRange.get()
                    );
                    String translationKey = outOfRange ? "create_fluid.baton.frogport.too_far" : "create_fluid.baton.frogport.can_connect";
                    int color = outOfRange ? 16736625 : 10416499;
                    CreateLang.builder().translate(translationKey).color(color).sendStatus(mc.player);
                }
            }
        }
    }

    private static void renderFrogportHighlight(Minecraft mc) {
        BlockPos pos = selectedFrogportPos;
        if (pos == null) return;
        var state = mc.level.getBlockState(pos);
        VoxelShape shape = state.getShape(mc.level, pos);
        if (!shape.isEmpty()) {
            Outliner.getInstance().showAABB("BatonFrogportHighlight", shape.bounds().move(pos)).colored(FROGPORT_HIGHLIGHT_COLOR.getRGB()).lineWidth(0.0625F);
        }
    }

    private static void renderChainPreview(Minecraft mc) {
        Vec3 start = Vec3.atCenterOf(selectedFrogportPos);
        Vec3 end = ChainConveyorInteractionHandler.selectedBakedPosition;
        boolean outOfRange = !end.closerThan(
                Vec3.atBottomCenterOf(selectedFrogportPos), (double) AllConfigs.server().logistics.packagePortRange.get()
        );
        Color color = outOfRange ? new Color(16736625) : CONNECTION_PREVIEW_COLOR;
        animateConnection(mc, start, end, color);
    }

    private static void animateConnection(Minecraft mc, Vec3 source, Vec3 target, Color color) {
        DustParticleOptions data = new DustParticleOptions(color.asVectorF(), 1.0F);
        double totalFlyingTicks = 10.0;
        int segments = (int) totalFlyingTicks / 3 + 1;
        double tickOffset = totalFlyingTicks / (double) segments;

        for (int i = 0; i < segments; i++) {
            double ticks = (double) (AnimationTickHolder.getRenderTime() / 3.0F) % tickOffset + (double) i * tickOffset;
            Vec3 vec = source.lerp(target, ticks / totalFlyingTicks);
            mc.level.addParticle(data, vec.x, vec.y, vec.z, 0.0, 0.0, 0.0);
        }
    }

    private static void clearOutlines() {
        Outliner.getInstance().remove("BatonFrogportHighlight");
        clearChainSelection();
    }

    public static void playSelectionSuccessEffect(Minecraft mc, BlockPos pos) {
        mc.level.playLocalSound(
                (double) pos.getX() + 0.5,
                (double) pos.getY() + 0.5,
                (double) pos.getZ() + 0.5,
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.BLOCKS,
                0.5F,
                1.0F,
                false
        );

        for (int i = 0; i < 20; i++) {
            double angle = (Math.PI * 2) * (double) i / 20.0;
            double radius = 0.7;
            double x = (double) pos.getX() + 0.5 + Math.cos(angle) * radius;
            double y = (double) pos.getY() + 0.5;
            double z = (double) pos.getZ() + 0.5 + Math.sin(angle) * radius;
            mc.level.addParticle(new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 1.5F), x, y, z, 0.0, 0.05, 0.0);
        }
    }

    public static boolean isValidFrogport(BlockPos pos) {
        return AllBlocks.PACKAGE_FROGPORT.has(Minecraft.getInstance().level.getBlockState(pos));
    }

    public static boolean onUse() {
        Minecraft mc = Minecraft.getInstance();
        if (ChainConveyorInteractionHandler.selectedLift == null) {
            return false;
        } else {
            com.simibubi.create.content.logistics.packagePort.PackagePortTargetSelectionHandler.exactPositionOfTarget = ChainConveyorInteractionHandler.selectedBakedPosition;
            com.simibubi.create.content.logistics.packagePort.PackagePortTargetSelectionHandler.activePackageTarget = new com.simibubi.create.content.logistics.packagePort.PackagePortTarget.ChainConveyorFrogportTarget(
                    ChainConveyorInteractionHandler.selectedLift,
                    ChainConveyorInteractionHandler.selectedChainPosition,
                    ChainConveyorInteractionHandler.selectedConnection
            );
            return true;
        }
    }
}
