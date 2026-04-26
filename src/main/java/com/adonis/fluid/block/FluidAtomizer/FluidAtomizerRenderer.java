package com.adonis.fluid.block.FluidAtomizer;

import com.adonis.fluid.registry.CFPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.FluidRenderHelper;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

public class FluidAtomizerRenderer extends KineticBlockEntityRenderer<FluidAtomizerBlockEntity> {

    public FluidAtomizerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(FluidAtomizerBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        if (CFPartialModels.FLUID_ATOMIZER_FAN != null) {
            BlockState state = be.getBlockState();
            Direction facing = state.getValue(com.adonis.fluid.block.FluidAtomizer.FluidAtomizerBlock.FACING);
            Direction shaftDirection = facing.getOpposite();
            VertexConsumer vb = buffer.getBuffer(RenderType.cutoutMipped());
            int lightInFront = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().relative(facing));

            SuperByteBuffer fan = CachedBuffers.partialFacing(CFPartialModels.FLUID_ATOMIZER_FAN, state, shaftDirection);
            standardKineticRotationTransform(fan, be, lightInFront).renderInto(ms, vb);
        }

        renderFluid(be, partialTicks, ms, buffer, light);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(FluidAtomizerBlockEntity be, BlockState state) {
        if (CFPartialModels.FLUID_ATOMIZER_SHAFT == null) {
            return null;
        }
        BlockState actualState = be.getBlockState();
        return CachedBuffers.partialFacing(CFPartialModels.FLUID_ATOMIZER_SHAFT, actualState, actualState.getValue(com.adonis.fluid.block.FluidAtomizer.FluidAtomizerBlock.FACING).getOpposite());
    }

    @Override
    protected BlockState getRenderedBlockState(FluidAtomizerBlockEntity be) {
        return shaft(be.getBlockState().getValue(com.adonis.fluid.block.FluidAtomizer.FluidAtomizerBlock.FACING).getAxis());
    }

    private void renderFluid(FluidAtomizerBlockEntity be, float partialTicks, PoseStack ms,
                             MultiBufferSource buffer, int light) {
        SmartFluidTankBehaviour tank = be.tankBehaviour;
        if (tank == null) {
            return;
        }

        SmartFluidTankBehaviour.TankSegment primaryTank = tank.getPrimaryTank();
        if (primaryTank == null) {
            return;
        }

        FluidStack renderedFluid = primaryTank.getRenderedFluid();
        float level = primaryTank.getFluidLevel().getValue(partialTicks);

        if (renderedFluid.isEmpty()) {
            renderedFluid = be.getFluid();
            if (!renderedFluid.isEmpty() && level <= 0) {
                level = (float) renderedFluid.getAmount() / FluidAtomizerBlockEntity.CAPACITY;
            }
        }

        if (renderedFluid.isEmpty() || level <= 0) {
            return;
        }

        IClientFluidTypeExtensions props = IClientFluidTypeExtensions.of(renderedFluid.getFluid());
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(props.getStillTexture(renderedFluid));
        int color = props.getTintColor(renderedFluid);
        VertexConsumer vb = buffer.getBuffer(RenderType.translucent());

        // 调整光照以考虑流体自发光
        int blockLightIn = (light >> 4) & 0xF;
        int luminosity = Math.max(blockLightIn, renderedFluid.getFluid().getFluidType().getLightLevel(renderedFluid));
        light = (light & 0xF00000) | luminosity << 4;

        float x1 = 2.1f / 16f;
        float x2 = 13.9f / 16f;
        float z1 = 2.1f / 16f;
        float z2 = 13.9f / 16f;
        float yBot = 2.1f / 16f;
        float yTop = yBot + (11.8f / 16f) * Math.min(level, 1f);

        ms.pushPose();
        FluidRenderHelper.renderStillTiledFace(Direction.UP, x1, z1, x2, z2, yTop, vb, ms, light, color, sprite);
        FluidRenderHelper.renderStillTiledFace(Direction.NORTH, x1, yBot, x2, yTop, z1, vb, ms, light, color, sprite);
        FluidRenderHelper.renderStillTiledFace(Direction.SOUTH, x1, yBot, x2, yTop, z2, vb, ms, light, color, sprite);
        FluidRenderHelper.renderStillTiledFace(Direction.WEST, z1, yBot, z2, yTop, x1, vb, ms, light, color, sprite);
        FluidRenderHelper.renderStillTiledFace(Direction.EAST, z1, yBot, z2, yTop, x2, vb, ms, light, color, sprite);
        ms.popPose();
    }
}
