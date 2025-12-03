package com.adonis.fluid.block.GutterOutlet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.createmod.catnip.render.FluidRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

/**
 * 集水器渲染器
 * 渲染梯形流体，参考 AqueductRenderer 实现
 */
public class GutterOutletRenderer extends SmartBlockEntityRenderer<GutterOutletBlockEntity> {

    public GutterOutletRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(GutterOutletBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        FluidStack fluidStack = be.getFluid();
        if (fluidStack.isEmpty()) return;

        float fluidLevel = be.getRenderedFluidLevel(partialTicks);
        if (fluidLevel <= 0) return;

        BlockState state = be.getBlockState();
        Direction facing = state.getValue(GutterOutletBlock.FACING);

        renderTrapezoidalFluid(be, fluidStack, fluidLevel, facing, partialTicks, ms, buffer, light);
    }

    private void renderTrapezoidalFluid(GutterOutletBlockEntity be, FluidStack fluidStack,
                                        float level, Direction facing, float partialTicks,
                                        PoseStack ms, MultiBufferSource buffer, int light) {

        Fluid fluid = fluidStack.getFluid();
        IClientFluidTypeExtensions clientFluid = IClientFluidTypeExtensions.of(fluid);
        TextureAtlasSprite stillTexture = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(clientFluid.getStillTexture(fluidStack));

        int color = clientFluid.getTintColor(fluidStack);
        VertexConsumer builder = buffer.getBuffer(RenderType.translucent());

        // 流体 Y 范围：5/16 到 14/16
        float baseY = 5f / 16f;
        float maxHeight = 9f / 16f; // 14/16 - 5/16 = 9/16
        float currentY = baseY + maxHeight * level;

        // FACING 表示宽面朝向的方向
        // 如果 FACING 是南北，则宽面在南北，窄面在东西 -> 梯形沿 X 轴
        // 如果 FACING 是东西，则宽面在东西，窄面在南北 -> 梯形沿 Z 轴
// 在 renderTrapezoidalFluid 方法中
        boolean isNorthSouth = (facing.getAxis() == Direction.Axis.X);  // 改成 X

        ms.pushPose();

        if (isNorthSouth) {
            // 宽面在南北（Z方向），窄面在东西（X方向）
            renderNorthSouthFluid(builder, ms, stillTexture, currentY, baseY, light, color);
        } else {
            // 宽面在东西（X方向），窄面在南北（Z方向）
            renderEastWestFluid(builder, ms, stillTexture, currentY, baseY, light, color);
        }

        ms.popPose();
    }

    /**
     * 南北朝向时的流体渲染
     * 宽面在南北（Z方向宽），窄面在东西（X方向窄）
     */
    private void renderNorthSouthFluid(VertexConsumer builder, PoseStack ms,
                                       TextureAtlasSprite texture, float topY, float bottomY,
                                       int light, int color) {
        float wallThickness = 2f / 16f;

        // 底部较窄，顶部较宽（X方向）
        float bottomMinX = 4.5f / 16f;
        float bottomMaxX = 11.5f / 16f;
        float topMinX = wallThickness + 0.5f / 16f;
        float topMaxX = 1f - wallThickness - 0.5f / 16f;

        // Z方向保持边距
        float zMin = 1f / 16f;
        float zMax = 15f / 16f;

        // 顶面
        FluidRenderHelper.renderStillTiledFace(Direction.UP,
                topMinX, zMin, topMaxX, zMax, topY,
                builder, ms, light, color, texture);

        // 底面
        if (bottomY > 0) {
            FluidRenderHelper.renderStillTiledFace(Direction.DOWN,
                    bottomMinX, zMin, bottomMaxX, zMax, bottomY,
                    builder, ms, light, color, texture);
        }

        renderNorthSouthSides(builder, ms, texture,
                bottomMinX, bottomMaxX, topMinX, topMaxX,
                bottomY, topY, zMin, zMax, light, color);
    }

    /**
     * 东西朝向时的流体渲染
     * 宽面在东西（X方向宽），窄面在南北（Z方向窄）
     */
    private void renderEastWestFluid(VertexConsumer builder, PoseStack ms,
                                     TextureAtlasSprite texture, float topY, float bottomY,
                                     int light, int color) {
        float wallThickness = 2f / 16f;

        // 底部较窄，顶部较宽（Z方向）
        float bottomMinZ = 4.5f / 16f;
        float bottomMaxZ = 11.5f / 16f;
        float topMinZ = wallThickness + 0.5f / 16f;
        float topMaxZ = 1f - wallThickness - 0.5f / 16f;

        // X方向保持边距
        float xMin = 1f / 16f;
        float xMax = 15f / 16f;

        // 顶面
        FluidRenderHelper.renderStillTiledFace(Direction.UP,
                xMin, topMinZ, xMax, topMaxZ, topY,
                builder, ms, light, color, texture);

        // 底面
        if (bottomY > 0) {
            FluidRenderHelper.renderStillTiledFace(Direction.DOWN,
                    xMin, bottomMinZ, xMax, bottomMaxZ, bottomY,
                    builder, ms, light, color, texture);
        }

        renderEastWestSides(builder, ms, texture,
                bottomMinZ, bottomMaxZ, topMinZ, topMaxZ,
                bottomY, topY, xMin, xMax, light, color);
    }

