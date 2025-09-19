package com.adonis.fluid.mixin;

import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlockEntity;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.data.WorldAttached;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(value = FluidTransportBehaviour.class, remap = false)
public abstract class FluidTransportBehaviourMixin {

    @Shadow
    public static WorldAttached<Map<BlockPos, Map<Direction, PipeConnection>>> interfaceTransfer;

    @Shadow
    public Map<Direction, PipeConnection> interfaces;

    @Inject(method = "cacheFlows", at = @At("HEAD"))
    private static void cacheCentrifugalPumpFlows(LevelAccessor world, BlockPos pos, CallbackInfo ci) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof CentrifugalPumpBlockEntity) {
            FluidTransportBehaviour behaviour = BlockEntityBehaviour.get(world, pos, FluidTransportBehaviour.TYPE);
            if (behaviour != null && behaviour.interfaces != null) {
                Map<BlockPos, Map<Direction, PipeConnection>> cache = interfaceTransfer.get(world);
                cache.put(pos, behaviour.interfaces);
            }
        }
    }

    @Inject(method = "loadFlows", at = @At("HEAD"))
    private static void loadCentrifugalPumpFlows(LevelAccessor world, BlockPos pos, CallbackInfo ci) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof CentrifugalPumpBlockEntity) {
            FluidTransportBehaviour behaviour = BlockEntityBehaviour.get(world, pos, FluidTransportBehaviour.TYPE);
            if (behaviour != null) {
                Map<BlockPos, Map<Direction, PipeConnection>> cache = interfaceTransfer.get(world);
                Map<Direction, PipeConnection> interfaces = cache.remove(pos);
                if (interfaces != null) {
                    behaviour.interfaces = interfaces;
                }
            }
        }
    }
}