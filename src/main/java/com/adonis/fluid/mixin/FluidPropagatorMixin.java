package com.adonis.fluid.mixin;

import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlock;
import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlockEntity;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(value = FluidPropagator.class, remap = false)
public class FluidPropagatorMixin {

    // 线程局部存储，用于传递发现的离心泵
    private static final ThreadLocal<Set<Pair<CentrifugalPumpBlockEntity, Direction>>> discoveredCentrifugalPumps =
            ThreadLocal.withInitial(HashSet::new);

    // 在propagateChangedPipe开始时清空集合
    @Inject(method = "propagateChangedPipe", at = @At("HEAD"))
    private static void clearCentrifugalPumps(LevelAccessor world, BlockPos pipePos, BlockState pipeState, CallbackInfo ci) {
        discoveredCentrifugalPumps.get().clear();
    }

    // 在检查BlockEntity时也检查离心泵
    @Inject(method = "propagateChangedPipe",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelAccessor;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
                    shift = At.Shift.AFTER))
    private static void detectCentrifugalPump(LevelAccessor world, BlockPos pipePos, BlockState pipeState, CallbackInfo ci) {
        // 这个方法会在每次getBlockEntity调用后执行
        // 我们需要检查周围的所有方块
    }

    // 在方法结束前，通知所有发现的离心泵
    @Inject(method = "propagateChangedPipe",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/Set;forEach(Ljava/util/function/Consumer;)V",
                    shift = At.Shift.AFTER))
    private static void notifyCentrifugalPumps(LevelAccessor world, BlockPos pipePos, BlockState pipeState, CallbackInfo ci) {
        // 通知所有发现的离心泵
        discoveredCentrifugalPumps.get().forEach(pair -> {
            pair.getFirst().updatePipesOnSide(pair.getSecond());
        });
        discoveredCentrifugalPumps.get().clear();
    }

    // 修改validateNeighbourChange来识别离心泵
    @Inject(method = "validateNeighbourChange", at = @At("HEAD"), cancellable = true)
    private static void recognizeCentrifugalPump(BlockState state, Level world, BlockPos pos,
                                                 Block otherBlock, BlockPos neighborPos, boolean isMoving,
                                                 CallbackInfoReturnable<Direction> cir) {

        if (world.isClientSide) {
            return;
        }

        Block actualBlock = world.getBlockState(neighborPos).getBlock();

        // 如果邻居是离心泵，返回null让它正常工作
        if (actualBlock instanceof CentrifugalPumpBlock) {
            cir.setReturnValue(null);
        }
    }

    // 修改isOpenEnd来识别离心泵
    @Inject(method = "isOpenEnd", at = @At("HEAD"), cancellable = true)
    private static void checkCentrifugalPumpEnd(net.minecraft.world.level.BlockGetter reader,
                                                BlockPos pos, Direction side, CallbackInfoReturnable<Boolean> cir) {

        BlockPos connectedPos = pos.relative(side);
        BlockState connectedState = reader.getBlockState(connectedPos);

        if (connectedState.getBlock() instanceof CentrifugalPumpBlock) {
            if (CentrifugalPumpBlock.isOpenAt(connectedState, side.getOpposite())) {
                cir.setReturnValue(false);
            }
        }
    }

    // 修改getStraightPipeAxis来处理离心泵
    @Inject(method = "getStraightPipeAxis", at = @At("HEAD"), cancellable = true)
    private static void handleCentrifugalPumpAxis(BlockState state,
                                                  CallbackInfoReturnable<Direction.Axis> cir) {

        if (state.getBlock() instanceof CentrifugalPumpBlock) {
            // 离心泵没有直轴
            cir.setReturnValue(null);
        }
    }
}