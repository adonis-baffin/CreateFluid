package com.adonis.fluid.mixin;

import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FluidPipeBlock.class, remap = false)
public class FluidPipeBlockMixin {

    // 让管道能连接到离心泵
    @Inject(method = "canConnectTo", at = @At("HEAD"), cancellable = true)
    private static void connectToCentrifugalPump(BlockAndTintGetter world, BlockPos neighbourPos,
                                                 BlockState neighbour, Direction direction, CallbackInfoReturnable<Boolean> cir) {

        if (neighbour.getBlock() instanceof CentrifugalPumpBlock) {
            // 检查离心泵这个方向是否开放
            if (CentrifugalPumpBlock.isOpenAt(neighbour, direction.getOpposite())) {
                cir.setReturnValue(true);
            }
        }
    }

    // 让管道在判断是否需要边框时识别离心泵
    @Inject(method = "shouldDrawRim", at = @At("HEAD"), cancellable = true)
    private static void checkRimForCentrifugalPump(BlockAndTintGetter world, BlockPos pos,
                                                   BlockState state, Direction direction, CallbackInfoReturnable<Boolean> cir) {

        BlockPos offsetPos = pos.relative(direction);
        BlockState facingState = world.getBlockState(offsetPos);

        if (facingState.getBlock() instanceof CentrifugalPumpBlock) {
            // 如果连接到离心泵，不显示边框
            if (CentrifugalPumpBlock.isOpenAt(facingState, direction.getOpposite())) {
                cir.setReturnValue(false);
            }
        }
    }
}
