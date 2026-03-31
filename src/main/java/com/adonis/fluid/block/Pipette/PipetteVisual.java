package com.adonis.fluid.block.Pipette;

import com.adonis.fluid.registry.CFPartialModels;
import com.google.common.collect.Lists;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmRenderer;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.engine_room.flywheel.lib.util.RecyclingPoseStack;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.createmod.catnip.data.Iterate;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;

public class PipetteVisual extends SingleAxisRotatingVisual<PipetteBlockEntity> 
        implements SimpleDynamicVisual {

    final TransformedInstance base;
    final TransformedInstance lowerBody;
    final TransformedInstance upperBody;
    final TransformedInstance head;
    private final ArrayList<TransformedInstance> models;
    private final boolean ceiling;
    private final RecyclingPoseStack poseStack = new RecyclingPoseStack();

    private float baseAngle = Float.NaN;
    private float lowerArmAngle = Float.NaN;
    private float upperArmAngle = Float.NaN;
    private float headAngle = Float.NaN;

    public PipetteVisual(VisualizationContext context, PipetteBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick, Models.partial(getRotatingModel()));

        this.base = createSafeInstance(CFPartialModels.PIPETTE_BASE, AllPartialModels.ARM_BASE);
        this.lowerBody = createSafeInstance(CFPartialModels.PIPETTE_LOWER_ARM, AllPartialModels.ARM_LOWER_BODY);
        this.upperBody = createSafeInstance(CFPartialModels.PIPETTE_UPPER_ARM, AllPartialModels.ARM_UPPER_BODY);

        // 根据流体量选择头部模型
        int fluidAmount = blockEntity.heldFluid.isEmpty() ? 0 : blockEntity.heldFluid.getAmount();
        PartialModel headModel = CFPartialModels.getPipetteHeadForFluidAmount(fluidAmount);
        this.head = createSafeInstance(headModel, AllPartialModels.ARM_CLAW_BASE);

        this.models = Lists.newArrayList(this.base, this.lowerBody, this.upperBody, this.head);

        this.ceiling = blockState.getValue(PipetteBlock.CEILING);
        PoseTransformStack msr = TransformStack.of(this.poseStack);
        msr.translate(this.getVisualPosition());
        msr.center();
        if (this.ceiling) {
            msr.rotateXDegrees(180.0F);
        }

        this.animate(partialTick);
    }

    private static PartialModel getRotatingModel() {
        try {
            if (CFPartialModels.PIPETTE_COG != null) {
                return CFPartialModels.PIPETTE_COG;
            }
        } catch (Exception e) {
            // 静默处理
        }
        return AllPartialModels.ARM_COG;
    }

    private TransformedInstance createSafeInstance(PartialModel model, PartialModel fallback) {
        PartialModel modelToUse = model != null ? model : fallback;

        try {
            return (TransformedInstance) this.instancerProvider()
                    .instancer(InstanceTypes.TRANSFORMED, Models.partial(modelToUse))
                    .createInstance();
        } catch (Exception e) {
            if (fallback != null && fallback != modelToUse) {
                try {
                    return (TransformedInstance) this.instancerProvider()
                            .instancer(InstanceTypes.TRANSFORMED, Models.partial(fallback))
                            .createInstance();
                } catch (Exception e2) {
                    // 静默处理
                }
            }
        }

        return null;
    }

    public void beginFrame(DynamicVisual.Context ctx) {
        try {
            this.animate(ctx.partialTick());
        } catch (Exception e) {
            // 静默处理
        }
    }

    private void animate(float pt) {
        try {
            float baseAngleNow = this.blockEntity.baseAngle.getValue(pt);
            float lowerArmAngleNow = this.blockEntity.lowerArmAngle.getValue(pt);
            float upperArmAngleNow = this.blockEntity.upperArmAngle.getValue(pt);
            float headAngleNow = this.blockEntity.headAngle.getValue(pt);

            boolean settled = Mth.equal(this.baseAngle, baseAngleNow)
                    && Mth.equal(this.lowerArmAngle, lowerArmAngleNow)
                    && Mth.equal(this.upperArmAngle, upperArmAngleNow)
                    && Mth.equal(this.headAngle, headAngleNow);

            this.baseAngle = baseAngleNow;
            this.lowerArmAngle = lowerArmAngleNow;
            this.upperArmAngle = upperArmAngleNow;
            this.headAngle = headAngleNow;

            if (!settled) {
                this.animateArm();
            }
        } catch (Exception e) {
            // 静默处理
        }
    }

    private void animateArm() {
        this.updateAngles(this.baseAngle, this.lowerArmAngle - 135.0F,
                this.upperArmAngle - 90.0F, this.headAngle, 0xFFFFFF);
    }

    private void updateAngles(float baseAngle, float lowerArmAngle, float upperArmAngle,
                              float headAngle, int color) {
        try {
            this.poseStack.pushPose();
            PoseTransformStack msr = TransformStack.of(this.poseStack);

            if (this.base != null) {
                ArmRenderer.transformBase(msr, baseAngle);
                this.base.setTransform(this.poseStack).setChanged();
            }

            if (this.lowerBody != null) {
                ArmRenderer.transformLowerArm(msr, lowerArmAngle);
                this.lowerBody.setTransform(this.poseStack).colorRgb(color).setChanged();
            }

            if (this.upperBody != null) {
                ArmRenderer.transformUpperArm(msr, upperArmAngle);
                this.upperBody.setTransform(this.poseStack).colorRgb(color).setChanged();
            }

            if (this.head != null) {
                ArmRenderer.transformHead(msr, headAngle);
                if (this.ceiling && this.blockEntity.goggles) {
                    msr.rotateZDegrees(180.0F);
                }
                this.head.setTransform(this.poseStack).setChanged();
            }

            this.poseStack.popPose();
        } catch (Exception e) {
            try {
                this.poseStack.popPose();
            } catch (Exception ignored) {}
        }
    }

    public void update(float pt) {
        try {
            super.update(pt);
        } catch (Exception e) {
            // 静默处理
        }
    }

    public void updateLight(float partialTick) {
        try {
            super.updateLight(partialTick);
            FlatLit[] litModels = this.models.stream()
                    .filter(Objects::nonNull)
                    .toArray(FlatLit[]::new);
            this.relight(litModels);
        } catch (Exception e) {
            // 静默处理
        }
    }

    protected void _delete() {
        try {
            super._delete();
            this.models.forEach(model -> {
                if (model != null) {
                    model.delete();
                }
            });
        } catch (Exception e) {
            // 静默处理
        }
    }

    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        try {
            super.collectCrumblingInstances(consumer);
            this.models.stream()
                    .filter(Objects::nonNull)
                    .forEach(consumer);
        } catch (Exception e) {
            // 静默处理
        }
    }
}