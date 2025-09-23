package com.adonis.fluid.block.Pipette;

import com.adonis.fluid.registry.CFPartialModels;
import com.adonis.fluid.render.PipetteFluidVisual;
import com.google.common.collect.Lists;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmRenderer;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.AbstractInstance;
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
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;

public class PipetteVisual extends SingleAxisRotatingVisual<PipetteBlockEntity> implements SimpleDynamicVisual {

    final TransformedInstance base;
    final TransformedInstance lowerBody;
    final TransformedInstance upperBody;
    final TransformedInstance claw;
    private final ArrayList<TransformedInstance> clawGrips;
    private final ArrayList<TransformedInstance> models;
    private final boolean ceiling;
    private final RecyclingPoseStack poseStack = new RecyclingPoseStack();

    // 流体渲染相关
    @Nullable
    private final PipetteFluidVisual fluidVisual;
    private TransformedInstance fluidInstance;
    private FluidStack lastFluid = FluidStack.EMPTY;

    private float baseAngle = Float.NaN;
    private float lowerArmAngle = Float.NaN;
    private float upperArmAngle = Float.NaN;
    private float headAngle = Float.NaN;

    public PipetteVisual(VisualizationContext context, PipetteBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick, Models.partial(getRotatingModel()));

        // 安全地创建各个部件的实例
        this.base = createSafeInstance(CFPartialModels.PIPETTE_BASE, AllPartialModels.ARM_BASE);
        this.lowerBody = createSafeInstance(CFPartialModels.PIPETTE_LOWER_ARM, AllPartialModels.ARM_LOWER_BODY);
        this.upperBody = createSafeInstance(CFPartialModels.PIPETTE_UPPER_ARM, AllPartialModels.ARM_UPPER_BODY);

        // 根据护目镜状态选择爪子模型
        PartialModel clawModel = blockEntity.goggles ?
                AllPartialModels.ARM_CLAW_BASE_GOGGLES : AllPartialModels.ARM_CLAW_BASE;
        this.claw = createSafeInstance(clawModel, AllPartialModels.ARM_CLAW_BASE);

        // 创建夹爪
        TransformedInstance clawGrip1 = createSafeInstance(
                AllPartialModels.ARM_CLAW_GRIP_UPPER,
                AllPartialModels.ARM_CLAW_GRIP_UPPER
        );
        TransformedInstance clawGrip2 = createSafeInstance(
                AllPartialModels.ARM_CLAW_GRIP_LOWER,
                AllPartialModels.ARM_CLAW_GRIP_LOWER
        );

        this.clawGrips = Lists.newArrayList(clawGrip1, clawGrip2);
        this.models = Lists.newArrayList(this.base, this.lowerBody, this.upperBody, this.claw, clawGrip1, clawGrip2);

        // 初始化流体渲染
        PipetteFluidVisual tempFluidVisual = null;
        try {
            tempFluidVisual = new PipetteFluidVisual(context);
        } catch (Exception e) {
            // 静默处理
        }
        this.fluidVisual = tempFluidVisual;

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
        PartialModel modelToUse = model;

        if (modelToUse == null) {
            modelToUse = fallback;
        }

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

