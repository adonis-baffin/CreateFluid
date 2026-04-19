package com.adonis.fluid.mixin.accessor;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorShape;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChainConveyorShape.ChainConveyorOBB.class)
public interface ChainConveyorOBBAccessor {
    @Accessor("connection")
    BlockPos createfluid$getConnection();
}
