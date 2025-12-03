package com.adonis.fluid.block.CopperTap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.content.fluids.FluidFX;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
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

        // 检查是否需要渲染
        if (!be.hasFluidToRender())
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
                ms.mulPose(Axis.YP.rotationDegrees(180));
                ms.translate(-0.5, 0, -0.5);
                break;
            case WEST:
                ms.translate(0.5, 0, 0.5);
                ms.mulPose(Axis.YP.rotationDegrees(270));
                ms.translate(-0.5, 0, -0.5);
                break;
            case EAST:
                ms.translate(0.5, 0, 0.5);
                ms.mulPose(Axis.YP.rotationDegrees(90));
                ms.translate(-0.5, 0, -0.5);
                break;
            default:
                break;
        }

        // 判断渲染类型
        if (be.isBeltProcessing()) {
            // 传送带加工时也用和置物台完全一样的渲染
            FluidStack beltFluid = be.getBeltProcessingFluid();
            if (!beltFluid.isEmpty()) {
                renderFluidStream(be, beltFluid, ms, buffer, light, partialTicks);
                renderSplashEffect(be, beltFluid, ms, buffer, light, partialTicks);  // 加上这行就有收尾水洼了
            }
        } else if (be.isProcessing()) {
            // 原来的置物台逻辑保持不变
            FluidStack fluid = be.getRenderingFluid();
            if (!fluid.isEmpty()) {
                renderFluidStream(be, fluid, ms, buffer, light, partialTicks);
                renderSplashEffect(be, fluid, ms, buffer, light, partialTicks);
            }
        } else {
            // 普通流出一律保持短水柱
            FluidStack fluid = be.getRenderingFluid();
            if (!fluid.isEmpty()) {
                renderFluidStream(be, fluid, ms, buffer, light, partialTicks);
            }
        }

        ms.popPose();
    }

    /**
     * 渲染飞溅效果（普通加工用）
     */
    private void renderSplashEffect(CopperTapBlockEntity be, FluidStack fluid, PoseStack ms,
                                    MultiBufferSource buffer, int light, float partialTicks) {
        int processingTicks = be.getProcessingTicks();
        if (processingTicks <= 0) return;

        float processingProgress = ((float) processingTicks - partialTicks) / 20f;

        // 只渲染飞溅效果
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

    /**
     * 普通流体流渲染
     */
    private void renderFluidStream(CopperTapBlockEntity be, FluidStack fluid, PoseStack ms,
                                   MultiBufferSource buffer, int light, float partialTicks) {
        // 流体流从出水口到下方
        float startX = 6f / 16f;
        float endX = 10f / 16f;
        float startZ = 6f / 16f;
        float endZ = 10f / 16f;
        float startY = 4f / 16f; // 出水口底部
        float endY = -6f / 16f; // 延伸到下方方块

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

        // 使用Catnip风格的流体渲染
        renderFluidBox(fluid, startX, endY, startZ,
                endX, startY, endZ,
                buffer, ms, light, false, true);
    }

    /**
     * Catnip风格的流体渲染方法
     */
    private void renderFluidBox(FluidStack fluidStack, float xMin, float yMin, float zMin,
                                float xMax, float yMax, float zMax,
                                MultiBufferSource buffer, PoseStack ms, int light,
                                boolean renderBottom, boolean invertGasses) {
        VertexConsumer builder = buffer.getBuffer(RenderType.translucent());
        renderFluidBox(fluidStack, xMin, yMin, zMin, xMax, yMax, zMax,
                builder, ms, light, renderBottom, invertGasses);
    }

    private void renderFluidBox(FluidStack fluidStack, float xMin, float yMin, float zMin,
                                float xMax, float yMax, float zMax,
                                VertexConsumer builder, PoseStack ms, int light,
                                boolean renderBottom, boolean invertGasses) {
        if (fluidStack.isEmpty())
            return;

        // 获取流体属性
        Fluid fluid = fluidStack.getFluid();
        IClientFluidTypeExtensions fluidAttributes = IClientFluidTypeExtensions.of(fluid);

        // 获取纹理
        ResourceLocation textureLocation = fluidAttributes.getStillTexture(fluidStack);
        TextureAtlasSprite fluidTexture = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(textureLocation);

        // 获取颜色
        int color = fluidAttributes.getTintColor(fluidStack);

        // 调整光照
        int blockLightIn = light >> 4 & 15;
        int luminosity = Math.max(blockLightIn, fluid.getFluidType().getLightLevel());
        light = (light & 0xF00000) | (luminosity << 4);

        Vec3 center = new Vec3(
                xMin + (xMax - xMin) / 2.0,
                yMin + (yMax - yMin) / 2.0,
                zMin + (zMax - zMin) / 2.0
        );

        ms.pushPose();

        // 如果是比空气轻的气体，翻转渲染
        if (invertGasses && fluid.getFluidType().isLighterThanAir()) {
            ms.translate(center.x, center.y, center.z);
            ms.mulPose(Axis.XP.rotationDegrees(180.0F));
            ms.translate(-center.x, -center.y, -center.z);
        }

        // 渲染所有面
        Direction[] directions = Direction.values();
        for (Direction side : directions) {
            if (side == Direction.DOWN && !renderBottom) {
                continue;
            }

            boolean positive = side.getAxisDirection() == Direction.AxisDirection.POSITIVE;

            if (side.getAxis().isHorizontal()) {
                if (side.getAxis() == Direction.Axis.X) {
                    renderStillTiledFace(side, zMin, yMin, zMax, yMax,
                            positive ? xMax : xMin,
                            builder, ms, light, color, fluidTexture);
                } else {
                    renderStillTiledFace(side, xMin, yMin, xMax, yMax,
                            positive ? zMax : zMin,
                            builder, ms, light, color, fluidTexture);
                }
            } else {
                renderStillTiledFace(side, xMin, zMin, xMax, zMax,
                        positive ? yMax : yMin,
                        builder, ms, light, color, fluidTexture);
            }
        }

        ms.popPose();
    }

    /**
     * 渲染平铺的流体面
     */
    private static void renderStillTiledFace(Direction dir, float left, float down,
                                             float right, float up, float depth,
                                             VertexConsumer builder, PoseStack ms,
                                             int light, int color, TextureAtlasSprite texture) {
        renderTiledFace(dir, left, down, right, up, depth, builder, ms,
                light, color, texture, 1.0F);
    }

    private static void renderTiledFace(Direction dir, float left, float down,
                                        float right, float up, float depth,
                                        VertexConsumer builder, PoseStack ms, int light,
                                        int color, TextureAtlasSprite texture, float textureScale) {
        boolean positive = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        boolean horizontal = dir.getAxis().isHorizontal();
        boolean x = dir.getAxis() == Direction.Axis.X;

        float shrink = texture.uvShrinkRatio() * 0.25F * textureScale;
        float centerU = texture.getU0() + (texture.getU1() - texture.getU0()) * 0.5F * textureScale;
        float centerV = texture.getV0() + (texture.getV1() - texture.getV0()) * 0.5F * textureScale;

        float x2 = 0.0F;
        float y2 = 0.0F;

        for (float x1 = left; x1 < right; x1 = x2) {
            float f = (float) Mth.floor(x1);
            x2 = Math.min(f + 1.0F, right);

            float u1, u2;
            if (dir == Direction.NORTH || dir == Direction.EAST) {
                f = (float) Mth.ceil(x2);
                u1 = texture.getU((f - x2) * 16.0F * textureScale);
                u2 = texture.getU((f - x1) * 16.0F * textureScale);
            } else {
                u1 = texture.getU((x1 - f) * 16.0F * textureScale);
                u2 = texture.getU((x2 - f) * 16.0F * textureScale);
            }

            u1 = Mth.lerp(shrink, u1, centerU);
            u2 = Mth.lerp(shrink, u2, centerU);

            for (float y1 = down; y1 < up; y1 = y2) {
                f = (float) Mth.floor(y1);
                y2 = Math.min(f + 1.0F, up);

                float v1, v2;
                if (dir == Direction.UP) {
                    v1 = texture.getV((y1 - f) * 16.0F * textureScale);
                    v2 = texture.getV((y2 - f) * 16.0F * textureScale);
                } else {
                    f = (float) Mth.ceil(y2);
                    v1 = texture.getV((f - y2) * 16.0F * textureScale);
                    v2 = texture.getV((f - y1) * 16.0F * textureScale);
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

    /**
     * 添加顶点
     */
    private static void putVertex(VertexConsumer builder, PoseStack ms,
                                  float x, float y, float z,
                                  int color, float u, float v,
                                  Direction face, int light) {
        Vec3i normal = face.getNormal();
        PoseStack.Pose peek = ms.last();

        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        builder.vertex(peek.pose(), x, y, z)
                .color(r, g, b, a)
                .uv(u, v)
                .uv2(light)
                .normal(peek.normal(), normal.getX(), normal.getY(), normal.getZ())
                .endVertex();
    }

    /**
     * 客户端粒子效果
     */
    public static void spawnFluidParticles(CopperTapBlockEntity be) {
        if (be.getLevel() == null || !be.getLevel().isClientSide)
            return;

        FluidStack fluid = be.getRenderingFluid();
        if (fluid.isEmpty() || !be.getBlockState().getValue(BlockStateProperties.OPEN))
            return;

        Vec3 pos = Vec3.atCenterOf(be.getBlockPos()).add(0, -0.25, 0);
        ParticleOptions particle = FluidFX.getFluidParticle(fluid);

        if (be.getLevel().random.nextFloat() < 0.1f) {
            be.getLevel().addParticle(particle,
                    pos.x, pos.y, pos.z,
                    0, -0.05, 0);
        }
    }
}