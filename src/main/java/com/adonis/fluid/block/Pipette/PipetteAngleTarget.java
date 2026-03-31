package com.adonis.fluid.block.Pipette;

import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class PipetteAngleTarget {
    public static final PipetteAngleTarget NO_TARGET = new PipetteAngleTarget();
    public float baseAngle;
    public float lowerArmAngle;
    public float upperArmAngle;
    public float headAngle;

    private PipetteAngleTarget() {
        this.lowerArmAngle = 135.0F;
        this.upperArmAngle = 45.0F;
        this.headAngle = 0.0F;
    }

    public PipetteAngleTarget(BlockPos armPos, Vec3 pointTarget, Direction clawFacing, boolean ceiling) {
        Vec3 target = pointTarget;
        Vec3 origin = VecHelper.getCenterOf(armPos).add(0.0, ceiling ? -0.375 : 0.375, 0.0);
        Vec3 clawTarget = target;
        target = target.add(Vec3.atLowerCornerOf(clawFacing.getOpposite().getNormal()).scale(0.5));
        Vec3 diff = target.subtract(origin);
        float horizontalDistance = (float)diff.multiply(1.0, 0.0, 1.0).length();
        float baseAngle = AngleHelper.deg(Mth.atan2(diff.x, diff.z)) + 180.0F;
        if (ceiling) {
            diff = diff.multiply(1.0, -1.0, 1.0);
            baseAngle = 180.0F - baseAngle;
        }

        float alphaOffset = AngleHelper.deg(Mth.atan2(diff.y, horizontalDistance));
        float a = 0.875F;
        float a2 = a * a;
        float b = 0.9375F;
        float b2 = b * b;
        float diffLength = Mth.clamp(Mth.sqrt((float)(diff.y * diff.y + horizontalDistance * horizontalDistance)), 0.125F, a + b);
        float diffLength2 = diffLength * diffLength;
        float alphaRatio = (-b2 + a2 + diffLength2) / (2.0F * a * diffLength);
        float alpha = AngleHelper.deg(Math.acos(alphaRatio)) + alphaOffset;
        float betaRatio = (-diffLength2 + a2 + b2) / (2.0F * b * a);
        float beta = AngleHelper.deg(Math.acos(betaRatio));
        if (Float.isNaN(alpha)) {
            alpha = 0.0F;
        }

        if (Float.isNaN(beta)) {
            beta = 0.0F;
        }

        Vec3 headPos = new Vec3(0.0, 0.0, 0.0);
        headPos = VecHelper.rotate(headPos.add(0.0, b, 0.0), beta + 180.0F, Axis.X);
        headPos = VecHelper.rotate(headPos.add(0.0, a, 0.0), alpha - 90.0F, Axis.X);
        headPos = VecHelper.rotate(headPos, baseAngle, Axis.Y);
        headPos = VecHelper.rotate(headPos, ceiling ? 180.0 : 0.0, Axis.X);
        headPos = headPos.add(origin);
        Vec3 headDiff = clawTarget.subtract(headPos);
        if (ceiling) {
            headDiff = headDiff.multiply(1.0, -1.0, 1.0);
        }

        float horizontalHeadDistance = (float)headDiff.multiply(1.0, 0.0, 1.0).length();
        float headAngle = alpha + beta + 135.0F - AngleHelper.deg(Mth.atan2(headDiff.y, horizontalHeadDistance));
        this.lowerArmAngle = alpha;
        this.upperArmAngle = beta;
        this.headAngle = -headAngle;
        this.baseAngle = baseAngle;
    }
}