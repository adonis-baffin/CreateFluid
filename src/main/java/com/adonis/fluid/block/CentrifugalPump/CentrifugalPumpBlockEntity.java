package com.adonis.fluid.block.CentrifugalPump;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.apache.commons.lang3.mutable.MutableBoolean;

import javax.annotation.Nullable;
import java.util.*;

public class CentrifugalPumpBlockEntity extends KineticBlockEntity {

    // 传输方向控制
    protected ScrollOptionBehaviour<TransferDirection> transferDirection;

    // 两侧的更新状态
    Couple<MutableBoolean> sidesToUpdate = Couple.create(MutableBoolean::new);
    boolean pressureUpdate;

    // 传输参数
    private static final int BASE_PUMP_RANGE = 20; // 固定传输距离20格
    private static final float SPEED_MULTIPLIER = 2.0f; // 速度倍率（相对于普通泵）
    private static final float STRESS_IMPACT = 8.0f; // 应力影响倍率

    // 传输方向枚举
    public enum TransferDirection implements INamedIconOptions {
        NORMAL(AllIcons.I_CONFIRM),      // 正常方向
        REVERSED(AllIcons.I_ROTATE_CCW); // 反向

        private String translationKey;
        private AllIcons icon;

        private TransferDirection(AllIcons icon) {
            this.icon = icon;
            this.translationKey = "create_fluid.pump.transfer_direction." + Lang.asId(this.name());
        }

        @Override
        public AllIcons getIcon() {
            return this.icon;
        }

        @Override
        public String getTranslationKey() {
            return this.translationKey;
        }

        public Component getDisplayName() {
            return Component.translatable(this.translationKey);
        }
    }

    public CentrifugalPumpBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        // 添加流体传输行为
        behaviours.add(new CentrifugalPumpFluidTransferBehaviour(this));

        // 添加方向控制行为（通过侧面面板）
        transferDirection = new ScrollOptionBehaviour<>(TransferDirection.class,
                Component.translatable("create_fluid.pump.transfer_direction"),
                this, new CentrifugalPumpValueBoxTransform());
        transferDirection.requiresWrench();  // 需要扳手才能调整
        transferDirection.withCallback(i -> onDirectionChanged());
        behaviours.add(transferDirection);

