package com.adonis.fluid.mixin;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.adonis.fluid.datacomponent.FluidManifestContent;
import com.adonis.fluid.item.FluidManifestItem;
import com.adonis.fluid.registry.CFDataComponents;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

@Mixin(InventorySummary.class)
public class InventorySummaryMixin {

    @Shadow(remap = false)
    @Final
    private Map<Item, List<BigItemStack>> items;

    @Shadow(remap = false)
    private int totalCount;

    /**
     * 让 InventorySummary 在匹配 FluidManifestItem 时，按流体 registry ID 匹配
     * 而不是严格的 ItemStack.isSameItemSameComponents。
     * ResourceLocation 的序列化是绝对稳定的，根治客户端反序列化后的匹配问题。
     */
    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;I)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluid$addFluidManifest(ItemStack stack, int count, CallbackInfo ci) {
        if (count == 0 || stack.isEmpty())
            return;
        if (!(stack.getItem() instanceof FluidManifestItem))
            return;

        FluidManifestContent content = stack.get(CFDataComponents.FLUID_MANIFEST.get());
        if (content == null || content.fluidId() == null)
            return;

        if (totalCount < BigItemStack.INF)
            totalCount += count;

        List<BigItemStack> stacks = items.computeIfAbsent(stack.getItem(), $ -> new java.util.ArrayList<>());
        for (BigItemStack existing : stacks) {
            FluidManifestContent existingContent = existing.stack.get(CFDataComponents.FLUID_MANIFEST.get());
            if (existingContent != null && existingContent.fluidId().equals(content.fluidId())) {
                if (existing.count < BigItemStack.INF)
                    existing.count += count;
                ci.cancel();
                return;
            }
        }
    }

    @Inject(method = "getCountOf", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluid$getCountOfFluidManifest(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (!(stack.getItem() instanceof FluidManifestItem))
            return;

        FluidManifestContent content = stack.get(CFDataComponents.FLUID_MANIFEST.get());
        if (content == null || content.fluidId() == null) {
            cir.setReturnValue(0);
            return;
        }

        List<BigItemStack> list = items.get(stack.getItem());
        if (list == null) {
            cir.setReturnValue(0);
            return;
        }

        int result = 0;
        for (BigItemStack entry : list) {
            FluidManifestContent entryContent = entry.stack.get(CFDataComponents.FLUID_MANIFEST.get());
            if (entryContent != null && entryContent.fluidId().equals(content.fluidId())) {
                result += entry.count;
            }
        }
        cir.setReturnValue(result);
    }

    @Inject(method = "erase", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluid$eraseFluidManifest(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!(stack.getItem() instanceof FluidManifestItem))
            return;

        FluidManifestContent content = stack.get(CFDataComponents.FLUID_MANIFEST.get());
        if (content == null || content.fluidId() == null) {
            cir.setReturnValue(false);
            return;
        }

        List<BigItemStack> stacks = items.get(stack.getItem());
        if (stacks == null) {
            cir.setReturnValue(false);
            return;
        }

        for (Iterator<BigItemStack> iterator = stacks.iterator(); iterator.hasNext(); ) {
            BigItemStack existing = iterator.next();
            FluidManifestContent existingContent = existing.stack.get(CFDataComponents.FLUID_MANIFEST.get());
            if (existingContent != null && existingContent.fluidId().equals(content.fluidId())) {
                totalCount -= existing.count;
                iterator.remove();
                cir.setReturnValue(true);
                return;
            }
        }

        cir.setReturnValue(false);
    }
}
