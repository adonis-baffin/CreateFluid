package com.adonis.fluid.mixin;

import com.adonis.fluid.registry.CFFluid;
import com.simibubi.create.content.fluids.pipes.VanillaFluidTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VanillaFluidTargets.class, remap = false)
public class VanillaFluidTargetsMixin {
    
    @Inject(method = "canProvideFluidWithoutCapability", at = @At("HEAD"), cancellable = true)
    private static void addPowderSnowCauldron(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (state.is(Blocks.POWDER_SNOW_CAULDRON)) {
            cir.setReturnValue(true);
        }
    }
    
    @Inject(method = "drainBlock", at = @At("HEAD"), cancellable = true)
    private static void drainPowderSnowCauldron(Level level, BlockPos pos, BlockState state, 
                                                 boolean simulate, CallbackInfoReturnable<FluidStack> cir) {
        if (state.is(Blocks.POWDER_SNOW_CAULDRON)) {
            // 检查是否满
            if (state.getBlock() instanceof LayeredCauldronBlock lcb) {
                if (!lcb.isFull(state)) {
                    cir.setReturnValue(FluidStack.EMPTY);
                    return;
                }
                
                if (!simulate) {
                    level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                }
                
                cir.setReturnValue(CFFluid.getPowderSnowFluidStack(1000));
            }
        }
    }
}