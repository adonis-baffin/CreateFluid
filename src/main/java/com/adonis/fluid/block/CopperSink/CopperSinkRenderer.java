package com.adonis.fluid.block.CopperSink;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.createmod.catnip.render.FluidRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
public class CopperSinkRenderer extends SmartBlockEntityRenderer<CopperSinkBlockEntity> {
    public CopperSinkRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }
    @Override
    protected void renderSafe(CopperSinkBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        FluidStack fluid = new FluidStack(Fluids.WATER, be.getTank().getFluidAmount());
        if (fluid.isEmpty()) return;
        float level = fluid.getAmount() / 2000f;
        float yMin = 1.9f / 16f;
        float yMax = 13.1f / 16f;
        float topY = yMin + (yMax - yMin) * level;
        IClientFluidTypeExtensions props = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(props.getStillTexture(fluid));
        int color = props.getTintColor(fluid);
        VertexConsumer vb = buffer.getBuffer(RenderType.translucent());
        // 关键修复：留 0.01 的微小间隙，彻底消除 Z-Fighting 重影
        float x1 = 2.01f/16f, x2 = 13.99f/16f;
        float z1 = 2.01f/16f, z2 = 13.99f/16f;
        float yBot = 3.01f/16f;
        ms.pushPose();
        // 顶面
        FluidRenderHelper.renderStillTiledFace(net.minecraft.core.Direction.UP,
                x1, z1, x2, z2, topY, vb, ms, light, color, sprite);
        // 四侧面
        renderSide(vb, ms, x1, topY, z1, x2, topY, z1, x2, yBot, z1, x1, yBot, z1, sprite, color, light); // 北
        renderSide(vb, ms, x2, topY, z2, x1, topY, z2, x1, yBot, z2, x2, yBot, z2, sprite, color, light); // 南
        renderSide(vb, ms, x1, topY, z1, x1, topY, z2, x1, yBot, z2, x1, yBot, z1, sprite, color, light); // 西
        renderSide(vb, ms, x2, topY, z2, x2, topY, z1, x2, yBot, z1, x2, yBot, z2, sprite, color, light); // 东
        ms.popPose();
    }
    private void renderSide(VertexConsumer vb, PoseStack ms,
                            float x1, float y1, float z1, float x2, float y2, float z2,
                            float x3, float y3, float z3, float x4, float y4, float z4,
                            TextureAtlasSprite sprite, int color, int light) {
        float r = ((color >> 16) & 255) / 255f * 0.8f;
        float g = ((color >> 8) & 255) / 255f * 0.8f;
        float b = (color & 255) / 255f * 0.8f;
        float a = ((color >> 24) & 255) / 255f;
        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();
        var pose = ms.last();
        vb.vertex(pose.pose(), x1, y1, z1).color(r, g, b, a).uv(u0, v0).uv2(light).normal(pose.normal(), 0, 1, 0).endVertex();
        vb.vertex(pose.pose(), x2, y2, z2).color(r, g, b, a).uv(u1, v0).uv2(light).normal(pose.normal(), 0, 1, 0).endVertex();
        vb.vertex(pose.pose(), x3, y3, z3).color(r, g, b, a).uv(u1, v1).uv2(light).normal(pose.normal(), 0, 1, 0).endVertex();
        vb.vertex(pose.pose(), x4, y4, z4).color(r, g, b, a).uv(u0, v1).uv2(light).normal(pose.normal(), 0, 1, 0).endVertex();
    }
}