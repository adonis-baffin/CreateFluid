//package com.adonis.fluid.block.CentrifugalPump;
//
//import com.mojang.blaze3d.vertex.PoseStack;
//import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
//import dev.engine_room.flywheel.lib.transform.TransformStack;
//import net.createmod.catnip.math.AngleHelper;
//import net.createmod.catnip.math.VecHelper;
//import net.minecraft.core.BlockPos;
//import net.minecraft.core.Direction;
//import net.minecraft.world.level.LevelAccessor;
//import net.minecraft.world.level.block.state.BlockState;
//import net.minecraft.world.level.block.state.properties.AttachFace;
//import net.minecraft.world.phys.Vec3;
//
//public class CentrifugalPumpValueBox extends ValueBoxTransform.Sided {
//
//    public CentrifugalPumpValueBox() {
//        super();
//        System.out.println("【ValueBox】CentrifugalPumpValueBox 构造函数被调用！");
//    }
//
//    @Override
//    protected boolean isSideActive(BlockState state, Direction side) {
//        System.out.println("【ValueBox】isSideActive 被调用 - side: " + side);
//
//        if (!(state.getBlock() instanceof CentrifugalPumpBlock)) {
//            System.out.println("【ValueBox】不是离心泵方块");
//            return false;
//        }
//
//        AttachFace face = state.getValue(CentrifugalPumpBlock.FACE);
//        Direction facing = state.getValue(CentrifugalPumpBlock.FACING);
//
//        boolean active = false;
//
//        if (face == AttachFace.WALL) {
//            // 垂直模式
//            active = side.getAxis() != Direction.Axis.Y
//                && side != facing
//                && side != facing.getOpposite();
//        } else {
//            // 水平模式
//            active = side.getAxis() != facing.getAxis()
//                && side.getAxis() != Direction.Axis.Y;
//        }
//
//        System.out.println("【ValueBox】Face: " + face + ", Facing: " + facing + ", Side: " + side + " -> Active: " + active);
//        return active;
//    }
//
//    @Override
//    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
//        Direction side = this.getSide();
//        System.out.println("【ValueBox】getLocalOffset 被调用 - getSide(): " + side);
//
//        if (!(state.getBlock() instanceof CentrifugalPumpBlock)) {
//            return null;
//        }
//
//        AttachFace face = state.getValue(CentrifugalPumpBlock.FACE);
//        Direction facing = state.getValue(CentrifugalPumpBlock.FACING);
//
//        // 使用简单的固定位置测试
//        Vec3 local;
//        if (side == Direction.NORTH) {
//            local = VecHelper.voxelSpace(8, 8, 0);
//        } else if (side == Direction.SOUTH) {
//            local = VecHelper.voxelSpace(8, 8, 16);
//        } else if (side == Direction.WEST) {
//            local = VecHelper.voxelSpace(0, 8, 8);
//        } else if (side == Direction.EAST) {
//            local = VecHelper.voxelSpace(16, 8, 8);
//        } else {
//            local = VecHelper.voxelSpace(8, 8, 8);
//        }
//
//        System.out.println("【ValueBox】返回位置: " + local);
//        return local;
//    }
//
//    @Override
//    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
//        Direction side = this.getSide();
//        System.out.println("【ValueBox】rotate 被调用 - side: " + side);
//
//        float yRot = AngleHelper.horizontalAngle(side) + 180;
//        TransformStack.of(ms).rotateYDegrees(yRot);
//
//        AttachFace face = state.getValue(CentrifugalPumpBlock.FACE);
//        if (face == AttachFace.CEILING) {
//            TransformStack.of(ms).rotateZDegrees(180);
//        }
//    }
//
//    @Override
//    protected Vec3 getSouthLocation() {
//        System.out.println("【ValueBox】getSouthLocation 被调用");
//        return VecHelper.voxelSpace(8, 8, 16);
//    }
//
//    @Override
//    public ValueBoxTransform.Sided fromSide(Direction direction) {
//        System.out.println("【ValueBox】fromSide 被调用 - direction: " + direction);
//        return super.fromSide(direction);
//    }
//
//    @Override
//    public boolean shouldRender(LevelAccessor level, BlockPos pos, BlockState state) {
//        boolean result = super.shouldRender(level, pos, state);
//        System.out.println("【ValueBox】shouldRender 被调用 - 结果: " + result);
//        return result;
//    }
//}