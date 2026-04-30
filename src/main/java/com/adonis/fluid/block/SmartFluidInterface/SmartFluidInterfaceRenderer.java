package com.adonis.fluid.block.SmartFluidInterface;

import com.adonis.fluid.registry.CFPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

public class SmartFluidInterfaceRenderer extends SmartBlockEntityRenderer<SmartFluidInterfaceBlockEntity> {

    public SmartFluidInterfaceRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(SmartFluidInterfaceBlockEntity blockEntity, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(blockEntity, partialTicks, ms, buffer, light, overlay);

        if (blockEntity.getLevel() == null || CFPartialModels.FLUID_INTERFACE_DRAIN == null)
            return;

        Direction facing = blockEntity.getBlockState().getValue(SmartFluidInterfaceBlock.FACING);
        Direction attachedFace = facing.getOpposite();

        if (FluidPropagator.hasFluidCapability(blockEntity.getLevel(),
                blockEntity.getBlockPos().relative(attachedFace), facing)) {
            SuperByteBuffer drain = CachedBuffers.partialFacing(
                    CFPartialModels.FLUID_INTERFACE_DRAIN,
                    blockEntity.getBlockState(),
                    attachedFace
            );
            if (drain != null) {
                drain.light(light).renderInto(ms, buffer.getBuffer(RenderType.solid()));
            }
        }
    }
}
