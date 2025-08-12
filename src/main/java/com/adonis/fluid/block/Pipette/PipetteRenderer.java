package com.adonis.fluid.block.Pipette;

import com.adonis.fluid.registry.CFPartialModels;
import com.adonis.fluid.render.PipetteFluidVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.platform.ForgeCatnipServices;
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

        // 1. 渲染齿轮
        BlockState state = this.getRenderedBlockState(be);
        RenderType type = this.getRenderType(be, state);
        SuperByteBuffer cogModel = this.getRotatedModel(be, state);
        renderRotatingBuffer(be, cogModel, ms, buffer.getBuffer(type), light);

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
        this.renderPipette(builder, ms, msLocal, msr, blockState, color, baseAngle,
                lowerArmAngle, upperArmAngle, headAngle, inverted, hasFluid, light);

        // 3. 渲染流体（如果有的话）
        if (hasFluid) {
            renderPipetteFluid(be, ms, buffer, light, pt, msr);
        }
    }

    private void renderPipetteFluid(PipetteBlockEntity be, PoseStack ms, MultiBufferSource buffer,
                                    int light, float pt, PoseTransformStack msr) {
        FluidStack fluid = be.heldFluid;
        if (fluid.isEmpty()) return;

        ms.pushPose();

        // 应用移液器头部的变换
        boolean inverted = be.getBlockState().getValue(PipetteBlock.CEILING);
        float baseAngle = be.baseAngle.getValue(pt);
        float lowerArmAngle = be.lowerArmAngle.getValue(pt) - 135.0F;
        float upperArmAngle = be.upperArmAngle.getValue(pt) - 90.0F;
        float headAngle = be.headAngle.getValue(pt);

        PoseTransformStack fluidMsr = TransformStack.of(ms);
        fluidMsr.center();
        if (inverted) {
            fluidMsr.rotateXDegrees(180.0F);
        }

        // 应用所有变换以定位到头部
        transformBase(fluidMsr, baseAngle);
        transformLowerArm(fluidMsr, lowerArmAngle);
        transformUpperArm(fluidMsr, upperArmAngle);
        transformHead(fluidMsr, headAngle);
        if (inverted) {
            fluidMsr.rotateZDegrees(180.0F);
        }

        // 在针头内部渲染流体
        renderFluidInNeedle(fluid, be.getFluidCapacity(), ms, buffer, light, be.isInjectMode());

        ms.popPose();
    }

    private void renderFluidInNeedle(FluidStack fluid, int capacity, PoseStack ms,
                                     MultiBufferSource buffer, int light, boolean isInjectMode) {
        float fillFactor = (float) fluid.getAmount() / capacity;

        // 针头内部流体渲染
        float needleRadius = 1.5f / 16f;
        float needleLength = 4f / 16f;
        float fluidLength = fillFactor * needleLength;

        // 根据模式调整位置
        float zOffset = isInjectMode ?
                -(needleLength - fluidLength) / 2 :
                (needleLength - fluidLength) / 2;

        ms.translate(0, 0, zOffset);

        // 使用Forge的流体渲染器
        ForgeCatnipServices.FLUID_RENDERER.renderFluidBox(
                fluid,
                -needleRadius, -needleRadius, -fluidLength / 2,
                needleRadius, needleRadius, fluidLength / 2,
                buffer, ms, light, true, false
        );
    }

    private void renderPipette(VertexConsumer builder, PoseStack ms, PoseStack msLocal,
                               TransformStack msr, BlockState blockState, int color,
                               float baseAngle, float lowerArmAngle, float upperArmAngle,
                               float headAngle, boolean inverted, boolean hasFluid, int light) {

        // 使用Create的底座模型
        SuperByteBuffer base = CachedBuffers.partial(CFPartialModels.PIPETTE_BASE, blockState).light(light);

        // 使用自定义的移液器模型
        SuperByteBuffer lowerBody = CachedBuffers.partial(CFPartialModels.PIPETTE_LOWER_ARM, blockState).light(light);
        SuperByteBuffer upperBody = CachedBuffers.partial(CFPartialModels.PIPETTE_UPPER_ARM, blockState).light(light);
        SuperByteBuffer head = CachedBuffers.partial(CFPartialModels.PIPETTE_HEAD, blockState).light(light);

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
        head.transform(msLocal).renderInto(ms, builder);
    }

    // 其他方法保持不变...
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

    // 变换方法
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

    public static void transformClawHalf(TransformStack msr, boolean hasFluid, int flip) {
        // 对于移液器，针头保持固定位置，不需要根据是否有流体调整
        msr.translate(0.0, (double)((float)(-flip) * 0.0625F), -0.375);
    }

    @Override
    public boolean shouldRenderOffScreen(PipetteBlockEntity be) {
        return true;
    }

    @Override
    protected SuperByteBuffer getRotatedModel(PipetteBlockEntity be, BlockState state) {
        return CachedBuffers.partial(CFPartialModels.PIPETTE_COG, state);
    }
}