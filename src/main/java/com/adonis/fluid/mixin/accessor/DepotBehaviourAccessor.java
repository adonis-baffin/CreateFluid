package com.adonis.fluid.mixin.accessor;

import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = DepotBehaviour.class, remap = false)
public interface DepotBehaviourAccessor {
    @Accessor("heldItem")
    TransportedItemStack getHeldItem();
    
    @Accessor("heldItem")
    void setHeldItem(TransportedItemStack heldItem);
}