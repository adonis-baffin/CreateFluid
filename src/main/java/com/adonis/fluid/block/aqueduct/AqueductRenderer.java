package com.adonis.fluid.block.aqueduct;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import com.simibubi.create.foundation.fluid.FluidRenderer;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.render.FluidRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

public class AqueductRenderer extends SmartBlockEntityRenderer<AbstractAqueductBlockEntity> {

    public AqueductRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(AbstractAqueductBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        FluidStack fluidStack = be.getFluid();
        if (fluidStack.isEmpty()) return;

        float fluidLevel = be.getFluidLevel();
        if (fluidLevel <= 0) return;

        BlockState state = be.getBlockState();
        Direction facing = state.getValue(AbstractAqueductBlock.FACING);

        // 使用自定义方法渲染梯形流体
        renderTrapezoidalFluid(be, fluidStack, fluidLevel, facing, partialTicks, ms, buffer, light);
    }

    private void renderTrapezoidalFluid(AbstractAqueductBlockEntity be, FluidStack fluidStack,
                                        float level, Direction facing, float partialTicks,
                                        PoseStack ms, MultiBufferSource buffer, int light) {

        Fluid fluid = fluidStack.getFluid();
        IClientFluidTypeExtensions clientFluid = IClientFluidTypeExtensions.of(fluid);
        TextureAtlasSprite stillTexture = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(clientFluid.getStillTexture(fluidStack));

        int color = clientFluid.getTintColor(fluidStack);
        VertexConsumer builder = buffer.getBuffer(RenderType.translucent());

        // 流体高度计算
        float baseY = 4f / 16f;  // 水渠底部
        float maxHeight = 6f / 16f;  // 最大高度差
        float currentY = baseY + maxHeight * level;

        // 根据朝向确定水渠内部边界
        boolean isNorthSouth = (facing == Direction.NORTH || facing == Direction.SOUTH);

        ms.pushPose();

        if (isNorthSouth) {
            // 南北向：东西两边有墙
            renderNorthSouthFluid(builder, ms, stillTexture, currentY, baseY, light, color);
        } else {
            // 东西向：南北两边有墙
            renderEastWestFluid(builder, ms, stillTexture, currentY, baseY, light, color);
        }

        ms.popPose();
    }

    private void renderNorthSouthFluid(VertexConsumer builder, PoseStack ms,
                                       TextureAtlasSprite texture, float topY, float bottomY,
                                       int light, int color) {
        // 梯形参数
        float wallThickness = 2f / 16f;
        float length = 14f / 16f;  // Z方向长度（去掉两端小墙）

        // 底部较窄
        float bottomMinX = 4.5f / 16f;
        float bottomMaxX = 11.5f / 16f;

        // 顶部较宽（接近墙壁）
        float topMinX = wallThickness + 0.5f/16f;  // 2.5/16
        float topMaxX = 1f - wallThickness - 0.5f/16f;  // 13.5/16

        float zMin = 1f / 16f;
        float zMax = 15f / 16f;

        // 使用FluidRenderHelper渲染各个面

        // 顶面（水平面）
        FluidRenderHelper.renderStillTiledFace(Direction.UP,
                topMinX, zMin, topMaxX, zMax, topY,
                builder, ms, light, color, texture);

        // 底面（如果需要的话）
        if (bottomY > 0) {
            FluidRenderHelper.renderStillTiledFace(Direction.DOWN,
                    bottomMinX, zMin, bottomMaxX, zMax, bottomY,
                    builder, ms, light, color, texture);
        }

        // 四个斜面需要自定义渲染
        renderTrapezoidSides(builder, ms, texture,
                bottomMinX, bottomMaxX, topMinX, topMaxX,
                bottomY, topY, zMin, zMax, light, color);
    }

    private void renderEastWestFluid(VertexConsumer builder, PoseStack ms,
                                     TextureAtlasSprite texture, float topY, float bottomY,
                                     int light, int color) {
        // 梯形参数
        float wallThickness = 2f / 16f;

        // 底部较窄
        float bottomMinZ = 4.5f / 16f;
        float bottomMaxZ = 11.5f / 16f;

        // 顶部较宽
        float topMinZ = wallThickness + 0.5f/16f;
        float topMaxZ = 1f - wallThickness - 0.5f/16f;

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

        // 四个斜面
        renderTrapezoidSidesEW(builder, ms, texture,
                bottomMinZ, bottomMaxZ, topMinZ, topMaxZ,
                bottomY, topY, xMin, xMax, light, color);
    }

    private void renderTrapezoidSides(VertexConsumer builder, PoseStack ms, TextureAtlasSprite texture,
                                      float bottomMinX, float bottomMaxX, float topMinX, float topMaxX,
                                      float bottomY, float topY, float zMin, float zMax,
                                      int light, int color) {

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        // 获取纹理UV坐标
        float minU = texture.getU0();
        float maxU = texture.getU1();
        float minV = texture.getV0();
        float maxV = texture.getV1();

        // 西侧斜面
        renderQuad(builder, ms,
                topMinX, topY, zMin,
                topMinX, topY, zMax,
                bottomMinX, bottomY, zMax,
                bottomMinX, bottomY, zMin,
                minU, minV, maxU, maxV,
                r * 0.6f, g * 0.6f, b * 0.6f, a, light);

        // 东侧斜面
        renderQuad(builder, ms,
                bottomMaxX, bottomY, zMin,
                bottomMaxX, bottomY, zMax,
                topMaxX, topY, zMax,
                topMaxX, topY, zMin,
                minU, minV, maxU, maxV,
                r * 0.6f, g * 0.6f, b * 0.6f, a, light);

        // 北侧斜面（如果需要）
        if (zMin > 0) {
            renderQuad(builder, ms,
                    bottomMinX, bottomY, zMin,
                    bottomMaxX, bottomY, zMin,
                    topMaxX, topY, zMin,
                    topMinX, topY, zMin,
                    minU, minV, maxU, maxV,
                    r * 0.8f, g * 0.8f, b * 0.8f, a, light);
        }

        // 南侧斜面（如果需要）
        if (zMax < 1) {
            renderQuad(builder, ms,
                    topMinX, topY, zMax,
                    topMaxX, topY, zMax,
                    bottomMaxX, bottomY, zMax,
                    bottomMinX, bottomY, zMax,
                    minU, minV, maxU, maxV,
                    r * 0.8f, g * 0.8f, b * 0.8f, a, light);
        }
    }

    private void renderTrapezoidSidesEW(VertexConsumer builder, PoseStack ms, TextureAtlasSprite texture,
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

        // 西侧斜面
        if (xMin > 0) {
            renderQuad(builder, ms,
                    xMin, bottomY, bottomMinZ,
                    xMin, bottomY, bottomMaxZ,
                    xMin, topY, topMaxZ,
                    xMin, topY, topMinZ,
                    minU, minV, maxU, maxV,
                    r * 0.8f, g * 0.8f, b * 0.8f, a, light);
        }

        // 东侧斜面
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
        float nx = (y2 - y1) * (z3 - z1) - (z2 - z1) * (y3 - y1);
        float ny = (z2 - z1) * (x3 - x1) - (x2 - x1) * (z3 - z1);
        float nz = (x2 - x1) * (y3 - y1) - (y2 - y1) * (x3 - x1);
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= length;
        ny /= length;
        nz /= length;

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