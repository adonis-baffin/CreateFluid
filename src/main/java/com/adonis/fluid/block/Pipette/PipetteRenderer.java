package com.adonis.fluid.block.Pipette;

import com.adonis.fluid.registry.CFPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class PipetteRenderer extends KineticBlockEntityRenderer<PipetteBlockEntity> {

    public PipetteRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(PipetteBlockEntity be, float pt, PoseStack ms, 
                             MultiBufferSource buffer, int light, int overlay) {
        try {
            // 1. 渲染齿轮
            BlockState state = this.getRenderedBlockState(be);
            RenderType type = this.getRenderType(be, state);
            SuperByteBuffer cogModel = this.getRotatedModel(be, state);

            if (cogModel != null) {
                renderRotatingBuffer(be, cogModel, ms, buffer.getBuffer(type), light);
            }

            // 2. 渲染机械臂部分
            VertexConsumer builder = buffer.getBuffer(RenderType.solid());
            BlockState blockState = be.getBlockState();

            PoseStack msLocal = new PoseStack();
            PoseTransformStack msr = TransformStack.of(msLocal);

            boolean inverted = blockState.getValue(PipetteBlock.CEILING);

            float baseAngle = be.baseAngle.getValue(pt);
            float lowerArmAngle = be.lowerArmAngle.getValue(pt) - 135.0F;
            float upperArmAngle = be.upperArmAngle.getValue(pt) - 90.0F;
            float headAngle = be.headAngle.getValue(pt);
            int color = 0xFFFFFF;

            msr.center();
            if (inverted) {
                msr.rotateXDegrees(180.0F);
            }

            // 渲染移液器部件
            renderPipetteSafe(builder, ms, msLocal, msr, blockState, color, baseAngle,
                    lowerArmAngle, upperArmAngle, headAngle, inverted, light, be);

        } catch (Exception e) {
            // 静默处理渲染错误
        }
    }

    private void renderPipetteSafe(VertexConsumer builder, PoseStack ms, PoseStack msLocal,
                                   TransformStack msr, BlockState blockState, int color,
                                   float baseAngle, float lowerArmAngle, float upperArmAngle,
                                   float headAngle, boolean inverted, int light,
                                   PipetteBlockEntity be) {
        try {
            // 获取模型
            SuperByteBuffer base = getModelSafe(CFPartialModels.PIPETTE_BASE,
                    AllPartialModels.ARM_BASE, blockState, light);
            SuperByteBuffer lowerBody = getModelSafe(CFPartialModels.PIPETTE_LOWER_ARM,
                    AllPartialModels.ARM_LOWER_BODY, blockState, light);
            SuperByteBuffer upperBody = getModelSafe(CFPartialModels.PIPETTE_UPPER_ARM,
                    AllPartialModels.ARM_UPPER_BODY, blockState, light);

            // 根据流体量选择头部模型
            int fluidAmount = 0;
            if (be != null && !be.heldFluid.isEmpty()) {
                fluidAmount = be.heldFluid.getAmount();
            }

            PartialModel headModel = CFPartialModels.getPipetteHeadForFluidAmount(fluidAmount);
            SuperByteBuffer head = getModelSafe(headModel, AllPartialModels.ARM_CLAW_BASE, blockState, light);

            if (base == null || lowerBody == null || upperBody == null || head == null) {
                return;
            }

            // 渲染底座
            ArmRenderer.transformBase(msr, baseAngle);
            base.transform(msLocal).renderInto(ms, builder);

            // 渲染下臂
            ArmRenderer.transformLowerArm(msr, lowerArmAngle);
            lowerBody.color(color).transform(msLocal).renderInto(ms, builder);

            // 渲染上臂
            ArmRenderer.transformUpperArm(msr, upperArmAngle);
            upperBody.color(color).transform(msLocal).renderInto(ms, builder);

            // 渲染头部
            ArmRenderer.transformHead(msr, headAngle);
            if (inverted) {
                msr.rotateZDegrees(180.0F);
            }
            head.transform(msLocal).renderInto(ms, builder);

        } catch (Exception e) {
            // 静默处理
        }
    }

    private SuperByteBuffer getModelSafe(PartialModel model, PartialModel fallback,
                                         BlockState blockState, int light) {
        if (model == null) {
            model = fallback;
        }

        SuperByteBuffer buffer = null;

        try {
            buffer = CachedBuffers.partial(model, blockState);
        } catch (Exception e) {
            // 静默处理
        }

        if (buffer == null && fallback != null && fallback != model) {
            try {
                buffer = CachedBuffers.partial(fallback, blockState);
            } catch (Exception e) {
                // 静默处理
            }
        }

        if (buffer != null) {
            buffer.light(light);
        }

        return buffer;
    }

    @Override
    protected SuperByteBuffer getRotatedModel(PipetteBlockEntity be, BlockState state) {
        try {
            return CachedBuffers.partial(CFPartialModels.PIPETTE_COG, state);
        } catch (Exception e) {
            try {
                return CachedBuffers.partial(AllPartialModels.ARM_COG, state);
            } catch (Exception e2) {
                return null;
            }
        }
    }
}