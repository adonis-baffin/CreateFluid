package com.adonis.fluid.mixin;

import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlock;
import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlockEntity;
import com.adonis.fluid.compat.TFMGCompat;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllTags;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.AxisPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.VanillaFluidTargets;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.advancement.CreateAdvancement;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.BlockHelper;
import com.simibubi.create.infrastructure.config.AllConfigs;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 这个 Mixin 的优先级设置为 900，低于 TFMG 的 1000（默认）
 * 这样当 TFMG 存在时，TFMG 的 Overwrite 会覆盖这个
 * 我们通过 FluidPropagatorMixin_TFMGCompat 来处理 TFMG 存在时的情况
 */
@Mixin(value = FluidPropagator.class, remap = false, priority = 900)
public class FluidPropagatorMixin {

    @Shadow
    public static CreateAdvancement[] getSharedTriggers() {
        return new CreateAdvancement[] { AllAdvancements.WATER_SUPPLY, AllAdvancements.CROSS_STREAMS,
                AllAdvancements.HONEY_DRAIN };
    }

    /**
     * @author Adonis
     * @reason Add support for CentrifugalPump in fluid network propagation
     */
    @Overwrite
    public static void propagateChangedPipe(LevelAccessor world, BlockPos pipePos, BlockState pipeState) {
        List<Pair<Integer, BlockPos>> frontier = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<Pair<PumpBlockEntity, Direction>> discoveredPumps = new HashSet<>();
        Set<Pair<CentrifugalPumpBlockEntity, Direction>> discoveredCentrifugalPumps = new HashSet<>();

        frontier.add(Pair.of(0, pipePos));

        while (!frontier.isEmpty()) {
            Pair<Integer, BlockPos> pair = frontier.remove(0);
            BlockPos currentPos = pair.getSecond();
            if (visited.contains(currentPos))
                continue;
            visited.add(currentPos);
            BlockState currentState = currentPos.equals(pipePos) ? pipeState : world.getBlockState(currentPos);
            FluidTransportBehaviour pipe = getPipe(world, currentPos);
            if (pipe == null)
                continue;
            pipe.wipePressure();

            for (Direction direction : getPipeConnections(currentState, pipe)) {
                BlockPos target = currentPos.relative(direction);
                if (world instanceof Level l && !l.isLoaded(target))
                    continue;

                BlockEntity tileEntity = world.getBlockEntity(target);
                BlockState targetState = world.getBlockState(target);

                // 检查离心泵
                if (tileEntity instanceof CentrifugalPumpBlockEntity centrifugalPump) {
                    if (targetState.getBlock() instanceof CentrifugalPumpBlock) {
                        if (CentrifugalPumpBlock.isOpenAt(targetState, direction.getOpposite())) {
                            discoveredCentrifugalPumps.add(Pair.of(centrifugalPump, direction.getOpposite()));
                        }
                    }
                    continue;
                }

                // 原有的机械泵检查
                if (tileEntity instanceof PumpBlockEntity) {
                    if (!AllBlocks.MECHANICAL_PUMP.has(targetState)
                            || targetState.getValue(PumpBlock.FACING).getAxis() != direction.getAxis())
                        continue;
                    discoveredPumps.add(Pair.of((PumpBlockEntity) tileEntity, direction.getOpposite()));
                    continue;
                }

                if (visited.contains(target))
                    continue;
                FluidTransportBehaviour targetPipe = getPipe(world, target);
                if (targetPipe == null)
                    continue;
                Integer distance = pair.getFirst();
                if (distance >= getPumpRange() && !targetPipe.hasAnyPressure())
                    continue;
                if (targetPipe.canHaveFlowToward(targetState, direction.getOpposite()))
                    frontier.add(Pair.of(distance + 1, target));
            }
        }

        discoveredPumps.forEach(p -> p.getFirst().updatePipesOnSide(p.getSecond()));
        discoveredCentrifugalPumps.forEach(p -> p.getFirst().updatePipesOnSide(p.getSecond()));
    }

    /**
     * @author Adonis
     * @reason Add support for CentrifugalPump in neighbor change validation
     */
    @Overwrite
    @Nullable
    public static Direction validateNeighbourChange(BlockState state, Level world, BlockPos pos, Block otherBlock,
                                                    BlockPos neighborPos, boolean isMoving) {
        if (world.isClientSide)
            return null;

        BlockState neighborState = world.getBlockState(neighborPos);
        if (neighborState.getBlock() instanceof CentrifugalPumpBlock)
            return null;

        if (otherBlock instanceof FluidPipeBlock)
            return null;
        if (otherBlock instanceof AxisPipeBlock)
            return null;
        if (otherBlock instanceof PumpBlock)
            return null;
        if (otherBlock instanceof CentrifugalPumpBlock)
            return null;

        for (Direction d : Iterate.directions) {
            if (!pos.relative(d).equals(neighborPos))
                continue;
            return d;
        }
        return null;
    }

