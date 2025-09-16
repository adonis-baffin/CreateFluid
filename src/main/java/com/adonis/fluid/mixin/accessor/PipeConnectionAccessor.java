package com.adonis.fluid.mixin.accessor;

import com.simibubi.create.content.fluids.PipeConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

@Mixin(PipeConnection.class)
public interface PipeConnectionAccessor {
    @Accessor("flow")
    void setFlow(Optional<PipeConnection.Flow> flow);
    
    @Accessor("flow")
    Optional<PipeConnection.Flow> getFlow();
}