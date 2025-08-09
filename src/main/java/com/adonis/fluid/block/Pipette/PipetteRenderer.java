package com.adonis.fluid.block.Pipette;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.registry.CFPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class PipetteRenderer extends KineticBlockEntityRenderer<PipetteBlockEntity> {

    public PipetteRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(PipetteBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        CreateFluid.LOGGER.debug("=== PipetteRenderer.renderSafe called ===");

        ItemStack item = be.heldItem;
        boolean hasItem = !item.isEmpty();
        // 强制使用传统渲染进行测试
        boolean usingFlywheel = false; // 强制设为false

        CreateFluid.LOGGER.debug("Has item: {}, Forced traditional rendering", hasItem);

        // 总是进行传统渲染
        {
            // 1. 手动渲染齿轮
            BlockState state = this.getRenderedBlockState(be);
            RenderType type = this.getRenderType(be, state);
            SuperByteBuffer cogModel = this.getRotatedModel(be, state);
            CreateFluid.LOGGER.debug("Rendering gear manually...");
            renderRotatingBuffer(be, cogModel, ms, buffer.getBuffer(type), light);
            CreateFluid.LOGGER.debug("✓ Gear rendered");

            // 2. 渲染机械臂部分
            ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
            BakedModel bakedModel = itemRenderer.getModel(item, be.getLevel(), (LivingEntity)null, 0);
            boolean isBlockItem = hasItem && item.getItem() instanceof BlockItem && bakedModel.isGui3d();

            VertexConsumer builder = buffer.getBuffer(be.goggles ? RenderType.cutout() : RenderType.solid());
            BlockState blockState = be.getBlockState();

            PoseStack msLocal = new PoseStack();
            PoseTransformStack msr = TransformStack.of(msLocal);

            boolean inverted = blockState.getValue(PipetteBlock.CEILING);

            // 完全禁用跳舞功能
            boolean rave = false;

            float baseAngle;
            float lowerArmAngle;
            float upperArmAngle;
            float headAngle;
            int color = 0xFFFFFF; // 固定为白色，不使用彩虹色

            // 移除跳舞动画，只使用正常动画
            baseAngle = be.baseAngle.getValue(pt);
            lowerArmAngle = be.lowerArmAngle.getValue(pt) - 135.0F;
            upperArmAngle = be.upperArmAngle.getValue(pt) - 90.0F;
            headAngle = be.headAngle.getValue(pt);

            CreateFluid.LOGGER.debug("Normal animation mode");
            CreateFluid.LOGGER.debug("Angles: Base={}, Lower={}, Upper={}, Head={}", baseAngle, lowerArmAngle, upperArmAngle, headAngle);

            msr.center();
            if (inverted) {
                msr.rotateXDegrees(180.0F);
            }

            // 渲染机械臂
            CreateFluid.LOGGER.debug("Rendering arm parts...");
            this.renderPipette(builder, ms, msLocal, msr, blockState, color, baseAngle,
                    lowerArmAngle, upperArmAngle, headAngle, be.goggles, inverted && be.goggles,
                    hasItem, isBlockItem, light);
            CreateFluid.LOGGER.debug("✓ Arm parts rendered");

            // 3. 渲染物品
            if (hasItem) {
                CreateFluid.LOGGER.debug("Rendering held item...");
                ms.pushPose();
                float itemScale = isBlockItem ? 0.5F : 0.625F;
                msr.rotateXDegrees(90.0F);
                msLocal.translate(0.0F, isBlockItem ? -0.5625F : -0.625F, 0.0F);
                msLocal.scale(itemScale, itemScale, itemScale);
                ms.last().pose().mul(msLocal.last().pose());
                itemRenderer.render(item, ItemDisplayContext.FIXED, false, ms, buffer, light, overlay, bakedModel);
                ms.popPose();
                CreateFluid.LOGGER.debug("✓ Item rendered");
            }
        }

        CreateFluid.LOGGER.debug("=== PipetteRenderer.renderSafe completed ===");
    }

    // 添加这些辅助方法（从KineticBlockEntityRenderer复制）
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
                               boolean hasItem, boolean isBlockItem, int light) {

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

        // 渲染爪子
        int[] var22 = Iterate.positiveAndNegative;
        for(int flip : var22) {
            msLocal.pushPose();
            transformClawHalf(msr, hasItem, isBlockItem, flip);
            (flip > 0 ? lowerClawGrip : upperClawGrip).transform(msLocal).renderInto(ms, builder);
            msLocal.popPose();
        }
    }

    private void doItemTransforms(TransformStack msr, float baseAngle, float lowerArmAngle,
                                  float upperArmAngle, float headAngle) {
        transformBase(msr, baseAngle);
        transformLowerArm(msr, lowerArmAngle);
        transformUpperArm(msr, upperArmAngle);
        transformHead(msr, headAngle);
    }

    // Create的变换方法
    public static void transformClawHalf(TransformStack msr, boolean hasItem, boolean isBlockItem, int flip) {
        msr.translate(0.0, (double)((float)(-flip) * (hasItem ? (isBlockItem ? 0.1875F : 0.078125F) : 0.0625F)), -0.375);
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