package com.adonis.fluid.block.CentrifugalPump;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class CentrifugalPumpRenderer extends KineticBlockEntityRenderer<CentrifugalPumpBlockEntity> {

    public CentrifugalPumpRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(CentrifugalPumpBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {

        if (!VisualizationManager.supportsVisualization(be.getLevel())) {
            BlockState blockState = be.getBlockState();
            if (!(blockState.getBlock() instanceof CentrifugalPumpBlock)) {
                return;
            }

            // 使用CentrifugalPumpBlock的静态方法获取轴方向
            Direction shaftDirection = CentrifugalPumpBlock.getShaftDirection(blockState);

            // 获取轴后面的光照
            int lightBehind = LevelRenderer.getLightColor(be.getLevel(),
                    be.getBlockPos().relative(shaftDirection));

            // 渲染半根轴
            VertexConsumer vb = buffer.getBuffer(RenderType.cutoutMipped());
            SuperByteBuffer shaftHalf = CachedBuffers.partialFacing(
                    AllPartialModels.SHAFT_HALF,
                    blockState,
                    shaftDirection
            );

            // 应用旋转变换并渲染
            standardKineticRotationTransform(shaftHalf, be, lightBehind)
                    .renderInto(ms, vb);
        }
    }

    @Override
    protected SuperByteBuffer getRotatedModel(CentrifugalPumpBlockEntity be, BlockState state) {
        // 离心泵本身不需要旋转模型，只有轴在旋转
        return null;
    }
}