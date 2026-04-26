package com.adonis.fluid.mixin;

import com.adonis.fluid.block.FluidAtomizer.FluidAtomizerBlockEntity;
import com.simibubi.create.content.kinetics.fan.NozzleBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NozzleBlock.class)
public class NozzleBlockMixin {

    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void fluid$preventPlacementOnAtomizer(BlockState state, LevelReader world, BlockPos pos,
                                                  CallbackInfoReturnable<Boolean> cir) {
        Direction towardsFan = state.getValue(NozzleBlock.FACING).getOpposite();
        BlockEntity be = world.getBlockEntity(pos.relative(towardsFan));
        if (be instanceof FluidAtomizerBlockEntity) {
            cir.setReturnValue(false);
        }
    }
}
