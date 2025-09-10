package com.adonis.fluid.mixin.accessor;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import net.minecraft.nbt.ListTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = ArmBlockEntity.class, remap = false)
public interface ArmBlockEntityAccessor {
    @Accessor("inputs")
    List<ArmInteractionPoint> getInputs();

    @Accessor("outputs")
    List<ArmInteractionPoint> getOutputs();

    @Accessor("interactionPointTag")
    ListTag getInteractionPointTag();

    @Accessor("interactionPointTag")
    void setInteractionPointTag(ListTag tag);

    @Accessor("updateInteractionPoints")
    boolean getUpdateInteractionPoints();

    @Accessor("updateInteractionPoints")
    void setUpdateInteractionPoints(boolean value);

    @Accessor("phase")
    ArmBlockEntity.Phase getPhase();

    @Accessor("phase")
    void setPhase(ArmBlockEntity.Phase phase);

    @Accessor("chasedPointProgress")
    float getChasedPointProgress();

    @Accessor("chasedPointProgress")
    void setChasedPointProgress(float value);

    @Accessor("chasedPointIndex")
    int getChasedPointIndex();

    @Accessor("chasedPointIndex")
    void setChasedPointIndex(int value);
}