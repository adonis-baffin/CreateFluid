package com.adonis.fluid.block.FluidInterface;

import com.adonis.fluid.registry.CFPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.fluids.FluidPropagator;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

public class FluidInterfaceRenderer implements BlockEntityRenderer<FluidInterfaceBlockEntity> {

    public FluidInterfaceRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FluidInterfaceBlockEntity blockEntity, float partialTick, PoseStack ms,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (blockEntity.getLevel() == null || CFPartialModels.FLUID_INTERFACE_DRAIN == null)
            return;

        Direction facing = blockEntity.getBlockState().getValue(FluidInterfaceBlock.FACING);
        Direction attachedFace = facing.getOpposite();

        if (FluidPropagator.hasFluidCapability(blockEntity.getLevel(),
                blockEntity.getBlockPos().relative(attachedFace), facing)) {
            SuperByteBuffer drain = CachedBuffers.partialFacing(
                    CFPartialModels.FLUID_INTERFACE_DRAIN,
                    blockEntity.getBlockState(),
                    attachedFace
            );
            if (drain != null) {
                drain.light(packedLight).renderInto(ms, buffer.getBuffer(RenderType.solid()));
            }
        }
    }
}
