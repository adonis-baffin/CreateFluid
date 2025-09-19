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

        // 调用父类方法处理标准渲染（包括第一个面板）
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        BlockState state = be.getBlockState();

        // 检查是否是封装状态，如果是则不渲染第二个面板
        if (state.hasProperty(CentrifugalPumpBlock.ENCASED) && state.getValue(CentrifugalPumpBlock.ENCASED)) {
            return; // 封装状态下不渲染额外的UI元素
        }

        // 只在非封装状态下渲染第二个面板
        // pumpMode 只在非封装状态下存在
        if (be.pumpMode != null) {
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

                // 这里可以添加额外的渲染代码，如果需要的话
                // 例如渲染一个指示当前模式的图标等

                ms.popPose();
            }
        }
    }
}