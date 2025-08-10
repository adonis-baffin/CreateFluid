package com.adonis.fluid.block.Pipette;

import com.google.common.collect.Lists;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.AbstractInstance;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.engine_room.flywheel.lib.util.RecyclingPoseStack;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import java.util.ArrayList;
import java.util.function.Consumer;
import net.createmod.catnip.data.Iterate;
import net.minecraft.util.Mth;
import net.minecraftforge.fluids.FluidStack;

public class PipetteVisual extends SingleAxisRotatingVisual<PipetteBlockEntity> implements SimpleDynamicVisual {
    final TransformedInstance base;
    final TransformedInstance lowerBody;
    final TransformedInstance upperBody;
    final TransformedInstance claw;
    private final ArrayList<TransformedInstance> clawGrips;
    private final ArrayList<TransformedInstance> models;
    private final boolean ceiling;
    private final RecyclingPoseStack poseStack = new RecyclingPoseStack();
    private float baseAngle = Float.NaN;
    private float lowerArmAngle = Float.NaN;
    private float upperArmAngle = Float.NaN;
    private float headAngle = Float.NaN;

    public PipetteVisual(VisualizationContext context, PipetteBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick, Models.partial(AllPartialModels.ARM_COG));

        this.base = (TransformedInstance)this.instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(AllPartialModels.ARM_BASE)).createInstance();
        this.lowerBody = (TransformedInstance)this.instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(AllPartialModels.ARM_LOWER_BODY)).createInstance();
        this.upperBody = (TransformedInstance)this.instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(AllPartialModels.ARM_UPPER_BODY)).createInstance();
        this.claw = (TransformedInstance)this.instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(blockEntity.goggles ? AllPartialModels.ARM_CLAW_BASE_GOGGLES : AllPartialModels.ARM_CLAW_BASE)).createInstance();

        TransformedInstance clawGrip1 = (TransformedInstance)this.instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(AllPartialModels.ARM_CLAW_GRIP_UPPER)).createInstance();
        TransformedInstance clawGrip2 = (TransformedInstance)this.instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(AllPartialModels.ARM_CLAW_GRIP_LOWER)).createInstance();
        this.clawGrips = Lists.newArrayList(new TransformedInstance[]{clawGrip1, clawGrip2});
        this.models = Lists.newArrayList(new TransformedInstance[]{this.base, this.lowerBody, this.upperBody, this.claw, clawGrip1, clawGrip2});

        this.ceiling = (Boolean)this.blockState.getValue(PipetteBlock.CEILING);
        PoseTransformStack msr = TransformStack.of(this.poseStack);
        msr.translate(this.getVisualPosition());
        msr.center();
        if (this.ceiling) {
            msr.rotateXDegrees(180.0F);
        }

        this.animate(partialTick);
    }

    public void beginFrame(DynamicVisual.Context ctx) {
        this.animate(ctx.partialTick());
    }

    private void animate(float pt) {
        // 只使用正常动画，移除跳舞功能
        float baseAngleNow = ((PipetteBlockEntity)this.blockEntity).baseAngle.getValue(pt);
        float lowerArmAngleNow = ((PipetteBlockEntity)this.blockEntity).lowerArmAngle.getValue(pt);
        float upperArmAngleNow = ((PipetteBlockEntity)this.blockEntity).upperArmAngle.getValue(pt);
        float headAngleNow = ((PipetteBlockEntity)this.blockEntity).headAngle.getValue(pt);

        boolean settled = Mth.equal(this.baseAngle, baseAngleNow) && Mth.equal(this.lowerArmAngle, lowerArmAngleNow)
                && Mth.equal(this.upperArmAngle, upperArmAngleNow) && Mth.equal(this.headAngle, headAngleNow);

        this.baseAngle = baseAngleNow;
        this.lowerArmAngle = lowerArmAngleNow;
        this.upperArmAngle = upperArmAngleNow;
        this.headAngle = headAngleNow;

        if (!settled) {
            this.animateArm();
        }
    }

    private void animateArm() {
        this.updateAngles(this.baseAngle, this.lowerArmAngle - 135.0F, this.upperArmAngle - 90.0F, this.headAngle, 16777215);
    }

    private void updateAngles(float baseAngle, float lowerArmAngle, float upperArmAngle, float headAngle, int color) {
        this.poseStack.pushPose();
        PoseTransformStack msr = TransformStack.of(this.poseStack);

        PipetteRenderer.transformBase(msr, baseAngle);
        this.base.setTransform(this.poseStack).setChanged();

        PipetteRenderer.transformLowerArm(msr, lowerArmAngle);
        this.lowerBody.setTransform(this.poseStack).colorRgb(color).setChanged();

        PipetteRenderer.transformUpperArm(msr, upperArmAngle);
        this.upperBody.setTransform(this.poseStack).colorRgb(color).setChanged();

        PipetteRenderer.transformHead(msr, headAngle);
        if (this.ceiling && ((PipetteBlockEntity)this.blockEntity).goggles) {
            msr.rotateZDegrees(180.0F);
        }
        this.claw.setTransform(this.poseStack).setChanged();

        if (this.ceiling && ((PipetteBlockEntity)this.blockEntity).goggles) {
            msr.rotateZDegrees(180.0F);
        }

        FluidStack fluid = ((PipetteBlockEntity)this.blockEntity).heldFluid;
        boolean hasFluid = !fluid.isEmpty();

        // 针头部分 - 保持固定状态
        int[] indices = Iterate.zeroAndOne;
        for(int index : indices) {
            this.poseStack.pushPose();
            int flip = index * 2 - 1;
            PipetteRenderer.transformClawHalf(msr, hasFluid, flip);
            ((TransformedInstance)this.clawGrips.get(index)).setTransform(this.poseStack).setChanged();
            this.poseStack.popPose();
        }

        this.poseStack.popPose();
    }

    public void update(float pt) {
        super.update(pt);
        this.instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(((PipetteBlockEntity)this.blockEntity).goggles ? AllPartialModels.ARM_CLAW_BASE_GOGGLES : AllPartialModels.ARM_CLAW_BASE)).stealInstance(this.claw);
    }

    public void updateLight(float partialTick) {
        super.updateLight(partialTick);
        this.relight((FlatLit[])this.models.toArray(new FlatLit[0]));
    }

    protected void _delete() {
        super._delete();
        this.models.forEach(AbstractInstance::delete);
    }

    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        super.collectCrumblingInstances(consumer);
        this.models.forEach(consumer);
    }
}