        // 最后的尝试，使用最基础的模型
        try {
            return (TransformedInstance) this.instancerProvider()
                    .instancer(InstanceTypes.TRANSFORMED, Models.partial(AllPartialModels.ARM_BASE))
                    .createInstance();
        } catch (Exception e) {
            return null;
        }
    }

    public void beginFrame(DynamicVisual.Context ctx) {
        try {
            if (fluidVisual != null) {
                fluidVisual.begin();
            }
            this.animate(ctx.partialTick());
            if (fluidVisual != null) {
                fluidVisual.end();
            }
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

            // 更新流体渲染
            if (fluidVisual != null) {
                this.updateFluidRendering(pt);
            }
        } catch (Exception e) {
            // 静默处理
        }
    }

    private void updateFluidRendering(float pt) {
        if (fluidVisual == null) return;

        try {
            FluidStack currentFluid = this.blockEntity.heldFluid;

            // 检查流体是否发生变化
            if (!FluidStack.areFluidStackTagsEqual(currentFluid, lastFluid) ||
                    currentFluid.getAmount() != lastFluid.getAmount()) {

                // 删除旧的流体实例
                if (fluidInstance != null) {
                    fluidInstance.delete();
                    fluidInstance = null;
                }

                // 创建新的流体实例
                if (!currentFluid.isEmpty()) {
                    fluidInstance = fluidVisual.createFluidInstance(currentFluid);
                    if (fluidInstance != null) {
                        models.add(fluidInstance);
                    }
                }

                lastFluid = currentFluid.copy();
            }

            // 更新流体位置和效果
            if (fluidInstance != null && !currentFluid.isEmpty()) {
                updateFluidTransform(currentFluid, pt);
            }
        } catch (Exception e) {
            // 静默处理
        }
    }

    private void updateFluidTransform(FluidStack fluid, float pt) {
        if (fluidInstance == null || fluidVisual == null) return;

        try {
            this.poseStack.pushPose();
            PoseTransformStack msr = TransformStack.of(this.poseStack);

            // 使用 ArmRenderer 的静态方法
            ArmRenderer.transformBase(msr, this.baseAngle);
            ArmRenderer.transformLowerArm(msr, this.lowerArmAngle - 135.0F);
            ArmRenderer.transformUpperArm(msr, this.upperArmAngle - 90.0F);
            ArmRenderer.transformHead(msr, this.headAngle);

            if (this.ceiling && this.blockEntity.goggles) {
                msr.rotateZDegrees(180.0F);
            }

            // 设置流体在针头中的渲染
            fluidVisual.setupPipetteFluid(
                    fluidInstance,
                    fluid,
                    this.blockEntity.getFluidCapacity(),
                    this.blockEntity.isInjectMode()
            );

            // 应用变换
            fluidInstance.setTransform(this.poseStack).setChanged();

            this.poseStack.popPose();
        } catch (Exception e) {
            this.poseStack.popPose(); // 确保堆栈平衡
        }
    }

    private void animateArm() {
        this.updateAngles(this.baseAngle, this.lowerArmAngle - 135.0F,
                this.upperArmAngle - 90.0F, this.headAngle, 16777215);
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

            if (this.claw != null) {
                ArmRenderer.transformHead(msr, headAngle);
                if (this.ceiling && this.blockEntity.goggles) {
                    msr.rotateZDegrees(180.0F);
                }
                this.claw.setTransform(this.poseStack).setChanged();
            }

            if (this.ceiling && this.blockEntity.goggles) {
                msr.rotateZDegrees(180.0F);
            }

            FluidStack fluid = this.blockEntity.heldFluid;
            boolean hasFluid = !fluid.isEmpty();

            // 针头部分 - 注意：这里可能需要使用你自己的 transformClawHalf 方法
            int[] indices = Iterate.zeroAndOne;
            for(int index : indices) {
                if (index < clawGrips.size() && clawGrips.get(index) != null) {
                    this.poseStack.pushPose();
                    int flip = index * 2 - 1;
                    // 如果 ArmRenderer 没有这个方法，使用你自己的实现
                    transformPipetteGrip(msr, hasFluid, flip);
                    clawGrips.get(index).setTransform(this.poseStack).setChanged();
                    this.poseStack.popPose();
                }
            }

            this.poseStack.popPose();
        } catch (Exception e) {
            // 确保堆栈平衡
            try {
                this.poseStack.popPose();
            } catch (Exception ignored) {}
        }
    }

    // 自定义的针头夹持部分变换
    private void transformPipetteGrip(PoseTransformStack msr, boolean hasFluid, int flip) {
        // 对于移液器，针头保持固定位置，不需要根据是否有流体调整
        msr.translate(0.0, (double)((float)(-flip) * 0.0625F), -0.375);
    }

    public void update(float pt) {
        try {
            super.update(pt);

            // 更新爪子模型
            PartialModel clawModel = this.blockEntity.goggles ?
                    AllPartialModels.ARM_CLAW_BASE_GOGGLES : AllPartialModels.ARM_CLAW_BASE;

            this.instancerProvider()
                    .instancer(InstanceTypes.TRANSFORMED, Models.partial(clawModel))
                    .stealInstance(this.claw);
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
            if (fluidVisual != null) {
                fluidVisual.delete();
            }
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