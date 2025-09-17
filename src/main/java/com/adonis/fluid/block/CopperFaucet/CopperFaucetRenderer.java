package com.adonis.fluid.block.CopperFaucet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.fluids.FluidFX;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createmod.catnip.platform.ForgeCatnipServices;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;

public class CopperFaucetRenderer extends SafeBlockEntityRenderer<CopperFaucetBlockEntity> {
    
    public CopperFaucetRenderer(BlockEntityRendererProvider.Context context) {
    }
    
    @Override
    protected void renderSafe(CopperFaucetBlockEntity be, float partialTicks, PoseStack ms, 
                              MultiBufferSource buffer, int light, int overlay) {
        
        FluidStack fluid = be.getCache();
        if (fluid.isEmpty())
            return;
        
        BlockState state = be.getBlockState();
        boolean isOpen = state.getValue(BlockStateProperties.OPEN);
        
        if (!isOpen)
            return;
        
        // 根据方向调整渲染位置
        Direction facing = state.getValue(CopperFaucetBlock.FACING);
        
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
        
        // 如果正在注液，渲染注液效果
        if (be.isProcessing()) {
            renderFillingEffect(be, fluid, ms, buffer, light, partialTicks);
        }
        
        ms.popPose();
    }
    
    private void renderFluidStream(CopperFaucetBlockEntity be, FluidStack fluid, PoseStack ms,
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
        
        // 使用类似youkaishomecoming的流体渲染
        ForgeCatnipServices.FLUID_RENDERER.renderFluidBox(
                fluid, startX, endY, startZ,
                endX, startY, endZ,
                buffer, ms, light, false, true
        );
    }
    
    private void renderFillingEffect(CopperFaucetBlockEntity be, FluidStack fluid, PoseStack ms,
                                     MultiBufferSource buffer, int light, float partialTicks) {
        // 注液时的特效，类似注液器
        float processingProgress = ((float) be.getProcessingTicks() - partialTicks) / 20f;
        
        if (processingProgress > 0) {
            // 渲染飞溅效果
            float splash = 1f - processingProgress;
            if (splash < 0.5f) {
                float splashRadius = splash * 0.25f;
                
                // 在底部渲染一个扩散的流体池
                ForgeCatnipServices.FLUID_RENDERER.renderFluidBox(
                        fluid, 
                        0.5f - splashRadius, -15.5f / 16f, 0.5f - splashRadius,
                        0.5f + splashRadius, -15f / 16f, 0.5f + splashRadius,
                        buffer, ms, light, false, true
                );
            }
        }
    }
    
    // 客户端粒子效果（可选）
    public static void spawnFluidParticles(CopperFaucetBlockEntity be) {
        if (be.getLevel() == null || !be.getLevel().isClientSide)
            return;
        
        FluidStack fluid = be.getCache();
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