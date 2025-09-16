package com.adonis.fluid.mixin;

import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlock;
import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlockEntity;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.HashSet;
import java.util.Set;

@Mixin(value = FluidPropagator.class, remap = false)
public class FluidPropagatorAccessorMixin {

    // 修改 discoveredPumps 集合的处理，加入离心泵
    @ModifyVariable(
        method = "propagateChangedPipe",
        at = @At(value = "INVOKE", target = "Ljava/util/Set;forEach(Ljava/util/function/Consumer;)V"),
        ordinal = 2
    )
    private static Set<Pair<PumpBlockEntity, Direction>> addCentrifugalPumps(
            Set<Pair<PumpBlockEntity, Direction>> discoveredPumps, 
            LevelAccessor world, BlockPos pipePos, BlockState pipeState) {
        
        // 创建一个新的集合来包含离心泵
        Set<Pair<Object, Direction>> allPumps = new HashSet<>();
        
        // 遍历周围的方块，寻找离心泵
        for (Direction dir : Direction.values()) {
            BlockPos targetPos = pipePos.relative(dir);
            BlockEntity be = world.getBlockEntity(targetPos);
            
            if (be instanceof CentrifugalPumpBlockEntity centrifugalPump) {
                BlockState targetState = world.getBlockState(targetPos);
                if (CentrifugalPumpBlock.isOpenAt(targetState, dir.getOpposite())) {
                    // 需要通知离心泵更新
                    centrifugalPump.updatePipesOnSide(dir.getOpposite());
                }
            }
        }
        
        return discoveredPumps;
    }
}