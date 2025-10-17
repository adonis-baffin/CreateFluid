package com.adonis.fluid.mixin;

import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlock;
import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlockEntity;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.*;

@Mixin(value = FluidPropagator.class, remap = false, priority = 1145)
public class FluidPropagatorMixin {

    @Unique
    private static final ThreadLocal<Set<Pair<CentrifugalPumpBlockEntity, Direction>>> centrifugalPumpsToUpdate =
            ThreadLocal.withInitial(HashSet::new);

    @Inject(method = "propagateChangedPipe", at = @At("HEAD"))
    private static void clearCentrifugalPumps(LevelAccessor world, BlockPos pipePos, BlockState pipeState, CallbackInfo ci) {
        centrifugalPumpsToUpdate.get().clear();
    }

    // 使用混淆后的方法名 m_7702_ (getBlockEntity 的 SRG 名)
    @Inject(method = "propagateChangedPipe",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelAccessor;m_7702_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
                    shift = At.Shift.AFTER),
            locals = LocalCapture.CAPTURE_FAILHARD)
    private static void detectCentrifugalPump(
            LevelAccessor world,
            BlockPos pipePos,
            BlockState pipeState,
            CallbackInfo ci,
            List<Pair<Integer, BlockPos>> frontier,
            Set<BlockPos> visited,
            Set<Pair<PumpBlockEntity, Direction>> discoveredPumps,
            Pair<Integer, BlockPos> pair,
            BlockPos currentPos,
            BlockState currentState,
            FluidTransportBehaviour pipe,
            Iterator<Direction> iterator,
            Direction direction,
            BlockPos target
    ) {
        BlockEntity blockEntity = world.getBlockEntity(target);
        BlockState targetState = world.getBlockState(target);

        if (blockEntity instanceof CentrifugalPumpBlockEntity centrifugalPump) {
            if (targetState.getBlock() instanceof CentrifugalPumpBlock) {
                if (CentrifugalPumpBlock.isOpenAt(targetState, direction.getOpposite())) {
                    centrifugalPumpsToUpdate.get().add(Pair.of(centrifugalPump, direction.getOpposite()));
                }
            }
        }
    }

    @Inject(method = "propagateChangedPipe", at = @At("RETURN"))
    private static void notifyCentrifugalPumps(LevelAccessor world, BlockPos pipePos, BlockState pipeState, CallbackInfo ci) {
        centrifugalPumpsToUpdate.get().forEach(pair -> {
            pair.getFirst().updatePipesOnSide(pair.getSecond());
        });
        centrifugalPumpsToUpdate.get().clear();
    }

    @Inject(method = "validateNeighbourChange", at = @At("HEAD"), cancellable = true)
    private static void recognizeCentrifugalPump(BlockState state, Level world, BlockPos pos,
                                                 Block otherBlock, BlockPos neighborPos, boolean isMoving,
                                                 CallbackInfoReturnable<Direction> cir) {
        if (world.isClientSide) {
            return;
        }

        Block actualBlock = world.getBlockState(neighborPos).getBlock();
        if (actualBlock instanceof CentrifugalPumpBlock) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "isOpenEnd", at = @At("HEAD"), cancellable = true)
    private static void checkCentrifugalPumpEnd(BlockGetter reader,
                                                BlockPos pos, Direction side, CallbackInfoReturnable<Boolean> cir) {
        BlockPos connectedPos = pos.relative(side);
        BlockState connectedState = reader.getBlockState(connectedPos);

        if (connectedState.getBlock() instanceof CentrifugalPumpBlock) {
            if (CentrifugalPumpBlock.isOpenAt(connectedState, side.getOpposite())) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "getStraightPipeAxis", at = @At("HEAD"), cancellable = true)
    private static void handleCentrifugalPumpAxis(BlockState state,
                                                  CallbackInfoReturnable<Direction.Axis> cir) {
        if (state.getBlock() instanceof CentrifugalPumpBlock) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "getPipe", at = @At("RETURN"), cancellable = true)
    private static void includeCentrifugalPump(BlockGetter reader, BlockPos pos,
                                               CallbackInfoReturnable<FluidTransportBehaviour> cir) {
        if (cir.getReturnValue() == null) {
            BlockEntity be = reader.getBlockEntity(pos);
            if (be instanceof CentrifugalPumpBlockEntity) {
                FluidTransportBehaviour behaviour = BlockEntityBehaviour.get(reader, pos, FluidTransportBehaviour.TYPE);
                cir.setReturnValue(behaviour);
            }
        }
    }
}




