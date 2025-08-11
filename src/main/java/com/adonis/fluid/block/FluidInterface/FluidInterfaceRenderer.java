package com.adonis.fluid.block.FluidInterface;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class FluidInterfaceRenderer implements BlockEntityRenderer<FluidInterfaceBlockEntity> {

    public FluidInterfaceRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FluidInterfaceBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        // 流体接口不需要特殊渲染，使用默认的方块模型即可
    }
}