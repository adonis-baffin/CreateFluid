package com.adonis.fluid.mixin;

import com.adonis.fluid.content.pipette.VirtualRelayManager;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BeltInventory.class, remap = false)
public class BeltInventoryMixin {

    @Shadow @Final
    BeltBlockEntity belt;

    @Inject(method = "getBeltProcessingAtSegment",
            at = @At("HEAD"),
            cancellable = true)
    private void injectVirtualRelay(int segment, CallbackInfoReturnable<BeltProcessingBehaviour> cir) {
        BlockPos beltPos = BeltHelper.getPositionForOffset(belt, segment);
        BlockPos checkPos = beltPos.above(2);

        VirtualRelayManager.VirtualRelay relay = VirtualRelayManager.getRelayAt(checkPos);
        if (relay != null && relay.isValid()) {
            cir.setReturnValue(relay.getProcessingBehaviour());
        }
    }
}