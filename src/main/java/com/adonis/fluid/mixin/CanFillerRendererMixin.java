package com.adonis.fluid.mixin;

import com.adonis.fluid.block.canfiller.CanFillerBlockEntity;
import com.adonis.fluid.registry.CFBlocks;
import com.adonis.fluid.registry.CFPartialModels;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PackagerRenderer.class)
public class CanFillerRendererMixin {

    @Inject(method = "getTrayModel(Lnet/minecraft/world/level/block/state/BlockState;)Ldev/engine_room/flywheel/lib/model/baked/PartialModel;",
            at = @At("HEAD"), cancellable = true)
    private static void fluidPackager$getTrayModel(BlockState blockState, CallbackInfoReturnable<PartialModel> cir) {
        if (CFBlocks.CAN_FILLER.has(blockState) && CFPartialModels.CAN_FILLER_TRAY != null) {
            cir.setReturnValue(CFPartialModels.CAN_FILLER_TRAY);
        }
    }

    @Inject(method = "getHatchModel(Lcom/simibubi/create/content/logistics/packager/PackagerBlockEntity;)Ldev/engine_room/flywheel/lib/model/baked/PartialModel;",
            at = @At("HEAD"), cancellable = true)
    private static void fluidPackager$getHatchModel(PackagerBlockEntity be, CallbackInfoReturnable<PartialModel> cir) {
        if (be instanceof CanFillerBlockEntity && CFPartialModels.CAN_FILLER_HATCH_OPEN != null && CFPartialModels.CAN_FILLER_HATCH_CLOSED != null) {
            boolean open = PackagerRenderer.isHatchOpen(be);
            cir.setReturnValue(open ? CFPartialModels.CAN_FILLER_HATCH_OPEN : CFPartialModels.CAN_FILLER_HATCH_CLOSED);
        }
    }
}
