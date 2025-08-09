//package com.adonis.fluid.block.Pipette;
//
//import com.adonis.fluid.registry.CFPartialModels;
//import com.google.common.collect.Lists;
//import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
//import dev.engine_room.flywheel.api.instance.Instance;
//import dev.engine_room.flywheel.api.visual.DynamicVisual;
//import dev.engine_room.flywheel.api.visualization.VisualizationContext;
//import dev.engine_room.flywheel.lib.instance.AbstractInstance;
//import dev.engine_room.flywheel.lib.instance.FlatLit;
//import dev.engine_room.flywheel.lib.instance.InstanceTypes;
//import dev.engine_room.flywheel.lib.instance.TransformedInstance;
//import dev.engine_room.flywheel.lib.model.Models;
//import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
//import dev.engine_room.flywheel.lib.transform.TransformStack;
//import dev.engine_room.flywheel.lib.util.RecyclingPoseStack;
//import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
//import net.createmod.catnip.animation.AnimationTickHolder;
//import net.createmod.catnip.theme.Color;
//import net.minecraft.client.Minecraft;
//import net.minecraft.client.renderer.entity.ItemRenderer;
//import net.minecraft.util.Mth;
//import net.minecraft.world.entity.LivingEntity;
//import net.minecraft.world.item.BlockItem;
//import net.minecraft.world.item.ItemStack;
//
//import java.util.ArrayList;
//import java.util.function.Consumer;
//
//public class PipetteVisual extends SingleAxisRotatingVisual<PipetteBlockEntity> implements SimpleDynamicVisual {
//    final TransformedInstance base;
//    final TransformedInstance lowerArm;
//    final TransformedInstance upperArm;
//    final TransformedInstance head;
//    final TransformedInstance tip;
//    final TransformedInstance needle;
//    private final ArrayList<TransformedInstance> models;
//    private final boolean ceiling;
//    private final RecyclingPoseStack poseStack = new RecyclingPoseStack();
//
//    private boolean wasWorking = false;
//    private float baseAngle = Float.NaN;
//    private float lowerArmAngle = Float.NaN;
//    private float upperArmAngle = Float.NaN;
//    private float headAngle = Float.NaN;
//
//    // 修正构造函数 - 使用正确的父类构造函数
//    public PipetteVisual(VisualizationContext context, PipetteBlockEntity blockEntity) {
//        super(context, blockEntity, Models.partial(CFPartialModels.PIPETTE_COG));
//
//        this.base = (TransformedInstance)this.instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(CFPartialModels.PIPETTE_BASE)).createInstance();
//        this.lowerArm = (TransformedInstance)this.instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(CFPartialModels.PIPETTE_LOWER_ARM)).createInstance();
//        this.upperArm = (TransformedInstance)this.instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(CFPartialModels.PIPETTE_UPPER_ARM)).createInstance();
//        this.head = (TransformedInstance)this.instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(blockEntity.goggles ? CFPartialModels.PIPETTE_HEAD_GOGGLES : CFPartialModels.PIPETTE_HEAD)).createInstance();
//        this.tip = (TransformedInstance)this.instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(CFPartialModels.PIPETTE_TIP)).createInstance();
//        this.needle = (TransformedInstance)this.instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(CFPartialModels.PIPETTE_NEEDLE)).createInstance();
//
//        this.models = Lists.newArrayList(this.base, this.lowerArm, this.upperArm, this.head, this.tip, this.needle);
//        this.ceiling = this.blockState.getValue(PipetteBlock.CEILING);
//
//        PoseTransformStack msr = TransformStack.of(this.poseStack);
//        msr.translate(this.getVisualPosition());
//        msr.center();
//        if (this.ceiling) {
//            msr.rotateXDegrees(180.0F);
//        }
//
//        this.animate(0.0F); // 初始化动画
//    }
//
//    @Override
//    public void beginFrame(DynamicVisual.Context ctx) {
//        this.animate(ctx.partialTick());
//    }
//
//    private void animate(float pt) {
//        if (((PipetteBlockEntity)this.blockEntity).phase == PipetteBlockEntity.Phase.SEARCH_INPUTS && ((PipetteBlockEntity)this.blockEntity).getSpeed() != 0.0F) {
//            this.animateRave(pt);
//            this.wasWorking = true;
//        } else {
//            float baseAngleNow = ((PipetteBlockEntity)this.blockEntity).baseAngle.getValue(pt);
//            float lowerArmAngleNow = ((PipetteBlockEntity)this.blockEntity).lowerArmAngle.getValue(pt);
//            float upperArmAngleNow = ((PipetteBlockEntity)this.blockEntity).upperArmAngle.getValue(pt);
//            float headAngleNow = ((PipetteBlockEntity)this.blockEntity).headAngle.getValue(pt);
//
//            boolean settled = Mth.equal(this.baseAngle, baseAngleNow) &&
//                    Mth.equal(this.lowerArmAngle, lowerArmAngleNow) &&
//                    Mth.equal(this.upperArmAngle, upperArmAngleNow) &&
//                    Mth.equal(this.headAngle, headAngleNow);
//
//            this.baseAngle = baseAngleNow;
//            this.lowerArmAngle = lowerArmAngleNow;
//            this.upperArmAngle = upperArmAngleNow;
//            this.headAngle = headAngleNow;
//
//            if (!settled || this.wasWorking) {
//                this.animatePipette();
//            }
//
//            this.wasWorking = false;
//        }
//    }
//
//    private void animateRave(float partialTick) {
//        int ticks = AnimationTickHolder.getTicks(((PipetteBlockEntity)this.blockEntity).getLevel());
//        float renderTick = (float)ticks + partialTick + (float)(((PipetteBlockEntity)this.blockEntity).hashCode() % 64);
//        float baseAngle = renderTick * 8.0F % 360.0F;
//        float lowerArmAngle = Mth.lerp((Mth.sin(renderTick / 6.0F) + 1.0F) / 2.0F, -30.0F, 30.0F);
//        float upperArmAngle = Mth.lerp((Mth.sin(renderTick / 10.0F) + 1.0F) / 4.0F, -60.0F, 60.0F);
//        float headAngle = -lowerArmAngle * 0.5F;
//        int color = Color.rainbowColor(ticks * 100).getRGB();
//        this.updateAngles(baseAngle, lowerArmAngle, upperArmAngle, headAngle, color);
//    }
//
//    private void animatePipette() {
//        this.updateAngles(this.baseAngle, this.lowerArmAngle - 135.0F, this.upperArmAngle - 90.0F, this.headAngle, 0xFFFFFF);
//    }
//
//    private void updateAngles(float baseAngle, float lowerArmAngle, float upperArmAngle, float headAngle, int color) {
//        this.poseStack.pushPose();
//        PoseTransformStack msr = TransformStack.of(this.poseStack);
//
//        PipetteRenderer.transformBase(msr, baseAngle);
//        this.base.setTransform(this.poseStack).setChanged();
//
//        PipetteRenderer.transformLowerArm(msr, lowerArmAngle);
//        this.lowerArm.setTransform(this.poseStack).colorRgb(color).setChanged();
//
//        PipetteRenderer.transformUpperArm(msr, upperArmAngle);
//        this.upperArm.setTransform(this.poseStack).colorRgb(color).setChanged();
//
//        PipetteRenderer.transformHead(msr, headAngle);
//        if (this.ceiling && ((PipetteBlockEntity)this.blockEntity).goggles) {
//            msr.rotateZDegrees(180.0F);
//        }
//        this.head.setTransform(this.poseStack).setChanged();
//
//        ItemStack item = ((PipetteBlockEntity)this.blockEntity).heldItem;
//        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
//        boolean hasItem = !item.isEmpty();
//        boolean isBlockItem = hasItem && item.getItem() instanceof BlockItem &&
//                itemRenderer.getModel(item, Minecraft.getInstance().level, (LivingEntity)null, 0).isGui3d();
//
//        PipetteRenderer.transformTip(msr, hasItem, isBlockItem);
//        this.tip.setTransform(this.poseStack).setChanged();
//
//        PipetteRenderer.transformNeedle(msr, hasItem, isBlockItem);
//        this.needle.setTransform(this.poseStack).setChanged();
//
//        if (this.ceiling && ((PipetteBlockEntity)this.blockEntity).goggles) {
//            msr.rotateZDegrees(180.0F);
//        }
//
//        this.poseStack.popPose();
//    }
//
//    @Override
//    public void update(float pt) {
//        super.update(pt);
//        this.instancerProvider().instancer(InstanceTypes.TRANSFORMED,
//                Models.partial(((PipetteBlockEntity)this.blockEntity).goggles ?
//                        CFPartialModels.PIPETTE_HEAD_GOGGLES : CFPartialModels.PIPETTE_HEAD)).stealInstance(this.head);
//    }
//
//    @Override
//    public void updateLight(float partialTick) {
//        super.updateLight(partialTick);
//        this.relight(this.models.toArray(new FlatLit[0]));
//    }
//
//    @Override
//    protected void _delete() {
//        super._delete();
//        this.models.forEach(AbstractInstance::delete);
//    }
//
//    @Override
//    public void collectCrumblingInstances(Consumer<Instance> consumer) {
//        super.collectCrumblingInstances(consumer);
//        this.models.forEach(consumer);
//    }
//}