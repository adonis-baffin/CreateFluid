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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class HarpoonISTER extends BlockEntityWithoutLevelRenderer {

    public static final ResourceLocation HARPOON_TEXTURE = new ResourceLocation(CreateFisheryMod.MODID, "textures/entity/harpoon.png");
    public static final HarpoonISTER RENDERER = new HarpoonISTER(
            Minecraft.getInstance().getBlockEntityRenderDispatcher(),
            Minecraft.getInstance().getEntityModels()
    );

    private final EntityModelSet modelSet;
    private TridentModel tridentModel;

    public HarpoonISTER(BlockEntityRenderDispatcher renderDispatcher, EntityModelSet modelSet) {
        super(renderDispatcher, modelSet);
        this.modelSet = modelSet;
    }

    @Override
    public void onResourceManagerReload(@NotNull ResourceManager resourceManager) {
        this.tridentModel = new TridentModel(modelSet.bakeLayer(ModelLayers.TRIDENT));
    }

    @Override
    public void renderByItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext displayContext,
                             @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer,
                             int combinedLight, int combinedOverlay) {
        poseStack.pushPose();
        poseStack.scale(1.0F, -1.0F, -1.0F);

        // 确保模型已初始化，如果没有则创建一个
        if (this.tridentModel == null) {
            this.tridentModel = new TridentModel(modelSet.bakeLayer(ModelLayers.TRIDENT));
        }

        VertexConsumer vertexConsumer = ItemRenderer.getFoilBufferDirect(
                buffer,
                tridentModel.renderType(HARPOON_TEXTURE),
                false,
                stack.hasFoil()
        );
        tridentModel.renderToBuffer(poseStack, vertexConsumer, combinedLight, combinedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }
}