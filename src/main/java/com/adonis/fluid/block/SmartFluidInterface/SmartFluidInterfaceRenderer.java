package com.adonis.fluid.block.SmartFluidInterface;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class SmartFluidInterfaceRenderer extends SmartBlockEntityRenderer<SmartFluidInterfaceBlockEntity> {

    public SmartFluidInterfaceRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(SmartFluidInterfaceBlockEntity blockEntity, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(blockEntity, partialTicks, ms, buffer, light, overlay);
        // 过滤槽位的渲染由 SmartBlockEntityRenderer 自动处理
    }
}