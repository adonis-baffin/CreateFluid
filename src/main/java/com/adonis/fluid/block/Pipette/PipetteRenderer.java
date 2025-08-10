package com.adonis.fluid.block.Pipette;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;

public class PipetteRenderer extends KineticBlockEntityRenderer<PipetteBlockEntity> {

    public PipetteRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(PipetteBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        FluidStack fluid = be.heldFluid;
        boolean hasFluid = !fluid.isEmpty();

        // 传统渲染路径
        {
            // 1. 渲染齿轮
            BlockState state = this.getRenderedBlockState(be);
            RenderType type = this.getRenderType(be, state);
            SuperByteBuffer cogModel = this.getRotatedModel(be, state);
            renderRotatingBuffer(be, cogModel, ms, buffer.getBuffer(type), light);

            // 2. 渲染机械臂部分
            VertexConsumer builder = buffer.getBuffer(be.goggles ? RenderType.cutout() : RenderType.solid());
            BlockState blockState = be.getBlockState();

            PoseStack msLocal = new PoseStack();
            PoseTransformStack msr = TransformStack.of(msLocal);

            boolean inverted = blockState.getValue(PipetteBlock.CEILING);

            float baseAngle;
            float lowerArmAngle;
            float upperArmAngle;
            float headAngle;
            int color = 0xFFFFFF; // 固定为白色

            // 使用正常动画
            baseAngle = be.baseAngle.getValue(pt);
            lowerArmAngle = be.lowerArmAngle.getValue(pt) - 135.0F;
            upperArmAngle = be.upperArmAngle.getValue(pt) - 90.0F;
            headAngle = be.headAngle.getValue(pt);

            msr.center();
            if (inverted) {
                msr.rotateXDegrees(180.0F);
            }

            // 渲染机械臂
            this.renderPipette(builder, ms, msLocal, msr, blockState, color, baseAngle,
                    lowerArmAngle, upperArmAngle, headAngle, be.goggles, inverted && be.goggles,
                    hasFluid, light);
        }
    }

    // 齿轮渲染辅助方法
    public static void renderRotatingBuffer(PipetteBlockEntity be, SuperByteBuffer superBuffer, PoseStack ms, VertexConsumer buffer, int light) {
        standardKineticRotationTransform(superBuffer, be, light).renderInto(ms, buffer);
    }

    public static SuperByteBuffer standardKineticRotationTransform(SuperByteBuffer buffer, PipetteBlockEntity be, int light) {
        BlockPos pos = be.getBlockPos();
        Direction.Axis axis = ((IRotate)be.getBlockState().getBlock()).getRotationAxis(be.getBlockState());
        return kineticRotationTransform(buffer, be, axis, getAngleForBe(be, pos, axis), light);
    }

    public static float getAngleForBe(PipetteBlockEntity be, BlockPos pos, Direction.Axis axis) {
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float offset = getRotationOffsetForPosition(be, pos, axis);
        float angle = (time * be.getSpeed() * 3.0F / 10.0F + offset) % 360.0F / 180.0F * 3.1415927F;
        return angle;
    }

    public static SuperByteBuffer kineticRotationTransform(SuperByteBuffer buffer, PipetteBlockEntity be, Direction.Axis axis, float angle, int light) {
        buffer.light(light);
        buffer.rotateCentered(angle, Direction.get(Direction.AxisDirection.POSITIVE, axis));
        buffer.color(Color.WHITE);
        return buffer;
    }

    public static float getRotationOffsetForPosition(PipetteBlockEntity be, BlockPos pos, Direction.Axis axis) {
        return (float)be.getRotationAngleOffset(axis);
    }

    private void renderPipette(VertexConsumer builder, PoseStack ms, PoseStack msLocal,
                               TransformStack msr, BlockState blockState, int color,
                               float baseAngle, float lowerArmAngle, float upperArmAngle,
                               float headAngle, boolean goggles, boolean inverted,
                               boolean hasFluid, int light) {

        SuperByteBuffer base = CachedBuffers.partial(AllPartialModels.ARM_BASE, blockState).light(light);
        SuperByteBuffer lowerBody = CachedBuffers.partial(AllPartialModels.ARM_LOWER_BODY, blockState).light(light);
        SuperByteBuffer upperBody = CachedBuffers.partial(AllPartialModels.ARM_UPPER_BODY, blockState).light(light);
        SuperByteBuffer claw = CachedBuffers.partial(goggles ? AllPartialModels.ARM_CLAW_BASE_GOGGLES : AllPartialModels.ARM_CLAW_BASE, blockState).light(light);
        SuperByteBuffer upperClawGrip = CachedBuffers.partial(AllPartialModels.ARM_CLAW_GRIP_UPPER, blockState).light(light);
        SuperByteBuffer lowerClawGrip = CachedBuffers.partial(AllPartialModels.ARM_CLAW_GRIP_LOWER, blockState).light(light);

        transformBase(msr, baseAngle);
        base.transform(msLocal).renderInto(ms, builder);

        transformLowerArm(msr, lowerArmAngle);
        lowerBody.color(color).transform(msLocal).renderInto(ms, builder);

        transformUpperArm(msr, upperArmAngle);
        upperBody.color(color).transform(msLocal).renderInto(ms, builder);

        transformHead(msr, headAngle);
        if (inverted) {
            msr.rotateZDegrees(180.0F);
        }
        claw.transform(msLocal).renderInto(ms, builder);

        if (inverted) {
            msr.rotateZDegrees(180.0F);
        }

        // 渲染针头部分 - 保持固定状态，不根据是否有流体改变
        int[] flips = Iterate.positiveAndNegative;
        for(int flip : flips) {
            msLocal.pushPose();
            transformClawHalf(msr, hasFluid, flip);
            (flip > 0 ? lowerClawGrip : upperClawGrip).transform(msLocal).renderInto(ms, builder);
            msLocal.popPose();
        }

        // TODO: 未来在这里添加流体渲染逻辑
    }

    // Create的变换方法
    public static void transformClawHalf(TransformStack msr, boolean hasFluid, int flip) {
        // 对于移液器，针头保持固定位置，不需要根据是否有流体调整
        msr.translate(0.0, (double)((float)(-flip) * 0.0625F), -0.375);
    }

    public static void transformHead(TransformStack msr, float headAngle) {
        msr.translate(0.0, 0.0, -0.9375);
        msr.rotateXDegrees(headAngle - 45.0F);
    }

    public static void transformUpperArm(TransformStack msr, float upperArmAngle) {
        msr.translate(0.0, 0.0, -0.875);
        msr.rotateXDegrees(upperArmAngle - 90.0F);
    }

    public static void transformLowerArm(TransformStack msr, float lowerArmAngle) {
        msr.translate(0.0, 0.125, 0.0);
        msr.rotateXDegrees(lowerArmAngle + 135.0F);
    }

    public static void transformBase(TransformStack msr, float baseAngle) {
        msr.translate(0.0, 0.25, 0.0);
        msr.rotateYDegrees(baseAngle);
    }

    @Override
    public boolean shouldRenderOffScreen(PipetteBlockEntity be) {
        return true;
    }

    @Override
    protected SuperByteBuffer getRotatedModel(PipetteBlockEntity be, BlockState state) {
        return CachedBuffers.partial(AllPartialModels.ARM_COG, state);
    }
}