        // 注册成就
        registerAwardables(behaviours, FluidPropagator.getSharedTriggers());
        registerAwardables(behaviours, AllAdvancements.PUMP);
    }

    private void onDirectionChanged() {
        if (!level.isClientSide || isVirtual()) {
            updatePressureChange();
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (!level.isClientSide || isVirtual()) {
            Direction primary = getFront();
            Direction secondary = getSecondaryFront();

            if (primary != null && secondary != null) {
                MutableBoolean primaryUpdate = sidesToUpdate.getFirst();
                if (!primaryUpdate.isFalse()) {
                    primaryUpdate.setFalse();
                    distributePressureTo(primary);
                }

                MutableBoolean secondaryUpdate = sidesToUpdate.getSecond();
                if (!secondaryUpdate.isFalse()) {
                    secondaryUpdate.setFalse();
                    distributePressureTo(secondary);
                }
            }
        }
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);

        if (Math.abs(previousSpeed) != Math.abs(getSpeed())) {
            if (speed != 0) {
                award(AllAdvancements.PUMP);
            }

            if (!level.isClientSide || isVirtual()) {
                updatePressureChange();
            }
        }
    }

    // 不要重写这些方法！让KineticBlockEntity从应力配置中自动获取值
    // 删除 calculateStressApplied() 和 calculateAddedStressCapacity() 的重写

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        // 调用父类方法，这会自动显示应力信息（从STRESS_CONFIG获取）
        return super.addToGoggleTooltip(tooltip, isPlayerSneaking);
    }

    public void updatePressureChange() {
        pressureUpdate = false;

        Direction primary = getFront();
        Direction secondary = getSecondaryFront();
        if (primary == null || secondary == null) return;

        BlockPos primaryPos = worldPosition.relative(primary);
        BlockPos secondaryPos = worldPosition.relative(secondary);

        FluidPropagator.propagateChangedPipe(level, primaryPos, level.getBlockState(primaryPos));
        FluidPropagator.propagateChangedPipe(level, secondaryPos, level.getBlockState(secondaryPos));

        FluidTransportBehaviour behaviour = getBehaviour(FluidTransportBehaviour.TYPE);
        if (behaviour != null) {
            behaviour.wipePressure();
        }

        sidesToUpdate.forEach(MutableBoolean::setTrue);
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);

        if (compound.contains("TransferDirection") && transferDirection != null) {
            try {
                int ordinal = TransferDirection.valueOf(compound.getString("TransferDirection")).ordinal();
                transferDirection.setValue(ordinal);
            } catch (Exception e) {
                transferDirection.setValue(0);
            }
        }
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);

        if (transferDirection != null) {
            compound.putString("TransferDirection", transferDirection.get().name());
        }
    }

    protected void distributePressureTo(Direction side) {
        if (getSpeed() == 0) return;

        BlockFace start = new BlockFace(worldPosition, side);
        boolean isPrimary = side == getFront();
        boolean pull = isPullingOnSide(isPrimary);

        Set<BlockFace> targets = new HashSet<>();
        Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph = new HashMap<>();

        if (!pull) {
            FluidPropagator.resetAffectedFluidNetworks(level, worldPosition, side.getOpposite());
        }

        int maxDistance = BASE_PUMP_RANGE;

        if (!hasReachedValidEndpoint(level, start, pull)) {
            pipeGraph.computeIfAbsent(worldPosition, $ -> Pair.of(0, new IdentityHashMap<>()))
                    .getSecond().put(side, pull);
            pipeGraph.computeIfAbsent(start.getConnectedPos(), $ -> Pair.of(1, new IdentityHashMap<>()))
                    .getSecond().put(side.getOpposite(), !pull);

            List<Pair<Integer, BlockPos>> frontier = new ArrayList<>();
            Set<BlockPos> visited = new HashSet<>();
            frontier.add(Pair.of(1, start.getConnectedPos()));

            while (!frontier.isEmpty()) {
                Pair<Integer, BlockPos> entry = frontier.remove(0);
                int distance = entry.getFirst();
                BlockPos currentPos = entry.getSecond();

                if (!level.isLoaded(currentPos) || visited.contains(currentPos)) continue;
                visited.add(currentPos);

                BlockState currentState = level.getBlockState(currentPos);
                FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, currentPos);
                if (pipe == null) continue;

                for (Direction face : FluidPropagator.getPipeConnections(currentState, pipe)) {
                    BlockFace blockFace = new BlockFace(currentPos, face);
                    BlockPos connectedPos = blockFace.getConnectedPos();

                    if (!level.isLoaded(connectedPos) || blockFace.isEquivalent(start)) continue;

                    if (hasReachedValidEndpoint(level, blockFace, pull)) {
                        pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>()))
                                .getSecond().put(face, pull);
                        targets.add(blockFace);
                    } else {
                        FluidTransportBehaviour pipeBehaviour = FluidPropagator.getPipe(level, connectedPos);
                        if (pipeBehaviour != null && !(pipeBehaviour instanceof CentrifugalPumpFluidTransferBehaviour)
                                && !visited.contains(connectedPos)) {
                            if (distance + 1 >= maxDistance) {
                                pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>()))
                                        .getSecond().put(face, pull);
                                targets.add(blockFace);
                            } else {
                                pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>()))
                                        .getSecond().put(face, pull);
                                pipeGraph.computeIfAbsent(connectedPos, $ -> Pair.of(distance + 1, new IdentityHashMap<>()))
                                        .getSecond().put(face.getOpposite(), !pull);
                                frontier.add(Pair.of(distance + 1, connectedPos));
                            }
                        }
                    }
                }
            }
        }

        Map<Integer, Set<BlockFace>> validFaces = new HashMap<>();
        searchForEndpointRecursively(pipeGraph, targets, validFaces,
                new BlockFace(start.getPos(), start.getOppositeFace()), pull);

        float pressure = Math.abs(getSpeed()) * SPEED_MULTIPLIER;

        for (Set<BlockFace> set : validFaces.values()) {
            int branches = Math.max(1, set.size() - 1);
            for (BlockFace face : set) {
                BlockPos pipePos = face.getPos();
                Direction pipeSide = face.getFace();

                if (!pipePos.equals(worldPosition)) {
                    boolean inbound = pipeGraph.get(pipePos).getSecond().get(pipeSide);
                    FluidTransportBehaviour pipeBehaviour = FluidPropagator.getPipe(level, pipePos);
                    if (pipeBehaviour != null) {
                        pipeBehaviour.addPressure(pipeSide, inbound, pressure / branches);
                    }
                }
            }
        }
    }

    protected boolean searchForEndpointRecursively(Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph,
                                                   Set<BlockFace> targets, Map<Integer, Set<BlockFace>> validFaces,
                                                   BlockFace currentFace, boolean pull) {
        BlockPos currentPos = currentFace.getPos();
        if (!pipeGraph.containsKey(currentPos)) return false;

        Pair<Integer, Map<Direction, Boolean>> pair = pipeGraph.get(currentPos);
        int distance = pair.getFirst();
        boolean atLeastOneBranchSuccessful = false;

        for (Direction nextFacing : Iterate.directions) {
            if (nextFacing == currentFace.getFace()) continue;

            Map<Direction, Boolean> map = pair.getSecond();
            if (!map.containsKey(nextFacing)) continue;

            BlockFace localTarget = new BlockFace(currentPos, nextFacing);
            if (targets.contains(localTarget)) {
                validFaces.computeIfAbsent(distance, $ -> new HashSet<>()).add(localTarget);
                atLeastOneBranchSuccessful = true;
            } else if (map.get(nextFacing) == pull) {
                if (searchForEndpointRecursively(pipeGraph, targets, validFaces,
                        new BlockFace(currentPos.relative(nextFacing), nextFacing.getOpposite()), pull)) {
                    validFaces.computeIfAbsent(distance, $ -> new HashSet<>()).add(localTarget);
                    atLeastOneBranchSuccessful = true;
                }
            }
        }

        if (atLeastOneBranchSuccessful) {
            validFaces.computeIfAbsent(distance, $ -> new HashSet<>()).add(currentFace);
        }

        return atLeastOneBranchSuccessful;
    }

    private boolean hasReachedValidEndpoint(LevelAccessor world, BlockFace blockFace, boolean pull) {
        BlockPos connectedPos = blockFace.getConnectedPos();
        BlockState connectedState = world.getBlockState(connectedPos);
        BlockEntity blockEntity = world.getBlockEntity(connectedPos);
        Direction face = blockFace.getFace();

        if (PumpBlock.isPump(connectedState) && connectedState.getValue(PumpBlock.FACING).getAxis() == face.getAxis()) {
            if (blockEntity instanceof CentrifugalPumpBlockEntity pumpBE) {
                Direction pumpFront = pumpBE.getFront();
                if (pumpFront != null) {
                    boolean otherPull = pumpBE.isPullingOnSide(pumpFront == blockFace.getOppositeFace());
                    return otherPull != pull;
                }
            }
        }

        FluidTransportBehaviour pipe = FluidPropagator.getPipe(world, connectedPos);
        if (pipe != null && pipe.canHaveFlowToward(connectedState, blockFace.getOppositeFace())) {
            return false;
        }

        if (blockEntity != null) {
            LazyOptional<IFluidHandler> capability = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, face.getOpposite());
            if (capability.isPresent()) {
                return true;
            }
        }

        return FluidPropagator.isOpenEnd(world, blockFace.getPos(), face);
    }

    public void updatePipesOnSide(Direction side) {
        if (isSideAccessible(side)) {
            updatePipeNetwork(side == getFront());
            FluidTransportBehaviour behaviour = getBehaviour(FluidTransportBehaviour.TYPE);
            if (behaviour != null) {
                behaviour.wipePressure();
            }
        }
    }

    protected boolean isFront(Direction side) {
        BlockState blockState = getBlockState();
        if (!(blockState.getBlock() instanceof CentrifugalPumpBlock)) {
            return false;
        }

        return side == CentrifugalPumpBlock.getPrimaryFluidDirection(blockState) ||
                side == CentrifugalPumpBlock.getSecondaryFluidDirection(blockState);
    }

    @Nullable
    protected Direction getFront() {
        BlockState blockState = getBlockState();
        if (!(blockState.getBlock() instanceof CentrifugalPumpBlock)) {
            return null;
        }

        return CentrifugalPumpBlock.getPrimaryFluidDirection(blockState);
    }

    @Nullable
    protected Direction getSecondaryFront() {
        BlockState blockState = getBlockState();
        if (!(blockState.getBlock() instanceof CentrifugalPumpBlock)) {
            return null;
        }

        return CentrifugalPumpBlock.getSecondaryFluidDirection(blockState);
    }

    protected void updatePipeNetwork(boolean front) {
        sidesToUpdate.get(front).setTrue();
    }

    public boolean isSideAccessible(Direction side) {
        BlockState blockState = getBlockState();
        if (!(blockState.getBlock() instanceof CentrifugalPumpBlock)) {
            return false;
        }

        return CentrifugalPumpBlock.isOpenAt(blockState, side);
    }

    public boolean isPullingOnSide(boolean isPrimaryDirection) {
        if (transferDirection == null) return !isPrimaryDirection;

        boolean reversed = transferDirection.get() == TransferDirection.REVERSED;
        return reversed ? isPrimaryDirection : !isPrimaryDirection;
    }

    // 内部流体传输行为类
    class CentrifugalPumpFluidTransferBehaviour extends FluidTransportBehaviour {
        public CentrifugalPumpFluidTransferBehaviour(SmartBlockEntity be) {
            super(be);
        }

        @Override
        public void tick() {
            super.tick();

            Direction primary = CentrifugalPumpBlockEntity.this.getFront();
            Direction secondary = CentrifugalPumpBlockEntity.this.getSecondaryFront();

            if (primary == null || secondary == null) return;

            for (Map.Entry<Direction, PipeConnection> entry : interfaces.entrySet()) {
                Direction dir = entry.getKey();
                Couple<Float> pressure = entry.getValue().getPressure();

                float pumpPressure = Math.abs(CentrifugalPumpBlockEntity.this.getSpeed()) * SPEED_MULTIPLIER;

                if (dir == primary) {
                    boolean pull = CentrifugalPumpBlockEntity.this.isPullingOnSide(true);
                    pressure.set(pull, pull ? pumpPressure : 0f);
                    pressure.set(!pull, pull ? 0f : pumpPressure);
                } else if (dir == secondary) {
                    boolean pull = CentrifugalPumpBlockEntity.this.isPullingOnSide(false);
                    pressure.set(pull, pull ? pumpPressure : 0f);
                    pressure.set(!pull, pull ? 0f : pumpPressure);
                } else {
                    pressure.set(true, 0f);
                    pressure.set(false, 0f);
                }
            }
        }

        @Override
        public boolean canHaveFlowToward(BlockState state, Direction direction) {
            return CentrifugalPumpBlockEntity.this.isSideAccessible(direction);
        }

        @Override
        public AttachmentTypes getRenderedRimAttachment(BlockAndTintGetter world, BlockPos pos,
                                                        BlockState state, Direction direction) {
            AttachmentTypes attachment = super.getRenderedRimAttachment(world, pos, state, direction);
            return attachment == AttachmentTypes.RIM ? AttachmentTypes.NONE : attachment;
        }
    }

    static class CentrifugalPumpValueBoxTransform extends ValueBoxTransform.Sided {

        @Override
        protected Vec3 getSouthLocation() {
            return Vec3.ZERO.add(0.5, 0.5, 0.5);
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            if (!(state.getBlock() instanceof CentrifugalPumpBlock)) {
                return false;
            }

            AttachFace face = state.getValue(CentrifugalPumpBlock.FACE);
            Direction facing = state.getValue(CentrifugalPumpBlock.FACING);

            if (face == AttachFace.WALL) {
                return direction.getAxis() != Direction.Axis.Y && direction != facing && direction != facing.getOpposite();
            } else {
                return direction.getAxis() != facing.getAxis() && direction.getAxis() != Direction.Axis.Y;
            }
        }
    }
}