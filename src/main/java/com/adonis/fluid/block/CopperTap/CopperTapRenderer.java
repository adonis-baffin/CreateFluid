package com.adonis.fluid.block.CopperTap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.fluids.FluidFX;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

public class CopperTapRenderer extends SafeBlockEntityRenderer<CopperTapBlockEntity> {

    public CopperTapRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(CopperTapBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {

        // 只在有流体要渲染时才渲染
        if (!be.hasFluidToRender())
            return;

        FluidStack fluid = be.getRenderingFluid();
        if (fluid.isEmpty())
            return;

        BlockState state = be.getBlockState();
        boolean isOpen = state.getValue(BlockStateProperties.OPEN);

        if (!isOpen)
            return;

        // 根据方向调整渲染位置
        Direction facing = state.getValue(CopperTapBlock.FACING);

        ms.pushPose();

        // 根据朝向旋转
        switch (facing) {
            case SOUTH:
                ms.translate(0.5, 0, 0.5);
                ms.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
                ms.translate(-0.5, 0, -0.5);
                break;
            case WEST:
                ms.translate(0.5, 0, 0.5);
                ms.mulPose(com.mojang.math.Axis.YP.rotationDegrees(270));
                ms.translate(-0.5, 0, -0.5);
                break;
            case EAST:
                ms.translate(0.5, 0, 0.5);
                ms.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
                ms.translate(-0.5, 0, -0.5);
                break;
        }

        // 渲染流体流
        renderFluidStream(be, fluid, ms, buffer, light, partialTicks);

        // 如果正在注液，渲染额外的注液效果
        if (be.isProcessing()) {
            renderFillingEffect(be, fluid, ms, buffer, light, partialTicks);
        }

        ms.popPose();
    }

    private void renderFluidStream(CopperTapBlockEntity be, FluidStack fluid, PoseStack ms,
                                   MultiBufferSource buffer, int light, float partialTicks) {
        // 流体流从出水口到下方
        float startX = 6f / 16f;
        float endX = 10f / 16f;
        float startZ = 6f / 16f;
        float endZ = 10f / 16f;
        float startY = 4f / 16f; // 出水口底部
        float endY = -8f / 16f; // 延伸到下方方块

        // 根据处理进度调整流的大小
        if (be.isProcessing()) {
            float progress = (float) be.getProcessingTicks() / 20f;
            float scale = 0.75f + 0.25f * Mth.sin(progress * 3.14159f);

            float center = 0.5f;
            startX = center - (center - startX) * scale;
            endX = center + (endX - center) * scale;
            startZ = center - (center - startZ) * scale;
            endZ = center + (endZ - center) * scale;
        }

        // 使用自定义流体渲染方法
        renderFluidBox(fluid, startX, endY, startZ, endX, startY, endZ,
                buffer, ms, light, false, true);
    }

    private void renderFillingEffect(CopperTapBlockEntity be, FluidStack fluid, PoseStack ms,
                                     MultiBufferSource buffer, int light, float partialTicks) {
        // 注液时的特效，类似注液器
        int processingTicks = be.getProcessingTicks();
        if (processingTicks <= 0) return;

        float processingProgress = ((float) processingTicks - partialTicks) / 20f;

        // 渲染注液流
        if (processingProgress > 0) {
            // 计算流体流的动态大小
            float flowScale = 0.5f + 0.5f * Mth.sin(processingProgress * 3.14159f);

            // 流体流的范围
            float startX = 0.5f - 0.0625f * flowScale;
            float endX = 0.5f + 0.0625f * flowScale;
            float startZ = 0.5f - 0.0625f * flowScale;
            float endZ = 0.5f + 0.0625f * flowScale;
            float startY = 3f / 16f; // 从龙头底部开始
            float endY = -12f / 16f; // 延伸到下方

            // 渲染细流
            renderFluidBox(fluid, startX, endY, startZ, endX, startY, endZ,
                    buffer, ms, light, false, true);

            // 渲染飞溅效果
            float splash = 1f - processingProgress;
            if (splash < 0.3f) {
                float splashRadius = splash * 0.5f;

                // 在底部渲染扩散的流体池
                renderFluidBox(fluid,
                        0.5f - splashRadius, -15.5f / 16f, 0.5f - splashRadius,
                        0.5f + splashRadius, -15f / 16f, 0.5f + splashRadius,
                        buffer, ms, light, false, true);
            }
        }
    }

    /**
     * 自定义流体渲染方法
     * 基于Catnip的实现，但使用机械动力和原版的API
     */
    private void renderFluidBox(FluidStack fluidStack, float xMin, float yMin, float zMin,
                                float xMax, float yMax, float zMax,
                                MultiBufferSource buffer, PoseStack ms, int light,
                                boolean renderBottom, boolean flowing) {
        if (fluidStack.isEmpty())
            return;

        Fluid fluid = fluidStack.getFluid();
        IClientFluidTypeExtensions fluidAttributes = IClientFluidTypeExtensions.of(fluid);

        // 获取流体纹理
        TextureAtlasSprite fluidTexture;
        if (flowing) {
            fluidTexture = Minecraft.getInstance()
                    .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(fluidAttributes.getFlowingTexture(fluidStack));
        } else {
            fluidTexture = Minecraft.getInstance()
                    .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(fluidAttributes.getStillTexture(fluidStack));
        }

        // 获取流体颜色
        int color = fluidAttributes.getTintColor(fluidStack);

        // 获取渲染类型 - 使用半透明渲染类型
        RenderType renderType = RenderType.translucent();
        VertexConsumer builder = buffer.getBuffer(renderType);

        // 使用类似Catnip的方式渲染每个面
        renderFluidFaces(builder, ms, fluidTexture, xMin, yMin, zMin, xMax, yMax, zMax,
                color, light, renderBottom);
    }

    private void renderFluidFaces(VertexConsumer builder, PoseStack ms, TextureAtlasSprite texture,
                                  float xMin, float yMin, float zMin, float xMax, float yMax, float zMax,
                                  int color, int light, boolean renderBottom) {
        // 提取颜色分量
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        if (a == 0) a = 255;

        // 纹理坐标
        float u0 = texture.getU0();
        float u1 = texture.getU1();
        float v0 = texture.getV0();
        float v1 = texture.getV1();

        PoseStack.Pose pose = ms.last();

        // 渲染各个面（按照Catnip的顺序）
        Direction[] directions = Direction.values();
        for (Direction dir : directions) {
            if (dir == Direction.DOWN && !renderBottom) {
                continue;
            }

            renderFace(builder, pose, dir, xMin, yMin, zMin, xMax, yMax, zMax,
                    u0, u1, v0, v1, r, g, b, a, light);
        }
    }

    private void renderFace(VertexConsumer builder, PoseStack.Pose pose, Direction dir,
                            float xMin, float yMin, float zMin, float xMax, float yMax, float zMax,
                            float u0, float u1, float v0, float v1,
                            int r, int g, int b, int a, int light) {
        float nx = dir.getNormal().getX();
        float ny = dir.getNormal().getY();
        float nz = dir.getNormal().getZ();

        switch (dir) {
            case DOWN: // Y-
                addVertex(builder, pose, xMin, yMin, zMin, u0, v0, r, g, b, a, light, nx, ny, nz);
                addVertex(builder, pose, xMax, yMin, zMin, u1, v0, r, g, b, a, light, nx, ny, nz);
                addVertex(builder, pose, xMax, yMin, zMax, u1, v1, r, g, b, a, light, nx, ny, nz);
                addVertex(builder, pose, xMin, yMin, zMax, u0, v1, r, g, b, a, light, nx, ny, nz);
                break;
            case UP: // Y+
                addVertex(builder, pose, xMin, yMax, zMin, u0, v0, r, g, b, a, light, nx, ny, nz);
                addVertex(builder, pose, xMin, yMax, zMax, u0, v1, r, g, b, a, light, nx, ny, nz);
                addVertex(builder, pose, xMax, yMax, zMax, u1, v1, r, g, b, a, light, nx, ny, nz);
                addVertex(builder, pose, xMax, yMax, zMin, u1, v0, r, g, b, a, light, nx, ny, nz);
                break;
            case NORTH: // Z-
                addVertex(builder, pose, xMin, yMin, zMin, u0, v1, r, g, b, a, light, nx, ny, nz);
                addVertex(builder, pose, xMin, yMax, zMin, u0, v0, r, g, b, a, light, nx, ny, nz);
                addVertex(builder, pose, xMax, yMax, zMin, u1, v0, r, g, b, a, light, nx, ny, nz);
                addVertex(builder, pose, xMax, yMin, zMin, u1, v1, r, g, b, a, light, nx, ny, nz);
                break;
            case SOUTH: // Z+
                addVertex(builder, pose, xMin, yMin, zMax, u0, v1, r, g, b, a, light, nx, ny, nz);
                addVertex(builder, pose, xMax, yMin, zMax, u1, v1, r, g, b, a, light, nx, ny, nz);
                addVertex(builder, pose, xMax, yMax, zMax, u1, v0, r, g, b, a, light, nx, ny, nz);
                addVertex(builder, pose, xMin, yMax, zMax, u0, v0, r, g, b, a, light, nx, ny, nz);
                break;
            case WEST: // X-
                addVertex(builder, pose, xMin, yMin, zMin, u0, v1, r, g, b, a, light, nx, ny, nz);
                addVertex(builder, pose, xMin, yMin, zMax, u1, v1, r, g, b, a, light, nx, ny, nz);
                addVertex(builder, pose, xMin, yMax, zMax, u1, v0, r, g, b, a, light, nx, ny, nz);
                addVertex(builder, pose, xMin, yMax, zMin, u0, v0, r, g, b, a, light, nx, ny, nz);
                break;
            case EAST: // X+
                addVertex(builder, pose, xMax, yMin, zMin, u0, v1, r, g, b, a, light, nx, ny, nz);
                addVertex(builder, pose, xMax, yMax, zMin, u0, v0, r, g, b, a, light, nx, ny, nz);
                addVertex(builder, pose, xMax, yMax, zMax, u1, v0, r, g, b, a, light, nx, ny, nz);
                addVertex(builder, pose, xMax, yMin, zMax, u1, v1, r, g, b, a, light, nx, ny, nz);
                break;
        }
    }

    private void addVertex(VertexConsumer builder, PoseStack.Pose pose,
                           float x, float y, float z,
                           float u, float v,
                           int r, int g, int b, int a, int light,
                           float nx, float ny, float nz) {
        builder.vertex(pose.pose(), x, y, z)
                .color(r, g, b, a)
                .uv(u, v)
                .uv2(light)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
    }

    // 客户端粒子效果（可选）
    public static void spawnFluidParticles(CopperTapBlockEntity be) {
        if (be.getLevel() == null || !be.getLevel().isClientSide)
            return;

        FluidStack fluid = be.getRenderingFluid();
        if (fluid.isEmpty() || !be.getBlockState().getValue(BlockStateProperties.OPEN))
            return;

        // 生成流体粒子
        Vec3 pos = Vec3.atCenterOf(be.getBlockPos()).add(0, -0.25, 0);
        ParticleOptions particle = FluidFX.getFluidParticle(fluid);

        if (be.getLevel().random.nextFloat() < 0.1f) {
            be.getLevel().addParticle(particle,
                    pos.x, pos.y, pos.z,
                    0, -0.05, 0);
        }
    }
}