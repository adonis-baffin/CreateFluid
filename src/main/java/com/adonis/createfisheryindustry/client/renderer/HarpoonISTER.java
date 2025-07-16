package com.adonis.createfisheryindustry.client.renderer;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.TridentModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;

public class HarpoonISTER extends BlockEntityWithoutLevelRenderer {
    public static final HarpoonISTER RENDERER = new HarpoonISTER(
            Minecraft.getInstance().getBlockEntityRenderDispatcher(),
            Minecraft.getInstance().getEntityModels()
    );

    private static final ResourceLocation HARPOON_TEXTURE = new ResourceLocation(CreateFisheryMod.MODID, "textures/entity/harpoon.png");

    private final EntityModelSet modelSet;
    private TridentModel tridentModel;

    public HarpoonISTER(BlockEntityRenderDispatcher renderDispatcher, EntityModelSet modelSet) {
        super(renderDispatcher, modelSet);
        this.modelSet = modelSet;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        this.tridentModel = new TridentModel(modelSet.bakeLayer(ModelLayers.TRIDENT));
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack matrix, MultiBufferSource renderer,
                             int light, int overlayLight) {
        matrix.pushPose();
        matrix.scale(1, -1, -1);
        VertexConsumer builder = ItemRenderer.getFoilBufferDirect(renderer, tridentModel.renderType(getTexture(stack)), false, stack.hasFoil());
        // 1.20.1需要8个参数：PoseStack, VertexConsumer, light, overlay, red, green, blue, alpha
        tridentModel.renderToBuffer(matrix, builder, light, overlayLight, 1.0F, 1.0F, 1.0F, 1.0F);
        matrix.popPose();
    }

    private static ResourceLocation getTexture(ItemStack stack) {
        return HARPOON_TEXTURE;
    }
}