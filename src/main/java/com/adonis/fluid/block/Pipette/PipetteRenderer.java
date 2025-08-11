package com.adonis.fluid.block.Pipette;

import com.adonis.fluid.registry.CFPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

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

        // 3. 渲染流体
        if (hasFluid) {
            renderFluid(be, fluid, ms, buffer, light, pt, baseAngle, lowerArmAngle, upperArmAngle, headAngle, inverted);
        }
    }

    private void renderFluid(PipetteBlockEntity be, FluidStack fluidStack, PoseStack ms, MultiBufferSource buffer, int light, float pt,
                             float baseAngle, float lowerArmAngle, float upperArmAngle, float headAngle, boolean inverted) {
        if (fluidStack.isEmpty()) return;

        IClientFluidTypeExtensions clientFluid = IClientFluidTypeExtensions.of(fluidStack.getFluid());
        TextureAtlasSprite stillTexture = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(clientFluid.getStillTexture(fluidStack));

        int color = clientFluid.getTintColor(fluidStack);
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float a = ((color >> 24) & 0xFF) / 255.0F;
        if (a == 0) a = 1.0F;

        ms.pushPose();

        // 复制机械臂的变换
        TransformStack fluidTransform = TransformStack.of(ms);
        fluidTransform.center();
        if (inverted) {
            fluidTransform.rotateXDegrees(180.0F);
        }

        // 应用机械臂的旋转
        transformBase(fluidTransform, baseAngle);
        transformLowerArm(fluidTransform, lowerArmAngle);
        transformUpperArm(fluidTransform, upperArmAngle);
        transformHead(fluidTransform, headAngle);

        // 移动到移液器头部内部的正确位置
        // 根据你的需求调整这个位置
        fluidTransform.translate(0.0, -0.125, -0.0625); // 调整到合适的位置

        // 根据流体量调整
        float fillAmount = Math.min(1.0F, (float)fluidStack.getAmount() / 1000.0F);

        VertexConsumer fluidBuilder = buffer.getBuffer(RenderType.translucent());

        // 渲染4x4x4像素的流体立方体
        renderFluidCube(fluidBuilder, ms, stillTexture, r, g, b, a, fillAmount, light);

        ms.popPose();
    }

    private void renderFluidCube(VertexConsumer builder, PoseStack ms, TextureAtlasSprite texture,
                                 float r, float g, float b, float a, float fillAmount, int light) {
        PoseStack.Pose pose = ms.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();

        // 4x4x4 像素 = 1/4 方块大小
        float size = 0.5F; // 4/16 = 1/4 方块宽度
        float height = size * fillAmount; // 高度根据流体量调整
        float halfSize = size / 2.0F;

        float minU = texture.getU0();
        float maxU = texture.getU1();
        float minV = texture.getV0();
        float maxV = texture.getV1();

        // 前面
        builder.vertex(matrix, -halfSize, 0, -halfSize).color(r, g, b, a).uv(minU, maxV).uv2(light).normal(normal, 0, 0, -1).endVertex();
        builder.vertex(matrix, halfSize, 0, -halfSize).color(r, g, b, a).uv(maxU, maxV).uv2(light).normal(normal, 0, 0, -1).endVertex();
        builder.vertex(matrix, halfSize, height, -halfSize).color(r, g, b, a).uv(maxU, minV).uv2(light).normal(normal, 0, 0, -1).endVertex();
        builder.vertex(matrix, -halfSize, height, -halfSize).color(r, g, b, a).uv(minU, minV).uv2(light).normal(normal, 0, 0, -1).endVertex();

        // 后面
        builder.vertex(matrix, halfSize, 0, halfSize).color(r, g, b, a).uv(minU, maxV).uv2(light).normal(normal, 0, 0, 1).endVertex();
        builder.vertex(matrix, -halfSize, 0, halfSize).color(r, g, b, a).uv(maxU, maxV).uv2(light).normal(normal, 0, 0, 1).endVertex();
        builder.vertex(matrix, -halfSize, height, halfSize).color(r, g, b, a).uv(maxU, minV).uv2(light).normal(normal, 0, 0, 1).endVertex();
        builder.vertex(matrix, halfSize, height, halfSize).color(r, g, b, a).uv(minU, minV).uv2(light).normal(normal, 0, 0, 1).endVertex();

        // 左面
        builder.vertex(matrix, -halfSize, 0, halfSize).color(r, g, b, a).uv(minU, maxV).uv2(light).normal(normal, -1, 0, 0).endVertex();
        builder.vertex(matrix, -halfSize, 0, -halfSize).color(r, g, b, a).uv(maxU, maxV).uv2(light).normal(normal, -1, 0, 0).endVertex();
        builder.vertex(matrix, -halfSize, height, -halfSize).color(r, g, b, a).uv(maxU, minV).uv2(light).normal(normal, -1, 0, 0).endVertex();
        builder.vertex(matrix, -halfSize, height, halfSize).color(r, g, b, a).uv(minU, minV).uv2(light).normal(normal, -1, 0, 0).endVertex();

        // 右面
        builder.vertex(matrix, halfSize, 0, -halfSize).color(r, g, b, a).uv(minU, maxV).uv2(light).normal(normal, 1, 0, 0).endVertex();
        builder.vertex(matrix, halfSize, 0, halfSize).color(r, g, b, a).uv(maxU, maxV).uv2(light).normal(normal, 1, 0, 0).endVertex();
        builder.vertex(matrix, halfSize, height, halfSize).color(r, g, b, a).uv(maxU, minV).uv2(light).normal(normal, 1, 0, 0).endVertex();
        builder.vertex(matrix, halfSize, height, -halfSize).color(r, g, b, a).uv(minU, minV).uv2(light).normal(normal, 1, 0, 0).endVertex();

        // 顶面（只在有流体时渲染）
        if (fillAmount > 0) {
            builder.vertex(matrix, -halfSize, height, -halfSize).color(r, g, b, a).uv(minU, minV).uv2(light).normal(normal, 0, 1, 0).endVertex();
            builder.vertex(matrix, halfSize, height, -halfSize).color(r, g, b, a).uv(maxU, minV).uv2(light).normal(normal, 0, 1, 0).endVertex();
            builder.vertex(matrix, halfSize, height, halfSize).color(r, g, b, a).uv(maxU, maxV).uv2(light).normal(normal, 0, 1, 0).endVertex();
            builder.vertex(matrix, -halfSize, height, halfSize).color(r, g, b, a).uv(minU, maxV).uv2(light).normal(normal, 0, 1, 0).endVertex();
        }

        // 底面
        builder.vertex(matrix, -halfSize, 0, halfSize).color(r, g, b, a).uv(minU, maxV).uv2(light).normal(normal, 0, -1, 0).endVertex();
        builder.vertex(matrix, halfSize, 0, halfSize).color(r, g, b, a).uv(maxU, maxV).uv2(light).normal(normal, 0, -1, 0).endVertex();
        builder.vertex(matrix, halfSize, 0, -halfSize).color(r, g, b, a).uv(maxU, minV).uv2(light).normal(normal, 0, -1, 0).endVertex();
        builder.vertex(matrix, -halfSize, 0, -halfSize).color(r, g, b, a).uv(minU, minV).uv2(light).normal(normal, 0, -1, 0).endVertex();
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
        SuperByteBuffer head = CachedBuffers.partial(CFPartialModels.PIPETTE_HEAD, blockState).light(light); // 包含整个头部

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

    // 静态辅助方法
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