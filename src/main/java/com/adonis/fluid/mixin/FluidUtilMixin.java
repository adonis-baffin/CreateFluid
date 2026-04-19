package com.adonis.fluid.mixin;

import com.adonis.fluid.registry.CFFluids;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Mixin to make FluidUtil recognize powder snow buckets
 */
@Mixin(value = FluidUtil.class, remap = false)
public class FluidUtilMixin {

    @Inject(method = "getFluidContained", at = @At("HEAD"), cancellable = true, remap = false)
    private static void handlePowderSnowBucket(ItemStack container, CallbackInfoReturnable<Optional<FluidStack>> cir) {
        if (container.is(Items.POWDER_SNOW_BUCKET)) {
            cir.setReturnValue(Optional.of(new FluidStack(CFFluids.POWDER_SNOW.get(), 1000)));
        }
    }
}
