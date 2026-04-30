package com.adonis.fluid.block.CommunicatingVessel;

import com.adonis.fluid.registry.CFPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class CommunicatingVesselRenderer extends SmartBlockEntityRenderer<CommunicatingVesselBlockEntity> {

    public CommunicatingVesselRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(CommunicatingVesselBlockEntity blockEntity, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(blockEntity, partialTicks, ms, buffer, light, overlay);

        if (blockEntity.getLevel() == null || CFPartialModels.FLUID_INTERFACE_DRAIN == null) {
            return;
        }

        BlockState state = blockEntity.getBlockState();
        Direction.Axis axis = state.getValue(CommunicatingVesselBlock.AXIS);

        renderDrainIfNeeded(blockEntity, state, Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE), ms, buffer, light);
        renderDrainIfNeeded(blockEntity, state, Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE), ms, buffer, light);
    }

    private void renderDrainIfNeeded(CommunicatingVesselBlockEntity blockEntity, BlockState state, Direction endDirection,
                                     PoseStack ms, MultiBufferSource buffer, int light) {
        BlockPos attachedPos = blockEntity.getBlockPos().relative(endDirection);
        BlockState attachedState = blockEntity.getLevel().getBlockState(attachedPos);

        if (attachedState.getBlock() instanceof CommunicatingVesselBlock) {
            return;
        }

        if (!FluidPropagator.hasFluidCapability(blockEntity.getLevel(), attachedPos, endDirection.getOpposite())) {
            return;
        }

        SuperByteBuffer drain = CachedBuffers.partialFacing(CFPartialModels.FLUID_INTERFACE_DRAIN, state, endDirection);
        if (drain != null) {
            drain.light(light).renderInto(ms, buffer.getBuffer(RenderType.solid()));
        }
    }
}
