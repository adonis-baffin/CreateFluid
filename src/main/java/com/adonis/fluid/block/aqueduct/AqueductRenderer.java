package com.adonis.fluid.block.aqueduct;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import com.simibubi.create.foundation.fluid.FluidRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;

public class AqueductRenderer extends SmartBlockEntityRenderer<AbstractAqueductBlockEntity> {

    public AqueductRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(AbstractAqueductBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        FluidStack fluidStack = be.getFluid();
        if (fluidStack.isEmpty()) return;

        float fluidLevel = be.getFluidLevel();
        if (fluidLevel <= 0) return;

        BlockState state = be.getBlockState();
        Direction facing = state.getValue(AbstractAqueductBlock.FACING);

        // 渲染流体
        renderFluid(be, fluidStack, fluidLevel, facing, ms, buffer, light);

        // 渲染流向箭头（如果需要）
        if (state.getValue(AbstractAqueductBlock.LOCKED)) {
            renderLockedIndicator(be, ms, buffer, light);
        }
    }

    private void renderFluid(AbstractAqueductBlockEntity be, FluidStack fluidStack, float level,
                             Direction facing, PoseStack ms, MultiBufferSource buffer, int light) {
        ms.pushPose();

        // 计算流体高度
        float height = 3f/16f + (7f/16f * level); // 从底部3像素到10像素高度

        // 使用FluidRenderer的renderFluidStream方法代替renderFluidBox
        // 或者自定义流体渲染
        float radius = 6f/16f; // 流体宽度
        FluidRenderer.renderFluidStream(fluidStack, Direction.UP, radius, level, false, buffer, ms, light);

        ms.popPose();
    }

    private void renderLockedIndicator(AbstractAqueductBlockEntity be, PoseStack ms,
                                       MultiBufferSource buffer, int light) {
        // 渲染红石锁定指示器
        ms.pushPose();

        // 在水渠侧面渲染一个小的红石指示器
        VertexConsumer vc = buffer.getBuffer(RenderType.solid());
        // 渲染代码...

        ms.popPose();
    }
}