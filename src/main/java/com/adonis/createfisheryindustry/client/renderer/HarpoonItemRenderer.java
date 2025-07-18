//package com.adonis.createfisheryindustry.client.renderer;
//
//import com.adonis.createfisheryindustry.client.model.HarpoonModel;
//import com.mojang.blaze3d.vertex.PoseStack;
//import com.mojang.blaze3d.vertex.VertexConsumer;
//import net.minecraft.client.Minecraft;
//import net.minecraft.client.model.geom.EntityModelSet;
//import net.minecraft.client.model.geom.ModelLayerLocation;
//import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
//import net.minecraft.client.renderer.MultiBufferSource;
//import net.minecraft.client.renderer.entity.ItemRenderer;
//import net.minecraft.client.resources.model.BakedModel;
//import net.minecraft.resources.ResourceLocation;
//import net.minecraft.world.item.ItemDisplayContext;
//import net.minecraft.world.item.ItemStack;
//import net.minecraftforge.api.distmarker.Dist;
//import net.minecraftforge.api.distmarker.OnlyIn;
//
//@OnlyIn(Dist.CLIENT)
//public class HarpoonItemRenderer extends BlockEntityWithoutLevelRenderer {
//    public static final ModelLayerLocation HARPOON_LAYER = new ModelLayerLocation(
//            new ResourceLocation("createfisheryindustry", "harpoon"), "main");
//
//    private HarpoonModel harpoonModel;
//
//    public HarpoonItemRenderer() {
//        super(null, null);
//    }
//
//    @Override
//    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
//                           MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
//
//        if (this.harpoonModel == null) {
//            EntityModelSet entityModels = Minecraft.getInstance().getEntityModels();
//            this.harpoonModel = new HarpoonModel(entityModels.bakeLayer(HARPOON_LAYER));
//        }
//
//        poseStack.pushPose();
//
//        // 根据显示上下文调整变换
//        switch (displayContext) {
//            case THIRD_PERSON_LEFT_HAND:
//            case THIRD_PERSON_RIGHT_HAND:
//                poseStack.scale(1.0F, 1.0F, 1.0F);
//                poseStack.translate(0.5, 0.5, 0.5);
//                break;
//            case FIRST_PERSON_LEFT_HAND:
//            case FIRST_PERSON_RIGHT_HAND:
//                poseStack.scale(1.0F, 1.0F, 1.0F);
//                poseStack.translate(0.5, 0.5, 0.5);
//                break;
//            case GUI:
//                poseStack.scale(0.625F, 0.625F, 0.625F);
//                poseStack.translate(0.5, 0.5, 0.5);
//                break;
//            case GROUND:
//                poseStack.scale(0.25F, 0.25F, 0.25F);
//                poseStack.translate(2.0, 2.0, 2.0);
//                break;
//            case FIXED:
//                poseStack.scale(0.5F, 0.5F, 0.5F);
//                poseStack.translate(1.0, 1.0, 1.0);
//                break;
//            default:
//                break;
//        }
//
//        VertexConsumer vertexConsumer = ItemRenderer.getFoilBufferDirect(bufferSource,
//                this.harpoonModel.renderType(HarpoonModel.TEXTURE), false, stack.hasFoil());
//
//        this.harpoonModel.renderToBuffer(poseStack, vertexConsumer, packedLight, packedOverlay,
//                1.0F, 1.0F, 1.0F, 1.0F);
//
//        poseStack.popPose();
//    }
//}