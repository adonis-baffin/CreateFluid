package com.adonis.fluid.mixin;

import com.adonis.fluid.item.BrassBoxItem;
import com.adonis.fluid.item.PackageRoutingHelper;
import com.adonis.fluid.logistics.manager.MixedOrderRoutingManager;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PackagerBlockEntity.class)
public class PackagerBlockEntityMixin {
	@Redirect(method = "attemptToSend",
		at = @At(value = "INVOKE",
			target = "Lcom/simibubi/create/content/logistics/box/PackageItem;setOrder(Lnet/minecraft/world/item/ItemStack;IIZIZLcom/simibubi/create/content/logistics/stockTicker/PackageOrderWithCrafts;)V"))
	private void fluid$setOrderAndAttachRouting(ItemStack box, int orderId, int linkIndex, boolean isFinalLink,
		int fragmentIndex, boolean isFinal, PackageOrderWithCrafts orderContext) {
		PackageItem.setOrder(box, orderId, linkIndex, isFinalLink, fragmentIndex, isFinal, orderContext);
		var routing = MixedOrderRoutingManager.resolveAndBind(orderId, orderContext);
		if (!routing.isEmpty())
			PackageRoutingHelper.setRoutingData(box, routing);
	}
}
