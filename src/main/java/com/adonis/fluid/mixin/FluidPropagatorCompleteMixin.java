package com.adonis.fluid.mixin;

import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlock;
import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlockEntity;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.*;

@Mixin(value = FluidPropagator.class, remap = false)
public class FluidPropagatorCompleteMixin {

    @Unique
    private static final Set<Pair<CentrifugalPumpBlockEntity, Direction>> centrifugalPumpsToUpdate = new HashSet<>();

    // 完全重写propagateChangedPipe的核心循环部分
    @Inject(method = "propagateChangedPipe",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelAccessor;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"),
            locals = LocalCapture.CAPTURE_FAILHARD)
    private static void checkAllPumpTypes(
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
            Direction direction,
            BlockPos target
    ) {
        BlockEntity blockEntity = world.getBlockEntity(target);
        if (blockEntity == null) return;

        BlockState targetState = world.getBlockState(target);

        // 先检查是否是离心泵方块
        if (!(targetState.getBlock() instanceof CentrifugalPumpBlock)) {
            return;
        }

        // 检查离心泵
        if (blockEntity instanceof CentrifugalPumpBlockEntity centrifugalPump) {
            // 安全地获取方向
            try {
                Direction primary = CentrifugalPumpBlock.getPrimaryFluidDirection(targetState);
                Direction secondary = CentrifugalPumpBlock.getSecondaryFluidDirection(targetState);

                if (direction.getAxis() == primary.getAxis() || direction.getAxis() == secondary.getAxis()) {
                    centrifugalPumpsToUpdate.add(Pair.of(centrifugalPump, direction.getOpposite()));
                }
            } catch (IllegalArgumentException e) {
                // 如果状态无效，忽略这个方块
                return;
            }
        }
    }

    // 在原方法结束时，更新所有离心泵
    @Inject(method = "propagateChangedPipe", at = @At("RETURN"))
    private static void updateAllCentrifugalPumps(LevelAccessor world, BlockPos pipePos, BlockState pipeState, CallbackInfo ci) {
        centrifugalPumpsToUpdate.forEach(pair -> {
            try {
                pair.getFirst().updatePipesOnSide(pair.getSecond());
            } catch (Exception e) {
                // 防止任何异常导致崩溃
                e.printStackTrace();
            }
        });
        centrifugalPumpsToUpdate.clear();
    }
}