package com.adonis.fluid.mixin.accessor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorShape;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ChainConveyorShape.class, remap = false)
public interface ChainConveyorShapeAccessor {
    @Invoker("drawOutline")
    void createfluid$invokeDrawOutline(BlockPos anchor, PoseStack ms, VertexConsumer vb);
}
