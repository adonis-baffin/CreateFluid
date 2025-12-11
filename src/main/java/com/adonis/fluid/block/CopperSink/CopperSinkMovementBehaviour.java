package com.adonis.fluid.block.CopperSink;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;

import net.minecraft.world.level.block.entity.BlockEntity;

public class CopperSinkMovementBehaviour implements MovementBehaviour {
    
    @Override
    public boolean mustTickWhileDisabled() {
        return true;
    }

    @Override
    public void tick(MovementContext context) {
        if (context.world.isClientSide) {
            BlockEntity be = context.contraption.getBlockEntityClientSide(context.localPos);
            if (be instanceof CopperSinkBlockEntity sink) {
                if (sink.getFluidLevel() != null) {
                    sink.getFluidLevel().tickChaser();
                }
            }
        }
    }
}