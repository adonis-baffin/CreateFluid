package com.adonis.fluid.block.FluidAtomizer;

import com.adonis.fluid.compat.ContraptionBlockEntityHelper;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import net.minecraft.world.level.block.entity.BlockEntity;

public class FluidAtomizerMovementBehaviour implements MovementBehaviour {

    @Override
    public boolean mustTickWhileDisabled() {
        return true;
    }

    @Override
    public void tick(MovementContext context) {
        if (!context.world.isClientSide) {
            return;
        }

        BlockEntity be = ContraptionBlockEntityHelper.getBlockEntity(context.contraption, context.localPos);
        if (!(be instanceof FluidAtomizerBlockEntity atomizer) || atomizer.tankBehaviour == null) {
            return;
        }

        SmartFluidTankBehaviour.TankSegment primaryTank = atomizer.tankBehaviour.getPrimaryTank();
        if (primaryTank != null && primaryTank.getFluidLevel() != null) {
            primaryTank.getFluidLevel().tickChaser();
        }
    }
}
