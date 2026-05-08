package com.adonis.fluid.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.adonis.fluid.item.BrassBoxItem;
import com.adonis.fluid.item.CopperCanItem;
import com.adonis.fluid.item.PackageRoutingHelper;
import com.simibubi.create.content.logistics.box.PackageItem;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

@Mixin(PackageItem.class)
public class PackageItemMixin {
	@Inject(method = "appendHoverText", at = @At("TAIL"))
	private void fluid$appendRoutingSummary(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
		List<Component> tooltipComponents, TooltipFlag tooltipFlag, CallbackInfo ci) {
		if (BrassBoxItem.isBrassBox(stack) || CopperCanItem.isCopperCan(stack))
			return;
		PackageRoutingHelper.appendRoutingSummary(stack, tooltipComponents);
	}
}
