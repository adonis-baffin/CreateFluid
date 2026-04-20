package com.adonis.fluid.block.GutterOutlet;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;

import net.minecraft.world.level.block.entity.BlockEntity;

public class GutterOutletMovementBehaviour implements MovementBehaviour {

    @Override
    public boolean mustTickWhileDisabled() {
        return true;
    }

    @Override
    public void tick(MovementContext context) {
        if (context.world.isClientSide) {
            BlockEntity be = context.contraption.getBlockEntityClientSide(context.localPos);
            if (be instanceof GutterOutletBlockEntity gutter) {
                if (gutter.getFluidLevel() != null) {
                    gutter.getFluidLevel().tickChaser();
                }
                // 同时 tick SmartFluidTankBehaviour 的 TankSegment，确保 renderer 的液面动画平滑
                if (gutter.tankBehaviour != null) {
                    SmartFluidTankBehaviour.TankSegment primaryTank = gutter.tankBehaviour.getPrimaryTank();
                    if (primaryTank != null && primaryTank.getFluidLevel() != null) {
                        primaryTank.getFluidLevel().tickChaser();
                    }
                }
            }
        }
    }
}