package com.adonis.fluid.mixin;

import com.adonis.fluid.registry.CFFluids;
import com.simibubi.create.content.fluids.pipes.VanillaFluidTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for VanillaFluidTargets to add powder snow cauldron support
 */
@Mixin(value = VanillaFluidTargets.class, remap = false)
public class VanillaFluidTargetsMixin {

    /**
     * Inject into canProvideFluidWithoutCapability to support powder snow cauldron
     */
    @Inject(method = "canProvideFluidWithoutCapability", at = @At("HEAD"), cancellable = true)
    private static void fluid$checkPowderSnowCauldron(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (state.is(Blocks.POWDER_SNOW_CAULDRON)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Inject into drainBlock to support draining powder snow cauldron
     */
    @Inject(method = "drainBlock", at = @At("HEAD"), cancellable = true)
    private static void fluid$drainPowderSnowCauldron(Level world, BlockPos pos, BlockState state, boolean simulate,
                                                      CallbackInfoReturnable<FluidStack> cir) {
        if (state.is(Blocks.POWDER_SNOW_CAULDRON)) {
            // Check if cauldron is full (level 3)
            int cauldronLevel = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_CAULDRON);
            if (cauldronLevel < 3) {
                cir.setReturnValue(FluidStack.EMPTY);
                return;
            }
            if (!simulate) {
                world.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
            }
            cir.setReturnValue(new FluidStack(CFFluids.POWDER_SNOW.get(), 1000));
        }
    }
}
