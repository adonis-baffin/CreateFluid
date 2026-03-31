package com.adonis.fluid.block.CentrifugalPump;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

public class CentrifugalPumpRenderer extends KineticBlockEntityRenderer<CentrifugalPumpBlockEntity> {

    public CentrifugalPumpRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(CentrifugalPumpBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        // 父类会处理所有标准的动力组件渲染，包括传动杆和ValueBox
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(CentrifugalPumpBlockEntity be, BlockState state) {
        // 注意：state 参数是轴方块的状态，不是离心泵的状态！
        // 我们需要从 BlockEntity 获取实际的离心泵状态
        BlockState pumpState = be.getBlockState();

        // 渲染半根传动杆，方向根据泵的放置方式决定
        Direction shaftDirection = CentrifugalPumpBlock.getShaftDirection(pumpState);
        AttachFace face = pumpState.getValue(CentrifugalPumpBlock.FACE);

        // 根据不同的放置模式返回正确朝向的半根传动杆（方向对调）
        if (face == AttachFace.WALL) {
            // 垂直模式，轴向下（原来是向上）
            return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, pumpState, Direction.UP);
        } else if (face == AttachFace.FLOOR) {
            // 地板模式，轴在facing方向（原来是反方向）
            return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, pumpState, shaftDirection);
        } else { // CEILING
            // 天花板模式，轴在facing方向（原来是反方向）
            return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, pumpState, shaftDirection);
        }
    }

    @Override
    protected BlockState getRenderedBlockState(CentrifugalPumpBlockEntity be) {
        // 返回带有正确旋转轴的轴方块状态，用于父类渲染传动杆
        return KineticBlockEntityRenderer.shaft(KineticBlockEntityRenderer.getRotationAxisOf(be));
    }
}
