package com.adonis.fluid.block.Pipette;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
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
            
            float baseAngle = be.baseAngle.getValue(pt);
            float lowerArmAngle = be.lowerArmAngle.getValue(pt) - 135.0F;
            float upperArmAngle = be.upperArmAngle.getValue(pt) - 90.0F;
            float headAngle = be.headAngle.getValue(pt);
            int color = 16777215;

            msr.center();
            if (inverted) {
                msr.rotateXDegrees(180.0F);
            }

            if (usingFlywheel) {
                this.doItemTransforms(msr, baseAngle, lowerArmAngle, upperArmAngle, headAngle);
            } else {
                this.renderArm(builder, ms, msLocal, msr, blockState, color, baseAngle, 
                    lowerArmAngle, upperArmAngle, headAngle, be.goggles, inverted && be.goggles, hasItem, isBlockItem, light);
            }

            if (hasItem) {
                ms.pushPose();
                float itemScale = isBlockItem ? 0.5F : 0.625F;
                msr.rotateXDegrees(90.0F);
                msLocal.translate(0.0F, isBlockItem ? -0.5625F : -0.625F, 0.0F);
                msLocal.scale(itemScale, itemScale, itemScale);
                ms.last().pose().mul(msLocal.last().pose());
                itemRenderer.render(item, ItemDisplayContext.FIXED, false, ms, buffer, light, overlay, bakedModel);
                ms.popPose();
            }
        }
    }

    private void renderArm(VertexConsumer builder, PoseStack ms, PoseStack msLocal, TransformStack msr, 
                          BlockState blockState, int color, float baseAngle, float lowerArmAngle, 
                          float upperArmAngle, float headAngle, boolean goggles, boolean inverted, 
                          boolean hasItem, boolean isBlockItem, int light) {
        SuperByteBuffer base = CachedBuffers.partial(AllPartialModels.ARM_BASE, blockState).light(light);
        SuperByteBuffer lowerBody = CachedBuffers.partial(AllPartialModels.ARM_LOWER_BODY, blockState).light(light);
        SuperByteBuffer upperBody = CachedBuffers.partial(AllPartialModels.ARM_UPPER_BODY, blockState).light(light);
        SuperByteBuffer claw = CachedBuffers.partial(goggles ? AllPartialModels.ARM_CLAW_BASE_GOGGLES : 
            AllPartialModels.ARM_CLAW_BASE, blockState).light(light);
        SuperByteBuffer upperClawGrip = CachedBuffers.partial(AllPartialModels.ARM_CLAW_GRIP_UPPER, blockState).light(light);
        SuperByteBuffer lowerClawGrip = CachedBuffers.partial(AllPartialModels.ARM_CLAW_GRIP_LOWER, blockState).light(light);
        
        ArmRenderer.transformBase(msr, baseAngle);
        base.transform(msLocal).renderInto(ms, builder);
        ArmRenderer.transformLowerArm(msr, lowerArmAngle);
        lowerBody.color(color).transform(msLocal).renderInto(ms, builder);
        ArmRenderer.transformUpperArm(msr, upperArmAngle);
        upperBody.color(color).transform(msLocal).renderInto(ms, builder);
        ArmRenderer.transformHead(msr, headAngle);
        
        if (inverted) {
            msr.rotateZDegrees(180.0F);
        }
        claw.transform(msLocal).renderInto(ms, builder);
        if (inverted) {
            msr.rotateZDegrees(180.0F);
        }

        for (int flip : Iterate.positiveAndNegative) {
            msLocal.pushPose();
            ArmRenderer.transformClawHalf(msr, hasItem, isBlockItem, flip);
            (flip > 0 ? lowerClawGrip : upperClawGrip).transform(msLocal).renderInto(ms, builder);
            msLocal.popPose();
        }
    }

    private void doItemTransforms(TransformStack msr, float baseAngle, float lowerArmAngle, float upperArmAngle, float headAngle) {
        ArmRenderer.transformBase(msr, baseAngle);
        ArmRenderer.transformLowerArm(msr, lowerArmAngle);
        ArmRenderer.transformUpperArm(msr, upperArmAngle);
        ArmRenderer.transformHead(msr, headAngle);
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