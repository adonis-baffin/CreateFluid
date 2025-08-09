package com.adonis.fluid.block.Pipette;

import com.adonis.fluid.registry.CFPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
    }

    @Override
    protected void renderSafe(PipetteBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, pt, ms, buffer, light, overlay);

        ItemStack item = be.heldItem;
        boolean hasItem = !item.isEmpty();
        boolean usingFlywheel = VisualizationManager.supportsVisualization(be.getLevel());

        if (!usingFlywheel || hasItem) {
            ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
            BakedModel bakedModel = itemRenderer.getModel(item, be.getLevel(), (LivingEntity)null, 0);
            boolean isBlockItem = hasItem && item.getItem() instanceof BlockItem && bakedModel.isGui3d();

            VertexConsumer builder = buffer.getBuffer(be.goggles ? RenderType.cutout() : RenderType.solid());
            BlockState blockState = be.getBlockState();

            PoseStack msLocal = new PoseStack();
            PoseTransformStack msr = TransformStack.of(msLocal);

            boolean inverted = blockState.getValue(PipetteBlock.CEILING);
            boolean rave = be.phase == PipetteBlockEntity.Phase.SEARCH_INPUTS && be.getSpeed() != 0.0F;

            float baseAngle;
            float lowerArmAngle;
            float upperArmAngle;
            float headAngle;
            int color;

            if (rave) {
                float time = AnimationTickHolder.getRenderTime(be.getLevel()) + (float)(be.hashCode() % 64);
                baseAngle = time * 8.0F % 360.0F;
                lowerArmAngle = Mth.lerp((Mth.sin(time / 6.0F) + 1.0F) / 2.0F, -30.0F, 30.0F);
                upperArmAngle = Mth.lerp((Mth.sin(time / 10.0F) + 1.0F) / 4.0F, -60.0F, 60.0F);
                headAngle = -lowerArmAngle * 0.5F;
                color = Color.rainbowColor(AnimationTickHolder.getTicks() * 100).getRGB();
            } else {
                baseAngle = be.baseAngle.getValue(pt);
                lowerArmAngle = be.lowerArmAngle.getValue(pt) - 135.0F;
                upperArmAngle = be.upperArmAngle.getValue(pt) - 90.0F;
                headAngle = be.headAngle.getValue(pt);
                color = 0xFFFFFF;
            }

            msr.center();
            if (inverted) {
                msr.rotateXDegrees(180.0F);
            }

            if (usingFlywheel) {
                this.doItemTransforms(msr, baseAngle, lowerArmAngle, upperArmAngle, headAngle);
            } else {
                this.renderPipette(builder, ms, msLocal, msr, blockState, color, baseAngle,
                        lowerArmAngle, upperArmAngle, headAngle, be.goggles, inverted && be.goggles,
                        hasItem, isBlockItem, light);
            }

            if (hasItem) {
                ms.pushPose();
                float itemScale = isBlockItem ? 0.4F : 0.5F;
                msr.rotateXDegrees(90.0F);
                msLocal.translate(0.0F, isBlockItem ? -0.4F : -0.5F, 0.0F);
                msLocal.scale(itemScale, itemScale, itemScale);
                ms.last().pose().mul(msLocal.last().pose());
                itemRenderer.render(item, ItemDisplayContext.FIXED, false, ms, buffer, light, overlay, bakedModel);
                ms.popPose();
            }
        }
    }

    private void renderPipette(VertexConsumer builder, PoseStack ms, PoseStack msLocal,
                               TransformStack msr, BlockState blockState, int color,
                               float baseAngle, float lowerArmAngle, float upperArmAngle,
                               float headAngle, boolean goggles, boolean inverted,
                               boolean hasItem, boolean isBlockItem, int light) {

        SuperByteBuffer base = CachedBuffers.partial(CFPartialModels.PIPETTE_BASE, blockState).light(light);
        SuperByteBuffer lowerArm = CachedBuffers.partial(CFPartialModels.PIPETTE_LOWER_ARM, blockState).light(light);
        SuperByteBuffer upperArm = CachedBuffers.partial(CFPartialModels.PIPETTE_UPPER_ARM, blockState).light(light);
        SuperByteBuffer head = CachedBuffers.partial(goggles ? CFPartialModels.PIPETTE_HEAD_GOGGLES : CFPartialModels.PIPETTE_HEAD, blockState).light(light);
        SuperByteBuffer tip = CachedBuffers.partial(CFPartialModels.PIPETTE_TIP, blockState).light(light);
        SuperByteBuffer needle = CachedBuffers.partial(CFPartialModels.PIPETTE_NEEDLE, blockState).light(light);

        // 渲染基座
        transformBase(msr, baseAngle);
        base.transform(msLocal).renderInto(ms, builder);

        // 渲染下臂
        transformLowerArm(msr, lowerArmAngle);
        lowerArm.color(color).transform(msLocal).renderInto(ms, builder);

        // 渲染上臂
        transformUpperArm(msr, upperArmAngle);
        upperArm.color(color).transform(msLocal).renderInto(ms, builder);

        // 渲染头部
        transformHead(msr, headAngle);
        if (inverted) {
            msr.rotateZDegrees(180.0F);
        }
        head.transform(msLocal).renderInto(ms, builder);

        // 渲染吸液头
        transformTip(msr, hasItem, isBlockItem);
        tip.transform(msLocal).renderInto(ms, builder);

        // 渲染针头
        transformNeedle(msr, hasItem, isBlockItem);
        needle.transform(msLocal).renderInto(ms, builder);

        if (inverted) {
            msr.rotateZDegrees(180.0F);
        }
    }

    private void doItemTransforms(TransformStack msr, float baseAngle, float lowerArmAngle,
                                  float upperArmAngle, float headAngle) {
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

    // 添加这个方法
    public static void transformNeedle(TransformStack msr, boolean hasItem, boolean isBlockItem) {
        // 针头在吸液头的基础上再向下延伸
        msr.translate(0.0, hasItem ? (isBlockItem ? -0.2 : -0.15) : -0.1, -0.1);
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