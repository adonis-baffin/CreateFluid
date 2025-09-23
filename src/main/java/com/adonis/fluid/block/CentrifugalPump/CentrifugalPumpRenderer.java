package com.adonis.fluid.block.CentrifugalPump;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;

public class CentrifugalPumpRenderer extends KineticBlockEntityRenderer<CentrifugalPumpBlockEntity> {

    public CentrifugalPumpRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(CentrifugalPumpBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {

        BlockState state = be.getBlockState();
        boolean isEncased = state.hasProperty(CentrifugalPumpBlock.ENCASED) && state.getValue(CentrifugalPumpBlock.ENCASED);

        // 始终调用父类的renderSafe来处理BlockEntityBehaviour（包括ValueBox等）
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        // 只在非封装状态且Flywheel未启用时渲染传动杆
        if (!isEncased && !VisualizationManager.supportsVisualization(be.getLevel())) {
            renderShaft(be, ms, buffer, light, overlay);
        }
    }

    protected void renderShaft(CentrifugalPumpBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        // 使用父类的方法来渲染旋转的传动杆
        KineticBlockEntityRenderer.renderRotatingBuffer(be, getRotatedModel(be), ms,
                buffer.getBuffer(RenderType.solid()), light);
    }

    protected SuperByteBuffer getRotatedModel(KineticBlockEntity be) {
        BlockState state = be.getBlockState();
        Direction shaftDirection = CentrifugalPumpBlock.getShaftDirection(state);
        AttachFace face = state.getValue(CentrifugalPumpBlock.FACE);

        // 根据不同的放置模式返回正确朝向的传动杆模型
        if (face == AttachFace.WALL) {
            // 垂直模式，轴向上
            return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, Direction.DOWN);
        } else if (face == AttachFace.FLOOR) {
            // 地板模式，轴在facing的反方向
            return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, shaftDirection.getOpposite());
        } else { // CEILING
            // 天花板模式，轴在facing的反方向
            return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, shaftDirection.getOpposite());
        }
    }

    @Override
    protected BlockState getRenderedBlockState(CentrifugalPumpBlockEntity be) {
        // 返回带有正确旋转轴的轴方块状态
        return KineticBlockEntityRenderer.shaft(KineticBlockEntityRenderer.getRotationAxisOf(be));
    }
}