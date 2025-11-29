package com.adonis.fluid.mixin.compat;

import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlock;
import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlockEntity;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.AxisPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.data.Iterate;
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

import java.util.HashSet;
import java.util.Set;

/**
 * 这个 Mixin 用于与 TFMG 兼容
 * 优先级设置为 1200，高于 TFMG 的默认优先级
 * 这样即使 TFMG 的 Overwrite 先执行，我们的 Inject 也能在其后补充离心泵逻辑
 */
@Mixin(value = FluidPropagator.class, remap = false, priority = 1200)
public class FluidPropagatorMixin_TFMGCompat {

    @Unique
    private static final ThreadLocal<Set<Pair<CentrifugalPumpBlockEntity, Direction>>> fluid$centrifugalPumpsToUpdate =
            ThreadLocal.withInitial(HashSet::new);

    /**
     * 在 propagateChangedPipe 开始时清空离心泵列表
     */
    @Inject(method = "propagateChangedPipe", at = @At("HEAD"))
    private static void fluid$clearCentrifugalPumps(LevelAccessor world, BlockPos pipePos, BlockState pipeState, CallbackInfo ci) {
        fluid$centrifugalPumpsToUpdate.get().clear();
    }

    /**
     * 在 propagateChangedPipe 结束时通知所有发现的离心泵
     */
    @Inject(method = "propagateChangedPipe", at = @At("RETURN"))
    private static void fluid$notifyCentrifugalPumps(LevelAccessor world, BlockPos pipePos, BlockState pipeState, CallbackInfo ci) {
        // 手动搜索连接的离心泵（因为 TFMG 的 Overwrite 不会检测我们的泵）
        fluid$scanForCentrifugalPumps(world, pipePos, pipeState);
        
        fluid$centrifugalPumpsToUpdate.get().forEach(pair -> {
            pair.getFirst().updatePipesOnSide(pair.getSecond());
        });
        fluid$centrifugalPumpsToUpdate.get().clear();
    }

    @Unique
    private static void fluid$scanForCentrifugalPumps(LevelAccessor world, BlockPos pipePos, BlockState pipeState) {
        Set<BlockPos> visited = new HashSet<>();
        fluid$scanRecursive(world, pipePos, visited, 0);
    }

    @Unique
    private static void fluid$scanRecursive(LevelAccessor world, BlockPos currentPos, Set<BlockPos> visited, int depth) {
        if (depth > 64 || visited.contains(currentPos))
            return;
        if (world instanceof Level level && !level.isLoaded(currentPos))
            return;
            
        visited.add(currentPos);
        
        BlockState currentState = world.getBlockState(currentPos);
        FluidTransportBehaviour pipe = BlockEntityBehaviour.get(world, currentPos, FluidTransportBehaviour.TYPE);
        
        // 检查六个方向
        for (Direction direction : Iterate.directions) {
            BlockPos target = currentPos.relative(direction);
            if (world instanceof Level level && !level.isLoaded(target))
                continue;

            BlockEntity tileEntity = world.getBlockEntity(target);
            BlockState targetState = world.getBlockState(target);

            // 检查是否是离心泵
            if (tileEntity instanceof CentrifugalPumpBlockEntity centrifugalPump) {
                if (targetState.getBlock() instanceof CentrifugalPumpBlock) {
                    if (CentrifugalPumpBlock.isOpenAt(targetState, direction.getOpposite())) {
                        fluid$centrifugalPumpsToUpdate.get().add(Pair.of(centrifugalPump, direction.getOpposite()));
                    }
                }
                continue;
            }

            // 如果是管道，继续递归搜索
            if (pipe != null && pipe.canHaveFlowToward(currentState, direction)) {
                FluidTransportBehaviour targetPipe = BlockEntityBehaviour.get(world, target, FluidTransportBehaviour.TYPE);
                if (targetPipe != null && !visited.contains(target)) {
                    fluid$scanRecursive(world, target, visited, depth + 1);
                }
            }
        }
    }

    /**
     * 让 validateNeighbourChange 识别离心泵
     */
    @Inject(method = "validateNeighbourChange", at = @At("HEAD"), cancellable = true)
    private static void fluid$recognizeCentrifugalPump(BlockState state, Level world, BlockPos pos,
                                                        Block otherBlock, BlockPos neighborPos, boolean isMoving,
                                                        CallbackInfoReturnable<Direction> cir) {
        if (world.isClientSide)
            return;

        BlockState neighborState = world.getBlockState(neighborPos);
        if (neighborState.getBlock() instanceof CentrifugalPumpBlock) {
            cir.setReturnValue(null);
        }
        
        if (otherBlock instanceof CentrifugalPumpBlock) {
            cir.setReturnValue(null);
        }
    }

    /**
     * 让 isOpenEnd 识别离心泵连接
     */
    @Inject(method = "isOpenEnd", at = @At("HEAD"), cancellable = true)
    private static void fluid$checkCentrifugalPumpEnd(BlockGetter reader, BlockPos pos, Direction side,
                                                       CallbackInfoReturnable<Boolean> cir) {
        BlockPos connectedPos = pos.relative(side);
        BlockState connectedState = reader.getBlockState(connectedPos);

        if (connectedState.getBlock() instanceof CentrifugalPumpBlock) {
            if (CentrifugalPumpBlock.isOpenAt(connectedState, side.getOpposite())) {
                cir.setReturnValue(false);
            }
        }
    }

    /**
     * 让 getStraightPipeAxis 识别离心泵
     */
    @Inject(method = "getStraightPipeAxis", at = @At("HEAD"), cancellable = true)
    private static void fluid$handleCentrifugalPumpAxis(BlockState state,
                                                         CallbackInfoReturnable<Direction.Axis> cir) {
        if (state.getBlock() instanceof CentrifugalPumpBlock) {
            cir.setReturnValue(null);
        }
    }

    /**
     * 让 getPipe 能获取离心泵的 FluidTransportBehaviour
     */
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