package com.adonis.fluid.mixin;

import com.adonis.fluid.registry.CFFluid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.wrappers.FluidBucketWrapper;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FluidBucketWrapper.class, remap = false)
public class FluidBucketWrapperMixin {

    @Shadow
    @NotNull
    protected ItemStack container;

    /**
     * 修改 canFillFluidType 让它接受细雪流体
     */
    @Inject(method = "canFillFluidType", at = @At("HEAD"), cancellable = true)
    private void acceptPowderSnow(FluidStack fluid, CallbackInfoReturnable<Boolean> cir) {
        if (CFFluid.isPowderSnowFluid(fluid.getFluid())) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 修改 setFluid 来处理细雪流体
     */
    @Inject(method = "setFluid", at = @At("HEAD"), cancellable = true)
    private void setPowderSnowBucket(FluidStack fluidStack, CallbackInfo ci) {
        if (!fluidStack.isEmpty() && CFFluid.isPowderSnowFluid(fluidStack.getFluid())) {
            this.container = new ItemStack(Items.POWDER_SNOW_BUCKET);
            ci.cancel();
        }
    }
}