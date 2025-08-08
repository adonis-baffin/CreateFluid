package com.adonis.fluid.block.SmartNozzle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class SmartNozzleRenderer extends SmartBlockEntityRenderer<SmartNozzleBlockEntity> {

    public SmartNozzleRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    protected void renderFilterItem(SmartNozzleBlockEntity blockEntity, ItemStack stack, float partialTicks,
                                    PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5); // 居中
        ms.scale(0.5f, 0.5f, 0.5f); // 缩小物品以适应方块
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, ms, buffer, blockEntity.getLevel(), 0);
        ms.popPose();
    }
}