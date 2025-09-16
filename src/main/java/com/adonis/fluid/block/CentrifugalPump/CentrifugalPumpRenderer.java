package com.adonis.fluid.block.CentrifugalPump;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

public class CentrifugalPumpRenderer extends KineticBlockEntityRenderer<CentrifugalPumpBlockEntity> {

    public CentrifugalPumpRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(CentrifugalPumpBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        if (!VisualizationManager.supportsVisualization(be.getLevel())) {
            BlockState blockState = be.getBlockState();
            if (!(blockState.getBlock() instanceof CentrifugalPumpBlock)) {
                return;
            }

            Direction shaftDirection = CentrifugalPumpBlock.getShaftDirection(blockState);
            AttachFace face = blockState.getValue(CentrifugalPumpBlock.FACE);

            // 获取轴后面的光照
            int lightBehind = LevelRenderer.getLightColor(
                    be.getLevel(),
                    be.getBlockPos().relative(shaftDirection)  // 修正：使用轴的方向，而不是反方向
            );

            VertexConsumer vb = buffer.getBuffer(RenderType.cutoutMipped());
            SuperByteBuffer shaftHalf;

            if (face == AttachFace.WALL) {
                // 垂直模式：轴从顶部向上伸出
                shaftHalf = CachedBuffers.partialFacing(
                        AllPartialModels.SHAFT_HALF,
                        blockState,
                        Direction.DOWN  // 改回DOWN，显示另外半根
                );
            } else if (face == AttachFace.FLOOR) {
                // 地面模式：轴从back_for_rotate伸出（facing的反方向）
                shaftHalf = CachedBuffers.partialFacing(
                        AllPartialModels.SHAFT_HALF,
                        blockState,
                        shaftDirection.getOpposite()  // 使用反方向，显示另外半根
                );
            } else { // CEILING
                // 天花板模式：轴从back_for_rotate伸出（facing的反方向）
                shaftHalf = CachedBuffers.partialFacing(
                        AllPartialModels.SHAFT_HALF,
                        blockState,
                        shaftDirection.getOpposite()  // CEILING保持不变，因为它是正确的
                );
            }

            standardKineticRotationTransform(shaftHalf, be, lightBehind)
                    .renderInto(ms, vb);
        }
    }
}