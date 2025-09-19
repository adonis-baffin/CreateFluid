package com.adonis.fluid.mixin;

import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlock;
import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlockEntity;
import com.simibubi.create.content.fluids.FluidNetwork;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FluidNetwork.class, remap = false)
public class FluidNetworkMixin {
    
    @Shadow
    Level world;
    
    @Shadow
    BlockFace start;
    
    @Inject(method = "tick", at = @At("HEAD"))
    private void recognizeCentrifugalPump(CallbackInfo ci) {
        if (world != null && start != null) {
            BlockPos pos = start.getPos();
            BlockState state = world.getBlockState(pos);
            
            // 确保离心泵被正确识别为网络的一部分
            if (state.getBlock() instanceof CentrifugalPumpBlock) {
                BlockEntity be = world.getBlockEntity(pos);
                if (be instanceof CentrifugalPumpBlockEntity pump) {
                    // 触发必要的更新
                    FluidTransportBehaviour behaviour = pump.getBehaviour(FluidTransportBehaviour.TYPE);
                    if (behaviour != null) {
                        PipeConnection connection = behaviour.getConnection(start.getFace());
                        if (connection != null && !connection.hasFlow()) {
                            connection.determineSource(world, pos);
                        }
                    }
                }
            }
        }
    }
}