package com.adonis.fluid.mixin;

import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlock;
import com.simibubi.create.content.fluids.FlowSource;
import com.simibubi.create.content.fluids.PipeConnection;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(value = PipeConnection.class, remap = false)
public class PipeConnectionMixin {
    
    @Shadow
    public Direction side;
    
    @Shadow
    Optional<FlowSource> source;
    
    @Inject(method = "determineSource", at = @At("HEAD"), cancellable = true)
    private void handleCentrifugalPumpSource(Level world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockPos relative = pos.relative(this.side);
        BlockState state = world.getBlockState(relative);
        
        if (state.getBlock() instanceof CentrifugalPumpBlock) {
            if (CentrifugalPumpBlock.isOpenAt(state, this.side.getOpposite())) {
                BlockFace location = new BlockFace(pos, this.side);
                this.source = Optional.of(new FlowSource.OtherPipe(location));
                cir.setReturnValue(true);
            }
        }
    }
}