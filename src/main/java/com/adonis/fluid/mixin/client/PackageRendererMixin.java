package com.adonis.fluid.mixin.client;

import com.adonis.fluid.item.CopperCanItem;
import com.adonis.fluid.util.ICopperCanFeedback;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PackageRenderer.class)
public class PackageRendererMixin {

	@Inject(method = "render", at = @At("HEAD"))
	private void fluid$pushAndShakeCopperCan(PackageEntity entity, float yaw, float pt, PoseStack ms,
		MultiBufferSource buffer, int light, CallbackInfo ci) {
		ms.pushPose();

		if (!CopperCanItem.isCopperCan(entity.box))
			return;
		if (!(entity instanceof ICopperCanFeedback feedback))
			return;

		float shakeTime = feedback.fluid$getHurtShakeTime() - pt;
		if (shakeTime <= 0)
			return;

		float direction = (entity.getId() & 1) == 0 ? 1.0F : -1.0F;
		float normalized = Math.min(1.0F, shakeTime / 26.0F);
		float decay = 0.35F + normalized * 0.65F;
		float roll = Mth.sin(shakeTime * 0.65F) * (10.0F + normalized * 16.0F) * decay * direction;
		ms.translate(0, 0.035F, 0);
		ms.mulPose(Axis.ZP.rotationDegrees(roll));
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void fluid$popAfterCopperCanShake(PackageEntity entity, float yaw, float pt, PoseStack ms,
		MultiBufferSource buffer, int light, CallbackInfo ci) {
		ms.popPose();
	}
}
