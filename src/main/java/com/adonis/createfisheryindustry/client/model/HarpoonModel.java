//package com.adonis.createfisheryindustry.client.model;
//
//import com.mojang.blaze3d.vertex.PoseStack;
//import com.mojang.blaze3d.vertex.VertexConsumer;
//import net.minecraft.client.model.Model;
//import net.minecraft.client.model.geom.ModelPart;
//import net.minecraft.client.model.geom.PartPose;
//import net.minecraft.client.model.geom.builders.CubeListBuilder;
//import net.minecraft.client.model.geom.builders.LayerDefinition;
//import net.minecraft.client.model.geom.builders.MeshDefinition;
//import net.minecraft.client.model.geom.builders.PartDefinition;
//import net.minecraft.client.renderer.RenderType;
//import net.minecraft.resources.ResourceLocation;
//import net.minecraftforge.api.distmarker.Dist;
//import net.minecraftforge.api.distmarker.OnlyIn;
//
//@OnlyIn(Dist.CLIENT)
//public class HarpoonModel extends Model {
//    public static final ResourceLocation TEXTURE = new ResourceLocation("createfisheryindustry", "textures/entity/harpoon.png");
//    private final ModelPart root;
//
//    public HarpoonModel(ModelPart pRoot) {
//        super(RenderType::entitySolid);
//        this.root = pRoot;
//    }
//
//    public static LayerDefinition createLayer() {
//        MeshDefinition meshDefinition = new MeshDefinition();
//        PartDefinition partDefinition = meshDefinition.getRoot();
//
//        // 创建鱼叉的主要部分
//        PartDefinition pole = partDefinition.addOrReplaceChild("pole",
//            CubeListBuilder.create().texOffs(0, 6).addBox(-0.5F, 2.0F, -0.5F, 1.0F, 25.0F, 1.0F),
//            PartPose.ZERO);
//
//        // 鱼叉的底座
//        pole.addOrReplaceChild("base",
//            CubeListBuilder.create().texOffs(4, 0).addBox(-1.5F, 0.0F, -0.5F, 3.0F, 2.0F, 1.0F),
//            PartPose.ZERO);
//
//        // 左边的尖刺
//        pole.addOrReplaceChild("left_spike",
//            CubeListBuilder.create().texOffs(4, 3).addBox(-2.5F, -3.0F, -0.5F, 1.0F, 4.0F, 1.0F),
//            PartPose.ZERO);
//
//        // 中间的尖刺
//        pole.addOrReplaceChild("middle_spike",
//            CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
//            PartPose.ZERO);
//
//        // 右边的尖刺
//        pole.addOrReplaceChild("right_spike",
//            CubeListBuilder.create().texOffs(4, 3).mirror().addBox(1.5F, -3.0F, -0.5F, 1.0F, 4.0F, 1.0F),
//            PartPose.ZERO);
//
//        return LayerDefinition.create(meshDefinition, 32, 32);
//    }
//
//    @Override
//    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
//        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
//    }
//}