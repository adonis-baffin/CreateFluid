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

/**
 * FluidPropagator 的 Mixin
 * 优先级 1145，在 TFMG (默认 1000) 之后执行
 */
@Mixin(value = FluidPropagator.class, remap = false, priority = 1145)
public class FluidPropagatorMixin {

    @Unique
    private static final ThreadLocal<Set<Pair<CentrifugalPumpBlockEntity, Direction>>> fluid$centrifugalPumpsToUpdate =
            ThreadLocal.withInitial(HashSet::new);

    @Inject(method = "propagateChangedPipe", at = @At("HEAD"))
    private static void fluid$clearCentrifugalPumps(LevelAccessor world, BlockPos pipePos, BlockState pipeState, CallbackInfo ci) {
        fluid$centrifugalPumpsToUpdate.get().clear();
    }

    /**
     * 在 BFS 循环中检测离心泵
     *
     * 这个注入点在 world.getBlockEntity(target) 调用之后
     * 使用 remap = true 让 Mixin 系统自动处理方法名映射
     */
    @Inject(method = "propagateChangedPipe",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelAccessor;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
                    shift = At.Shift.AFTER,
                    remap = true),
            locals = LocalCapture.CAPTURE_FAILSOFT,
            remap = false)
    private static void fluid$detectCentrifugalPump(
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
        if (world instanceof Level level && !level.isLoaded(target)) {
            return;
        }

        BlockEntity blockEntity = world.getBlockEntity(target);
        BlockState targetState = world.getBlockState(target);

        if (blockEntity instanceof CentrifugalPumpBlockEntity centrifugalPump) {
            if (targetState.getBlock() instanceof CentrifugalPumpBlock) {
                if (CentrifugalPumpBlock.isOpenAt(targetState, direction.getOpposite())) {
                    fluid$centrifugalPumpsToUpdate.get().add(Pair.of(centrifugalPump, direction.getOpposite()));
                }
            }
        }
    }

    @Inject(method = "propagateChangedPipe", at = @At("RETURN"))
    private static void fluid$notifyCentrifugalPumps(LevelAccessor world, BlockPos pipePos, BlockState pipeState, CallbackInfo ci) {
        fluid$centrifugalPumpsToUpdate.get().forEach(pair -> {
            pair.getFirst().updatePipesOnSide(pair.getSecond());
        });
        fluid$centrifugalPumpsToUpdate.get().clear();
    }

    @Inject(method = "validateNeighbourChange", at = @At("HEAD"), cancellable = true)
    private static void fluid$recognizeCentrifugalPump(BlockState state, Level world, BlockPos pos,
                                                       Block otherBlock, BlockPos neighborPos, boolean isMoving,
                                                       CallbackInfoReturnable<Direction> cir) {
        if (world.isClientSide) {
            return;
        }

        Block actualBlock = world.getBlockState(neighborPos).getBlock();
        if (actualBlock instanceof CentrifugalPumpBlock) {
            cir.setReturnValue(null);
        }

        if (otherBlock instanceof CentrifugalPumpBlock) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "isOpenEnd", at = @At("HEAD"), cancellable = true)
    private static void fluid$checkCentrifugalPumpEnd(BlockGetter reader,
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
    private static void fluid$handleCentrifugalPumpAxis(BlockState state,
                                                        CallbackInfoReturnable<Direction.Axis> cir) {
        if (state.getBlock() instanceof CentrifugalPumpBlock) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "getPipe", at = @At("RETURN"), cancellable = true)
    private static void fluid$includeCentrifugalPump(BlockGetter reader, BlockPos pos,
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