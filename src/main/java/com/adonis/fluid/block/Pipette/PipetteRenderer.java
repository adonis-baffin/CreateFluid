package com.adonis.fluid.block.Pipette;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.registry.CFPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
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
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class PipetteRenderer extends KineticBlockEntityRenderer<PipetteBlockEntity> {

    public PipetteRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
        CreateFluid.LOGGER.info("PipetteRenderer constructor called");
    }

    @Override
    protected void renderSafe(PipetteBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        CreateFluid.LOGGER.info("=== PipetteRenderer.renderSafe called ===");
        CreateFluid.LOGGER.info("BlockEntity position: {}", be.getBlockPos());
        CreateFluid.LOGGER.info("BlockEntity class: {}", be.getClass().getSimpleName());
        CreateFluid.LOGGER.info("Level: {}", be.getLevel());
        CreateFluid.LOGGER.info("Is client side: {}", be.getLevel() != null ? be.getLevel().isClientSide : "null level");

        // 调用父类方法
        super.renderSafe(be, pt, ms, buffer, light, overlay);
        CreateFluid.LOGGER.info("✓ Super.renderSafe completed");

        ItemStack item = be.heldItem;
        boolean hasItem = !item.isEmpty();
        boolean usingFlywheel = VisualizationManager.supportsVisualization(be.getLevel());

        CreateFluid.LOGGER.info("Has item: {}, Using Flywheel: {}", hasItem, usingFlywheel);
        CreateFluid.LOGGER.info("Item: {}", hasItem ? item.toString() : "EMPTY");

        // 强制进入渲染路径进行测试
        CreateFluid.LOGGER.info("Forcing traditional rendering for debugging...");

        try {
            ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
            BakedModel bakedModel = itemRenderer.getModel(item, be.getLevel(), (LivingEntity)null, 0);
            boolean isBlockItem = hasItem && item.getItem() instanceof BlockItem && bakedModel.isGui3d();

            VertexConsumer builder = buffer.getBuffer(be.goggles ? RenderType.cutout() : RenderType.solid());
            BlockState blockState = be.getBlockState();

            PoseStack msLocal = new PoseStack();
            PoseTransformStack msr = TransformStack.of(msLocal);

            boolean inverted = blockState.getValue(PipetteBlock.CEILING);
            boolean rave = be.phase == PipetteBlockEntity.Phase.SEARCH_INPUTS && be.getSpeed() != 0.0F;

            CreateFluid.LOGGER.info("Inverted: {}, Rave mode: {}, Goggles: {}", inverted, rave, be.goggles);
            CreateFluid.LOGGER.info("Phase: {}, Speed: {}", be.phase, be.getSpeed());

            float baseAngle = be.baseAngle.getValue(pt);
            float lowerArmAngle = be.lowerArmAngle.getValue(pt) - 135.0F;
            float upperArmAngle = be.upperArmAngle.getValue(pt) - 90.0F;
            float headAngle = be.headAngle.getValue(pt);
            int color = 0xFFFFFF;

            CreateFluid.LOGGER.info("Animation angles - Base: {}, Lower: {}, Upper: {}, Head: {}",
                    baseAngle, lowerArmAngle, upperArmAngle, headAngle);

            msr.center();
            if (inverted) {
                msr.rotateXDegrees(180.0F);
            }

            // 强制调用传统渲染
            CreateFluid.LOGGER.info("Calling renderPipette...");
            this.renderPipette(builder, ms, msLocal, msr, blockState, color, baseAngle,
                    lowerArmAngle, upperArmAngle, headAngle, be.goggles, inverted && be.goggles,
                    hasItem, isBlockItem, light);
            CreateFluid.LOGGER.info("✓ renderPipette completed");

        } catch (Exception e) {
            CreateFluid.LOGGER.error("=== renderSafe FAILED ===", e);
        }

        CreateFluid.LOGGER.info("=== PipetteRenderer.renderSafe completed ===");
    }

    private void renderPipette(VertexConsumer builder, PoseStack ms, PoseStack msLocal,
                               TransformStack msr, BlockState blockState, int color,
                               float baseAngle, float lowerArmAngle, float upperArmAngle,
                               float headAngle, boolean goggles, boolean inverted,
                               boolean hasItem, boolean isBlockItem, int light) {

        CreateFluid.LOGGER.info("=== renderPipette method called ===");
        CreateFluid.LOGGER.info("Angles - Base: {}, Lower: {}, Upper: {}, Head: {}",
                baseAngle, lowerArmAngle, upperArmAngle, headAngle);

        try {
            // 1. 渲染基座
            CreateFluid.LOGGER.info("Rendering ARM_BASE...");
            SuperByteBuffer base = CachedBuffers.partial(AllPartialModels.ARM_BASE, blockState).light(light);
            transformBase(msr, baseAngle);
            base.transform(msLocal).renderInto(ms, builder);
            CreateFluid.LOGGER.info("✓ ARM_BASE rendered at base angle: {}", baseAngle);

            // 2. 渲染下臂
            CreateFluid.LOGGER.info("Rendering ARM_LOWER_BODY...");
            SuperByteBuffer lowerArm = CachedBuffers.partial(AllPartialModels.ARM_LOWER_BODY, blockState).light(light);
            transformLowerArm(msr, lowerArmAngle);
            lowerArm.color(color).transform(msLocal).renderInto(ms, builder);
            CreateFluid.LOGGER.info("✓ ARM_LOWER_BODY rendered at angle: {}", lowerArmAngle);

            // 3. 渲染上臂
            CreateFluid.LOGGER.info("Rendering ARM_UPPER_BODY...");
            SuperByteBuffer upperArm = CachedBuffers.partial(AllPartialModels.ARM_UPPER_BODY, blockState).light(light);
            transformUpperArm(msr, upperArmAngle);
            upperArm.color(color).transform(msLocal).renderInto(ms, builder);
            CreateFluid.LOGGER.info("✓ ARM_UPPER_BODY rendered at angle: {}", upperArmAngle);

            // 4. 渲染爪子头部
            CreateFluid.LOGGER.info("Rendering ARM_CLAW_BASE...");
            SuperByteBuffer head = CachedBuffers.partial(goggles ? AllPartialModels.ARM_CLAW_BASE_GOGGLES : AllPartialModels.ARM_CLAW_BASE, blockState).light(light);
            transformHead(msr, headAngle);
            if (inverted) {
                msr.rotateZDegrees(180.0F);
                CreateFluid.LOGGER.info("Applied head inversion");
            }
            head.transform(msLocal).renderInto(ms, builder);
            CreateFluid.LOGGER.info("✓ ARM_CLAW_BASE rendered at angle: {} (goggles: {})", headAngle, goggles);

            // 5. 渲染爪子部分
            CreateFluid.LOGGER.info("Rendering claw grips...");
            SuperByteBuffer upperGrip = CachedBuffers.partial(AllPartialModels.ARM_CLAW_GRIP_UPPER, blockState).light(light);
            SuperByteBuffer lowerGrip = CachedBuffers.partial(AllPartialModels.ARM_CLAW_GRIP_LOWER, blockState).light(light);

            // 上爪
            msLocal.pushPose();
            transformClawHalf(msr, hasItem, isBlockItem, 1); // 上爪
            upperGrip.transform(msLocal).renderInto(ms, builder);
            msLocal.popPose();
            CreateFluid.LOGGER.info("✓ Upper claw grip rendered");

            // 下爪
            msLocal.pushPose();
            transformClawHalf(msr, hasItem, isBlockItem, -1); // 下爪
            lowerGrip.transform(msLocal).renderInto(ms, builder);
            msLocal.popPose();
            CreateFluid.LOGGER.info("✓ Lower claw grip rendered");

            if (inverted) {
                msr.rotateZDegrees(180.0F);
            }

            CreateFluid.LOGGER.info("=== renderPipette completed successfully ===");
        } catch (Exception e) {
            CreateFluid.LOGGER.error("=== renderPipette FAILED ===", e);
            CreateFluid.LOGGER.error("Error at angles - Base: {}, Lower: {}, Upper: {}, Head: {}",
                    baseAngle, lowerArmAngle, upperArmAngle, headAngle);
            CreateFluid.LOGGER.error("Stack trace: ", e);
        }
    }

    // 添加爪子变换方法
    public static void transformClawHalf(TransformStack msr, boolean hasItem, boolean isBlockItem, int flip) {
        msr.translate(0.0, (double)((float)(-flip) * (hasItem ? (isBlockItem ? 0.1875F : 0.078125F) : 0.0625F)), -0.375);
    }

    private void doItemTransforms(TransformStack msr, float baseAngle, float lowerArmAngle,
                                  float upperArmAngle, float headAngle) {
        CreateFluid.LOGGER.debug("doItemTransforms called with angles: Base={}, Lower={}, Upper={}, Head={}",
                baseAngle, lowerArmAngle, upperArmAngle, headAngle);
        transformBase(msr, baseAngle);
        transformLowerArm(msr, lowerArmAngle);
        transformUpperArm(msr, upperArmAngle);
        transformHead(msr, headAngle);
    }

    // 变换方法
    public static void transformBase(TransformStack msr, float baseAngle) {
        msr.translate(0.0, 0.25, 0.0);
        msr.rotateYDegrees(baseAngle);
    }

    public static void transformLowerArm(TransformStack msr, float lowerArmAngle) {
        msr.translate(0.0, 0.125, 0.0);
        msr.rotateXDegrees(lowerArmAngle + 135.0F);
    }

    public static void transformUpperArm(TransformStack msr, float upperArmAngle) {
        msr.translate(0.0, 0.0, -0.75);
        msr.rotateXDegrees(upperArmAngle - 90.0F);
    }

    public static void transformHead(TransformStack msr, float headAngle) {
        msr.translate(0.0, 0.0, -0.8);
        msr.rotateXDegrees(headAngle - 45.0F);
    }

    public static void transformTip(TransformStack msr, boolean hasItem, boolean isBlockItem) {
        msr.translate(0.0, hasItem ? (isBlockItem ? -0.15 : -0.1) : -0.05, -0.3);
    }

    public static void transformNeedle(TransformStack msr, boolean hasItem, boolean isBlockItem) {
        msr.translate(0.0, hasItem ? (isBlockItem ? -0.2 : -0.15) : -0.1, -0.1);
    }

    @Override
    public boolean shouldRenderOffScreen(PipetteBlockEntity be) {
        return true;
    }

    @Override
    protected SuperByteBuffer getRotatedModel(PipetteBlockEntity be, BlockState state) {
        CreateFluid.LOGGER.debug("getRotatedModel called for COG");
        return CachedBuffers.partial(CFPartialModels.PIPETTE_COG, state);
    }
}