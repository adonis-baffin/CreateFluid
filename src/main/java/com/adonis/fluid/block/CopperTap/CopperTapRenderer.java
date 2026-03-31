package com.adonis.fluid.block.CopperTap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

public class CopperTapRenderer extends SafeBlockEntityRenderer<CopperTapBlockEntity> {

    public CopperTapRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(CopperTapBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {

        if (!be.hasFluidToRender())
            return;

        FluidStack fluidStack = be.getRenderingFluid();
        if (fluidStack.isEmpty())
            return;

        BlockState state = be.getBlockState();
        boolean isOpen = state.getValue(BlockStateProperties.OPEN);

        if (!isOpen)
            return;

        Direction facing = state.getValue(CopperTapBlock.FACING);

        ms.pushPose();

        // 根据朝向旋转
        switch (facing) {
            case SOUTH -> {
                ms.translate(0.5, 0, 0.5);
                ms.mulPose(Axis.YP.rotationDegrees(180));
                ms.translate(-0.5, 0, -0.5);
            }
            case WEST -> {
                ms.translate(0.5, 0, 0.5);
                ms.mulPose(Axis.YP.rotationDegrees(270));
                ms.translate(-0.5, 0, -0.5);
            }
            case EAST -> {
                ms.translate(0.5, 0, 0.5);
                ms.mulPose(Axis.YP.rotationDegrees(90));
                ms.translate(-0.5, 0, -0.5);
            }
        }

        // 渲染流体流
        renderFluidStream(be, fluidStack, ms, buffer, light, partialTicks);

        ms.popPose();
    }

    private void renderFluidStream(CopperTapBlockEntity be, FluidStack fluidStack, PoseStack ms,
                                   MultiBufferSource buffer, int light, float partialTicks) {
        float xMin = 6f / 16f;
        float xMax = 10f / 16f;
        float zMin = 6f / 16f;
        float zMax = 10f / 16f;
        float yTop = 4f / 16f;
        float yBottom = -8f / 16f;

        // 动画效果
        if (be.isProcessing()) {
            float progress = (float) be.getProcessingTicks() / 20f;
            float scale = 0.75f + 0.25f * Mth.sin(progress * 3.14159f);

            float center = 0.5f;
            xMin = center - (center - xMin) * scale;
            xMax = center + (xMax - center) * scale;
            zMin = center - (center - zMin) * scale;
            zMax = center + (zMax - center) * scale;
        }

        // 使用我们自己的流体渲染
        renderFluidBox(fluidStack, xMin, yBottom, zMin, xMax, yTop, zMax, buffer, ms, light, false);
    }

    private void renderFluidBox(FluidStack fluidStack, float xMin, float yMin, float zMin,
                                float xMax, float yMax, float zMax,
                                MultiBufferSource buffer, PoseStack ms, int light, boolean renderBottom) {
        if (fluidStack.isEmpty())
            return;

        Fluid fluid = fluidStack.getFluid();
        IClientFluidTypeExtensions fluidExtensions = IClientFluidTypeExtensions.of(fluid);

        // 获取流体纹理
        TextureAtlasSprite texture = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(fluidExtensions.getStillTexture(fluidStack));

        // 获取流体颜色
        int color = fluidExtensions.getTintColor(fluidStack);

        // 调整光照
        int blockLightIn = (light >> 4) & 0xF;
        int luminosity = Math.max(blockLightIn, fluid.getFluidType().getLightLevel(fluidStack));
        light = (light & 0xF00000) | luminosity << 4;

        VertexConsumer builder = buffer.getBuffer(RenderType.translucent());

        // 渲染每个面
        // 顶面
        renderStillTiledFace(Direction.UP, xMin, zMin, xMax, zMax, yMax,
                builder, ms, light, color, texture);

        // 底面（如果需要）
        if (renderBottom) {
            renderStillTiledFace(Direction.DOWN, xMin, zMin, xMax, zMax, yMin,
                    builder, ms, light, color, texture);
        }

        // 四个侧面
        renderStillTiledFace(Direction.NORTH, xMin, yMin, xMax, yMax, zMin,
                builder, ms, light, color, texture);
        renderStillTiledFace(Direction.SOUTH, xMin, yMin, xMax, yMax, zMax,
                builder, ms, light, color, texture);
        renderStillTiledFace(Direction.WEST, zMin, yMin, zMax, yMax, xMin,
                builder, ms, light, color, texture);
        renderStillTiledFace(Direction.EAST, zMin, yMin, zMax, yMax, xMax,
                builder, ms, light, color, texture);
    }

    private static void renderStillTiledFace(Direction dir, float left, float down, float right, float up,
                                             float depth, VertexConsumer builder, PoseStack ms,
                                             int light, int color, TextureAtlasSprite texture) {
        renderTiledFace(dir, left, down, right, up, depth, builder, ms, light, color, texture, 1);
    }

    private static void renderTiledFace(Direction dir, float left, float down, float right, float up,
                                        float depth, VertexConsumer builder, PoseStack ms, int light,
                                        int color, TextureAtlasSprite texture, float textureScale) {
        boolean positive = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        boolean horizontal = dir.getAxis().isHorizontal();
        boolean x = dir.getAxis() == Direction.Axis.X;

        float shrink = texture.uvShrinkRatio() * 0.25f * textureScale;
        float centerU = texture.getU0() + (texture.getU1() - texture.getU0()) * 0.5f * textureScale;
        float centerV = texture.getV0() + (texture.getV1() - texture.getV0()) * 0.5f * textureScale;

        float f;
        float x2 = 0;
        float y2 = 0;
        float u1, u2;
        float v1, v2;

        for (float x1 = left; x1 < right; x1 = x2) {
            f = Mth.floor(x1);
            x2 = Math.min(f + 1, right);
            if (dir == Direction.NORTH || dir == Direction.EAST) {
                f = Mth.ceil(x2);
                u1 = texture.getU((f - x2) * textureScale);
                u2 = texture.getU((f - x1) * textureScale);
            } else {
                u1 = texture.getU((x1 - f) * textureScale);
                u2 = texture.getU((x2 - f) * textureScale);
            }
            u1 = Mth.lerp(shrink, u1, centerU);
            u2 = Mth.lerp(shrink, u2, centerU);

            for (float y1 = down; y1 < up; y1 = y2) {
                f = Mth.floor(y1);
                y2 = Math.min(f + 1, up);
                if (dir == Direction.UP) {
                    v1 = texture.getV((y1 - f) * textureScale);
                    v2 = texture.getV((y2 - f) * textureScale);
                } else {
                    f = Mth.ceil(y2);
                    v1 = texture.getV((f - y2) * textureScale);
                    v2 = texture.getV((f - y1) * textureScale);
                }
                v1 = Mth.lerp(shrink, v1, centerV);
                v2 = Mth.lerp(shrink, v2, centerV);

                if (horizontal) {
                    if (x) {
                        putVertex(builder, ms, depth, y2, positive ? x2 : x1, color, u1, v1, dir, light);
                        putVertex(builder, ms, depth, y1, positive ? x2 : x1, color, u1, v2, dir, light);
                        putVertex(builder, ms, depth, y1, positive ? x1 : x2, color, u2, v2, dir, light);
                        putVertex(builder, ms, depth, y2, positive ? x1 : x2, color, u2, v1, dir, light);
                    } else {
                        putVertex(builder, ms, positive ? x1 : x2, y2, depth, color, u1, v1, dir, light);
                        putVertex(builder, ms, positive ? x1 : x2, y1, depth, color, u1, v2, dir, light);
                        putVertex(builder, ms, positive ? x2 : x1, y1, depth, color, u2, v2, dir, light);
                        putVertex(builder, ms, positive ? x2 : x1, y2, depth, color, u2, v1, dir, light);
                    }
                } else {
                    putVertex(builder, ms, x1, depth, positive ? y1 : y2, color, u1, v1, dir, light);
                    putVertex(builder, ms, x1, depth, positive ? y2 : y1, color, u1, v2, dir, light);
                    putVertex(builder, ms, x2, depth, positive ? y2 : y1, color, u2, v2, dir, light);
                    putVertex(builder, ms, x2, depth, positive ? y1 : y2, color, u2, v1, dir, light);
                }
            }
        }
    }

    private static void putVertex(VertexConsumer builder, PoseStack ms, float x, float y, float z,
                                  int color, float u, float v, Direction face, int light) {
        Vec3i normal = face.getNormal();
        PoseStack.Pose peek = ms.last();
        int a = color >> 24 & 0xff;
        int r = color >> 16 & 0xff;
        int g = color >> 8 & 0xff;
        int b = color & 0xff;

        builder.addVertex(peek.pose(), x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setLight(light)
                .setNormal(peek, normal.getX(), normal.getY(), normal.getZ());
    }
}