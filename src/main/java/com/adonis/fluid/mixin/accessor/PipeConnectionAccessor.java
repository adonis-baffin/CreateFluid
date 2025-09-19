package com.adonis.fluid.mixin.accessor;

import com.simibubi.create.content.fluids.FlowSource;
import com.simibubi.create.content.fluids.PipeConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

@Mixin(value = PipeConnection.class, remap = false)
public interface PipeConnectionAccessor {

    @Accessor("source")
    Optional<FlowSource> getSource();

    @Accessor("source")
    void setSource(Optional<FlowSource> source);

    @Accessor("flow")
    Optional<PipeConnection.Flow> getFlow();

    @Accessor("flow")
    void setFlow(Optional<PipeConnection.Flow> flow);
}