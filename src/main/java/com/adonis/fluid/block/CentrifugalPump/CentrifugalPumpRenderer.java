package com.adonis.fluid.block.CentrifugalPump;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

public class CentrifugalPumpRenderer extends KineticBlockEntityRenderer<CentrifugalPumpBlockEntity> {

    public CentrifugalPumpRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(CentrifugalPumpBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {

        // 渲染传动轴
        if (!VisualizationManager.supportsVisualization(be.getLevel())) {
            BlockState blockState = be.getBlockState();
            if (!(blockState.getBlock() instanceof CentrifugalPumpBlock)) {
                return;
            }

            Direction shaftDirection = CentrifugalPumpBlock.getShaftDirection(blockState);
            AttachFace face = blockState.getValue(CentrifugalPumpBlock.FACE);

            int lightBehind = LevelRenderer.getLightColor(
                    be.getLevel(),
                    be.getBlockPos().relative(shaftDirection)
            );

            VertexConsumer vb = buffer.getBuffer(RenderType.cutoutMipped());
            SuperByteBuffer shaftHalf;

            if (face == AttachFace.WALL) {
                shaftHalf = CachedBuffers.partialFacing(
                        AllPartialModels.SHAFT_HALF,
                        blockState,
                        Direction.DOWN
                );
            } else if (face == AttachFace.FLOOR) {
                shaftHalf = CachedBuffers.partialFacing(
                        AllPartialModels.SHAFT_HALF,
                        blockState,
                        shaftDirection.getOpposite()
                );
            } else { // CEILING
                shaftHalf = CachedBuffers.partialFacing(
                        AllPartialModels.SHAFT_HALF,
                        blockState,
                        shaftDirection.getOpposite()
                );
            }

            standardKineticRotationTransform(shaftHalf, be, lightBehind)
                    .renderInto(ms, vb);
        }

        // 调用父类方法处理标准渲染（包括单个面板）
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        // 手动渲染第二个面板
        if (be.pumpMode != null && be.pumpMode.isActive()) {
            BlockState state = be.getBlockState();
            AttachFace attachFace = state.getValue(CentrifugalPumpBlock.FACE);
            Direction facing = state.getValue(CentrifugalPumpBlock.FACING);

            // 计算第二个面板的位置
            Vec3 secondPanelOffset = null;
            Direction secondPanelFacing = null;

            if (attachFace == AttachFace.WALL) {
                if (facing == Direction.NORTH || facing == Direction.SOUTH) {
                    // 第一个面板在西侧，第二个在东侧
                    secondPanelOffset = VecHelper.voxelSpace(16, 8, 8);
                    secondPanelFacing = Direction.EAST;
                } else {
                    // 第一个面板在北侧，第二个在南侧
                    secondPanelOffset = VecHelper.voxelSpace(8, 8, 16);
                    secondPanelFacing = Direction.SOUTH;
                }
            } else {
                if (facing == Direction.NORTH || facing == Direction.SOUTH) {
                    // 第一个面板在西侧，第二个在东侧
                    secondPanelOffset = VecHelper.voxelSpace(16, 8, 8);
                    secondPanelFacing = Direction.EAST;
                } else {
                    // 第一个面板在北侧，第二个在南侧
                    secondPanelOffset = VecHelper.voxelSpace(8, 8, 16);
                    secondPanelFacing = Direction.SOUTH;
                }
            }

            // 渲染第二个面板
            if (secondPanelOffset != null) {
                ms.pushPose();
                ms.translate(secondPanelOffset.x, secondPanelOffset.y, secondPanelOffset.z);

                // 这里需要实际的渲染代码，可能需要访问 ValueBoxRenderer 的内部方法
                // 或者创建一个临时的 ValueBoxTransform 来渲染

                ms.popPose();
            }
        }
    }
}