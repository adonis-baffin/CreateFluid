package com.adonis.fluid.mixin.accessor;

import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ItemDrainBlockEntity.class, remap = false)
public interface ItemDrainBlockEntityAccessor {
    @Accessor("internalTank")
    SmartFluidTankBehaviour getInternalTank();
}