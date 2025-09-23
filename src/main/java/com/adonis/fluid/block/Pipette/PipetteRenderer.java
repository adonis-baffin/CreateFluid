package com.adonis.fluid.block.Pipette;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.registry.CFPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import static com.simibubi.create.content.kinetics.mechanicalArm.ArmRenderer.*;

public class PipetteRenderer extends KineticBlockEntityRenderer<PipetteBlockEntity> {

    public PipetteRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(PipetteBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        try {
            FluidStack fluid = be.heldFluid;
            boolean hasFluid = !fluid.isEmpty();

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
                    lowerArmAngle, upperArmAngle, headAngle, inverted, hasFluid, light, be);

            // 3. 渲染流体（如果有的话）
            if (hasFluid) {
                renderPipetteFluidSafe(be, ms, buffer, light, pt, msr);
            }

        } catch (Exception e) {
            // 静默处理
        }
    }

    private void renderPipetteFluidSafe(PipetteBlockEntity be, PoseStack ms, MultiBufferSource buffer,
                                        int light, float pt, PoseTransformStack msr) {
        try {
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
        } catch (Exception e) {
            // 静默处理
        }
    }

    private void renderPipetteSafe(VertexConsumer builder, PoseStack ms, PoseStack msLocal,
                                   TransformStack msr, BlockState blockState, int color,
                                   float baseAngle, float lowerArmAngle, float upperArmAngle,
                                   float headAngle, boolean inverted, boolean hasFluid, int light,
                                   PipetteBlockEntity be) {

        try {
            // 获取模型，如果失败则使用后备模型
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

            // 如果所有模型都失败，直接返回
            if (base == null || lowerBody == null || upperBody == null || head == null) {
                return;
            }

            // 渲染底座
            transformBase(msr, baseAngle);
            base.transform(msLocal).renderInto(ms, builder);

            // 渲染下臂
            transformLowerArm(msr, lowerArmAngle);
            lowerBody.color(color).transform(msLocal).renderInto(ms, builder);

            // 渲染上臂
            transformUpperArm(msr, upperArmAngle);
            upperBody.color(color).transform(msLocal).renderInto(ms, builder);

            // 渲染头部
            transformHead(msr, headAngle);
            if (inverted) {
                msr.rotateZDegrees(180.0F);
            }
            head.transform(msLocal).renderInto(ms, builder);

        } catch (Exception e) {
            // 静默处理
        }
    }

    /**
     * 安全地获取模型，如果失败则使用后备模型
     */
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

        // 如果失败，尝试后备模型
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

    private void renderFluidInNeedle(FluidStack fluidStack, int capacity, PoseStack ms,
                                     MultiBufferSource buffer, int light, boolean isInjectMode) {
        if (fluidStack.isEmpty()) return;

        try {
            float fillFactor = (float) fluidStack.getAmount() / capacity;

            // 针头内部流体渲染
            float needleRadius = 1.5f / 16f;
            float needleLength = 4f / 16f;
            float fluidLength = fillFactor * needleLength;

            // 根据模式调整位置
            float zOffset = isInjectMode ?
                    -(needleLength - fluidLength) / 2 :
                    (needleLength - fluidLength) / 2;

            ms.translate(0, 0, zOffset);

            // 自定义流体渲染
            float xMin = -needleRadius;
            float xMax = needleRadius;
            float yMin = -needleRadius;
            float yMax = needleRadius;
            float zMin = -fluidLength / 2;
            float zMax = fluidLength / 2;

            // 获取流体属性
            Fluid fluid = fluidStack.getFluid();
            IClientFluidTypeExtensions fluidAttributes = IClientFluidTypeExtensions.of(fluid);

            // 获取流体纹理
            TextureAtlasSprite fluidTexture = Minecraft.getInstance()
                    .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(fluidAttributes.getStillTexture(fluidStack));

            // 获取流体颜色
            int color = fluidAttributes.getTintColor(fluidStack);
            int a = (color >> 24) & 0xFF;
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            if (a == 0) a = 255;

            // 获取渲染类型
            RenderType renderType = RenderType.translucent();
            VertexConsumer builder = buffer.getBuffer(renderType);

            // 纹理坐标
            float u0 = fluidTexture.getU0();
            float u1 = fluidTexture.getU1();
            float v0 = fluidTexture.getV0();
            float v1 = fluidTexture.getV1();

            PoseStack.Pose pose = ms.last();

            // 渲染六个面（代码保持不变）
            // ... [原有的六个面渲染代码]

        } catch (Exception e) {
            // 静默处理
        }
    }

    // ... [其余辅助方法保持不变]

    @Override
    protected SuperByteBuffer getRotatedModel(PipetteBlockEntity be, BlockState state) {
        try {
            return CachedBuffers.partial(CFPartialModels.PIPETTE_COG, state);
        } catch (Exception e) {
            // 如果失败，尝试使用Create的默认齿轮
            try {
                return CachedBuffers.partial(AllPartialModels.ARM_COG, state);
            } catch (Exception e2) {
                return null;
            }
        }
    }
}