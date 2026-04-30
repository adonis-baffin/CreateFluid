package com.adonis.fluid.handler;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.item.CopperCanItem;
import com.adonis.fluid.util.ICopperCanFeedback;
import com.simibubi.create.content.logistics.box.PackageEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@EventBusSubscriber(modid = CreateFluid.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class CopperCanFeedbackHandler {

	@SubscribeEvent
	public static void onCopperCanAttacked(LivingIncomingDamageEvent event) {
		if (!(event.getEntity() instanceof PackageEntity packageEntity))
			return;
		if (!CopperCanItem.isCopperCan(packageEntity.box))
			return;
		if (!(packageEntity instanceof ICopperCanFeedback feedback))
			return;

		feedback.fluid$setHurtShakeTime(26);
		packageEntity.hurtMarked = true;
	}
}