    /**
     * @author Adonis
     * @reason Add support for CentrifugalPump in open end detection
     */
    @Overwrite
    public static boolean isOpenEnd(BlockGetter reader, BlockPos pos, Direction side) {
        BlockPos connectedPos = pos.relative(side);
        BlockState connectedState = reader.getBlockState(connectedPos);

        // 检查离心泵
        if (connectedState.getBlock() instanceof CentrifugalPumpBlock) {
            if (CentrifugalPumpBlock.isOpenAt(connectedState, side.getOpposite())) {
                return false;
            }
        }

        FluidTransportBehaviour pipe = FluidPropagator.getPipe(reader, connectedPos);
        if (pipe != null && pipe.canHaveFlowToward(connectedState, side.getOpposite()))
            return false;
        if (PumpBlock.isPump(connectedState) && connectedState.getValue(PumpBlock.FACING)
                .getAxis() == side.getAxis())
            return false;
        if (VanillaFluidTargets.canProvideFluidWithoutCapability(connectedState))
            return true;
        if (BlockHelper.hasBlockSolidSide(connectedState, reader, connectedPos, side.getOpposite())
                && !AllTags.AllBlockTags.FAN_TRANSPARENT.matches(connectedState))
            return false;
        if (hasFluidCapability(reader, connectedPos, side.getOpposite()))
            return false;
        if (!(connectedState.canBeReplaced() && connectedState.getDestroySpeed(reader, connectedPos) != -1)
                && !connectedState.hasProperty(BlockStateProperties.WATERLOGGED))
            return false;
        return true;
    }

    /**
     * @author Adonis
     * @reason Add support for CentrifugalPump in straight pipe axis detection
     */
    @Overwrite
    @Nullable
    public static Direction.Axis getStraightPipeAxis(BlockState state) {
        if (state.getBlock() instanceof CentrifugalPumpBlock)
            return null;

        if (state.getBlock() instanceof PumpBlock)
            return state.getValue(PumpBlock.FACING).getAxis();
        if (state.getBlock() instanceof AxisPipeBlock)
            return state.getValue(AxisPipeBlock.AXIS);
        if (!FluidPipeBlock.isPipe(state))
            return null;
        Direction.Axis axisFound = null;
        int connections = 0;
        for (Direction.Axis axis : Iterate.axes) {
            Direction d1 = Direction.get(Direction.AxisDirection.NEGATIVE, axis);
            Direction d2 = Direction.get(Direction.AxisDirection.POSITIVE, axis);
            boolean openAt1 = FluidPipeBlock.isOpenAt(state, d1);
            boolean openAt2 = FluidPipeBlock.isOpenAt(state, d2);
            if (openAt1)
                connections++;
            if (openAt2)
                connections++;
            if (openAt1 && openAt2)
                if (axisFound != null)
                    return null;
                else
                    axisFound = axis;
        }
        return connections == 2 ? axisFound : null;
    }

    /**
     * @author Adonis
     * @reason Add support for CentrifugalPump in pipe retrieval
     */
    @Overwrite
    @Nullable
    public static FluidTransportBehaviour getPipe(BlockGetter reader, BlockPos pos) {
        return BlockEntityBehaviour.get(reader, pos, FluidTransportBehaviour.TYPE);
    }

    @Shadow
    public static List<Direction> getPipeConnections(BlockState state, FluidTransportBehaviour pipe) {
        List<Direction> list = new ArrayList<>();
        for (Direction d : Iterate.directions)
            if (pipe.canHaveFlowToward(state, d))
                list.add(d);
        return list;
    }

    @Shadow
    public static int getPumpRange() {
        return AllConfigs.server().fluids.mechanicalPumpRange.get();
    }

    @Shadow
    public static boolean hasFluidCapability(BlockGetter world, BlockPos pos, Direction side) {
        BlockEntity tileEntity = world.getBlockEntity(pos);
        if (tileEntity == null)
            return false;
        LazyOptional<IFluidHandler> capability =
                tileEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, side);
        return capability.isPresent();
    }

    @Shadow
    public static void resetAffectedFluidNetworks(Level world, BlockPos start, Direction side) {
    }
}