    private void renderNorthSouthSides(VertexConsumer builder, PoseStack ms, TextureAtlasSprite texture,
                                       float bottomMinX, float bottomMaxX, float topMinX, float topMaxX,
                                       float bottomY, float topY, float zMin, float zMax,
                                       int light, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        float minU = texture.getU0();
        float maxU = texture.getU1();
        float minV = texture.getV0();
        float maxV = texture.getV1();

        // 西侧斜面
        renderQuad(builder, ms,
                topMinX, topY, zMax,
                topMinX, topY, zMin,
                bottomMinX, bottomY, zMin,
                bottomMinX, bottomY, zMax,
                minU, minV, maxU, maxV,
                r * 0.6f, g * 0.6f, b * 0.6f, a, light);

        // 东侧斜面
        renderQuad(builder, ms,
                topMaxX, topY, zMin,
                topMaxX, topY, zMax,
                bottomMaxX, bottomY, zMax,
                bottomMaxX, bottomY, zMin,
                minU, minV, maxU, maxV,
                r * 0.6f, g * 0.6f, b * 0.6f, a, light);

        // 北侧端面
        if (zMin > 0) {
            renderQuad(builder, ms,
                    topMinX, topY, zMin,
                    topMaxX, topY, zMin,
                    bottomMaxX, bottomY, zMin,
                    bottomMinX, bottomY, zMin,
                    minU, minV, maxU, maxV,
                    r * 0.8f, g * 0.8f, b * 0.8f, a, light);
        }

        // 南侧端面
        if (zMax < 1) {
            renderQuad(builder, ms,
                    topMaxX, topY, zMax,
                    topMinX, topY, zMax,
                    bottomMinX, bottomY, zMax,
                    bottomMaxX, bottomY, zMax,
                    minU, minV, maxU, maxV,
                    r * 0.8f, g * 0.8f, b * 0.8f, a, light);
        }
    }

    private void renderEastWestSides(VertexConsumer builder, PoseStack ms, TextureAtlasSprite texture,
                                     float bottomMinZ, float bottomMaxZ, float topMinZ, float topMaxZ,
                                     float bottomY, float topY, float xMin, float xMax,
                                     int light, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        float minU = texture.getU0();
        float maxU = texture.getU1();
        float minV = texture.getV0();
        float maxV = texture.getV1();

        // 北侧斜面
        renderQuad(builder, ms,
                xMin, topY, topMinZ,
                xMax, topY, topMinZ,
                xMax, bottomY, bottomMinZ,
                xMin, bottomY, bottomMinZ,
                minU, minV, maxU, maxV,
                r * 0.6f, g * 0.6f, b * 0.6f, a, light);

        // 南侧斜面
        renderQuad(builder, ms,
                xMin, bottomY, bottomMaxZ,
                xMax, bottomY, bottomMaxZ,
                xMax, topY, topMaxZ,
                xMin, topY, topMaxZ,
                minU, minV, maxU, maxV,
                r * 0.6f, g * 0.6f, b * 0.6f, a, light);

        // 西侧端面
        if (xMin > 0) {
            renderQuad(builder, ms,
                    xMin, bottomY, bottomMinZ,
                    xMin, bottomY, bottomMaxZ,
                    xMin, topY, topMaxZ,
                    xMin, topY, topMinZ,
                    minU, minV, maxU, maxV,
                    r * 0.8f, g * 0.8f, b * 0.8f, a, light);
        }

        // 东侧端面
        if (xMax < 1) {
            renderQuad(builder, ms,
                    xMax, topY, topMinZ,
                    xMax, topY, topMaxZ,
                    xMax, bottomY, bottomMaxZ,
                    xMax, bottomY, bottomMinZ,
                    minU, minV, maxU, maxV,
                    r * 0.8f, g * 0.8f, b * 0.8f, a, light);
        }
    }

    private void renderQuad(VertexConsumer builder, PoseStack ms,
                            float x1, float y1, float z1,
                            float x2, float y2, float z2,
                            float x3, float y3, float z3,
                            float x4, float y4, float z4,
                            float minU, float minV, float maxU, float maxV,
                            float r, float g, float b, float a, int light) {
        var pose = ms.last().pose();
        var normal = ms.last().normal();

        // 计算法线
        float e1x = x2 - x1;
        float e1y = y2 - y1;
        float e1z = z2 - z1;
        float e2x = x4 - x1;
        float e2y = y4 - y1;
        float e2z = z4 - z1;

        float nx = e1y * e2z - e1z * e2y;
        float ny = e1z * e2x - e1x * e2z;
        float nz = e1x * e2y - e1y * e2x;

        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 0.0001f) {
            nx /= length;
            ny /= length;
            nz /= length;
        }

        builder.vertex(pose, x1, y1, z1)
                .color(r, g, b, a)
                .uv(minU, minV)
                .overlayCoords(0, 10)
                .uv2(light)
                .normal(normal, nx, ny, nz)
                .endVertex();

        builder.vertex(pose, x2, y2, z2)
                .color(r, g, b, a)
                .uv(maxU, minV)
                .overlayCoords(0, 10)
                .uv2(light)
                .normal(normal, nx, ny, nz)
                .endVertex();

        builder.vertex(pose, x3, y3, z3)
                .color(r, g, b, a)
                .uv(maxU, maxV)
                .overlayCoords(0, 10)
                .uv2(light)
                .normal(normal, nx, ny, nz)
                .endVertex();

        builder.vertex(pose, x4, y4, z4)
                .color(r, g, b, a)
                .uv(minU, maxV)
                .overlayCoords(0, 10)
                .uv2(light)
                .normal(normal, nx, ny, nz)
                .endVertex();
    }
}