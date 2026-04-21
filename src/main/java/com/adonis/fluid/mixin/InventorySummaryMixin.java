package com.adonis.fluid.mixin;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.adonis.fluid.item.FluidManifestItem;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

@Mixin(InventorySummary.class)
public class InventorySummaryMixin {

    @Shadow(remap = false)
    @Final
    private Map<Item, List<BigItemStack>> items;

    @Shadow(remap = false)
    private int totalCount;

    /**
     * 让 InventorySummary 在匹配 FluidManifestItem 时，按流体类型匹配而不是严格的
     * ItemStack.isSameItemSameComponents。这解决了客户端反序列化后的 FluidManifestItem
     * 与服务端生成的 FluidManifestItem 因 FluidStack 内部字段微小差异而不匹配的问题。
     */
    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;I)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluid$addFluidManifest(ItemStack stack, int count, CallbackInfo ci) {
        if (count == 0 || stack.isEmpty())
            return;
        if (!(stack.getItem() instanceof FluidManifestItem))
            return;

        FluidStack fluid = FluidManifestItem.read(stack);
        if (fluid.isEmpty())
            return;

        if (totalCount < BigItemStack.INF)
            totalCount += count;

        List<BigItemStack> stacks = items.computeIfAbsent(stack.getItem(), $ -> new java.util.ArrayList<>());
        for (BigItemStack existing : stacks) {
            FluidStack existingFluid = FluidManifestItem.read(existing.stack);
            if (!existingFluid.isEmpty() && FluidStack.isSameFluidSameComponents(existingFluid, fluid)) {
                if (existing.count < BigItemStack.INF)
                    existing.count += count;
                ci.cancel();
                return;
            }
        }

        // 未找到匹配，继续走原版逻辑（添加新条目）
        // 注意：我们不在此处 cancel，让原版代码处理新条目插入
    }

    @Inject(method = "getCountOf", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluid$getCountOfFluidManifest(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (!(stack.getItem() instanceof FluidManifestItem))
            return;

        FluidStack fluid = FluidManifestItem.read(stack);
        if (fluid.isEmpty()) {
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
            FluidStack entryFluid = FluidManifestItem.read(entry.stack);
            if (!entryFluid.isEmpty() && FluidStack.isSameFluidSameComponents(entryFluid, fluid)) {
                result += entry.count;
            }
        }
        cir.setReturnValue(result);
    }

    @Inject(method = "erase", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluid$eraseFluidManifest(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!(stack.getItem() instanceof FluidManifestItem))
            return;

        FluidStack fluid = FluidManifestItem.read(stack);
        if (fluid.isEmpty()) {
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
            FluidStack existingFluid = FluidManifestItem.read(existing.stack);
            if (!existingFluid.isEmpty() && FluidStack.isSameFluidSameComponents(existingFluid, fluid)) {
                totalCount -= existing.count;
                iterator.remove();
                cir.setReturnValue(true);
                return;
            }
        }

        cir.setReturnValue(false);
    }
}
