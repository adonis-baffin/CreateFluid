package com.adonis.fluid.block.FluidAtomizer;

import com.adonis.fluid.block.FluidAtomizer.FluidAtomizerBlockEntity;
import com.adonis.fluid.registry.CFPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import net.minecraft.core.Direction;

import java.util.function.Consumer;

public class FluidAtomizerVisual extends KineticBlockEntityVisual<FluidAtomizerBlockEntity> {

    protected final RotatingInstance shaft;
    protected final RotatingInstance fan;
    protected final Direction facing;
    protected final Direction shaftDirection;

    public FluidAtomizerVisual(VisualizationContext context, FluidAtomizerBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
        this.facing = blockState.getValue(FluidAtomizerBlock.FACING);
        this.shaftDirection = facing.getOpposite();

        if (CFPartialModels.FLUID_ATOMIZER_SHAFT != null) {
            shaft = instancerProvider().instancer(AllInstanceTypes.ROTATING, Models.partial(CFPartialModels.FLUID_ATOMIZER_SHAFT))
                    .createInstance();
            shaft.setup(blockEntity)
                    .setPosition(getVisualPosition())
                    .rotateToFace(Direction.SOUTH, shaftDirection)
                    .setChanged();
        } else {
            shaft = null;
        }

        if (CFPartialModels.FLUID_ATOMIZER_FAN != null) {
            fan = instancerProvider().instancer(AllInstanceTypes.ROTATING, Models.partial(CFPartialModels.FLUID_ATOMIZER_FAN))
                    .createInstance();
            fan.setup(blockEntity)
                    .setPosition(getVisualPosition())
                    .rotateToFace(Direction.SOUTH, shaftDirection)
                    .setChanged();
        } else {
            fan = null;
        }
    }

    @Override
    public void update(float pt) {
        if (shaft != null) {
            shaft.setup(blockEntity).setChanged();
        }
        if (fan != null) {
            fan.setup(blockEntity).setChanged();
        }
    }

    @Override
    public void updateLight(float partialTick) {
        if (shaft != null) {
            relight(pos, shaft);
        }
        if (fan != null) {
            relight(pos.relative(facing), fan);
        }
    }

    @Override
    protected void _delete() {
        if (shaft != null) {
            shaft.delete();
        }
        if (fan != null) {
            fan.delete();
        }
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        if (shaft != null) {
            consumer.accept(shaft);
        }
        if (fan != null) {
            consumer.accept(fan);
        }
    }
}
