// java/com/adonis/fluid/mixin/FluidUtilMixin.java
package com.adonis.fluid.mixin;

import com.adonis.fluid.registry.CFFluid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;

@Mixin(value = FluidUtil.class, remap = false)
public class FluidUtilMixin {
    
    @Inject(method = "getFluidContained", at = @At("HEAD"), cancellable = true)
    private static void handlePowderSnowBucket(ItemStack container, CallbackInfoReturnable<Optional<FluidStack>> cir) {
        if (container.getItem() == Items.POWDER_SNOW_BUCKET) {
            cir.setReturnValue(Optional.of(new FluidStack(CFFluid.POWDER_SNOW.get(), 1000)));
        }
    }
}