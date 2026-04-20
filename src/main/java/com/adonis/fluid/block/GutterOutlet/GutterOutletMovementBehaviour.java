package com.adonis.fluid.block.GutterOutlet;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;

import net.minecraft.world.level.block.entity.BlockEntity;

public class GutterOutletMovementBehaviour implements MovementBehaviour {

    @Override
    public boolean mustTickWhileDisabled() {
        return true;
    }

    @Override
    public void tick(MovementContext context) {
        if (context.world.isClientSide) {
            BlockEntity be = context.contraption.presentBlockEntities.get(context.localPos);
            if (be instanceof GutterOutletBlockEntity gutter) {
                if (gutter.getFluidLevel() != null) {
                    gutter.getFluidLevel().tickChaser();
                }
            }
        }
    }
}
