package com.adonis.fluid.mixin;

import com.adonis.fluid.mixin.accessor.ArmBlockEntityAccessor;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointHandler;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ArmBlockEntity.class, remap = false)
public class ArmBlockEntityWrenchUpdateMixin {
    
    @Inject(method = "read", at = @At("TAIL"))
    private void onReadClient(CompoundTag compound, boolean clientPacket, CallbackInfo ci) {
        if (clientPacket) {
            // 当动力臂数据更新时，清除扳手的缓存
            try {
                java.lang.reflect.Field lastBlockPosField = ArmInteractionPointHandler.class.getDeclaredField("lastBlockPos");
                lastBlockPosField.setAccessible(true);
                lastBlockPosField.set(null, -1L);
            } catch (Exception e) {
                // 静默处理
            }
        }
    }
}