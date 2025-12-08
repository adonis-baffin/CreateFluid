package com.adonis.fluid.block.CentrifugalPump;

import com.adonis.fluid.mixin.accessor.PipeConnectionAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.fluids.*;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
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

    public ScrollOptionBehaviour<PumpMode> pumpMode;
    Couple<MutableBoolean> sidesToUpdate = Couple.create(MutableBoolean::new);
    boolean pressureUpdate;

    // 封装状态
    private boolean isEncased = false;

    private static final int BASE_PUMP_RANGE = 20;
    private static final float SPEED_MULTIPLIER = 2.0f;

    private int networkCheckTimer = 0;
    private static final int CHECK_INTERVAL = 20;
    private boolean networkInitialized = false;

    public enum PumpMode implements INamedIconOptions {
        PUMP_IN(AllIcons.I_REFRESH),
        PUMP_OUT(AllIcons.I_ROTATE_CCW);

        private String translationKey;
        private AllIcons icon;

        private PumpMode(AllIcons icon) {
            this.icon = icon;
            this.translationKey = "create_fluid.centrifugal_pump.mode." + Lang.asId(this.name());
        }

        @Override
        public AllIcons getIcon() {
            return this.icon;
        }

        @Override
        public String getTranslationKey() {
            return this.translationKey;
        }
    }

    public CentrifugalPumpBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    public void onEncasedStateChanged(boolean encased) {
        this.isEncased = encased;

        // 触发更新
        updatePressureChange();
        notifyUpdate();
    }

    public void onFluidContainerDetected(Direction dir) {
        if (level == null || level.isClientSide) return;

        FluidTransportBehaviour behaviour = getBehaviour(FluidTransportBehaviour.TYPE);
        if (behaviour != null) {
            PipeConnection connection = behaviour.getConnection(dir);

            if (connection == null) {
                pressureUpdate = true;
                updatePipeNetwork(dir == getFront());
            } else {
                PipeConnectionAccessor accessor = (PipeConnectionAccessor) connection;
                if (!accessor.getSource().isPresent()) {
                    connection.determineSource(level, worldPosition);
                    updatePipeNetwork(dir == getFront());
                } else {
                    connection.determineSource(level, worldPosition);
                }
            }
        }

        FluidPropagator.propagateChangedPipe(level, worldPosition, getBlockState());
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        behaviours.add(new CentrifugalPumpFluidTransferBehaviour(this));

        // 只在非封装状态下添加 ScrollOptionBehaviour
        BlockState state = getBlockState();
        boolean isCurrentlyEncased = state.hasProperty(CentrifugalPumpBlock.ENCASED)
                && state.getValue(CentrifugalPumpBlock.ENCASED);

        if (!isCurrentlyEncased) {
            pumpMode = new ScrollOptionBehaviour<>(
                    PumpMode.class,
                    Component.translatable("create_fluid.centrifugal_pump.pump_mode"),
                    this,
                    new CentrifugalPumpValueBox()
            );
            pumpMode.withCallback(i -> onModeChanged());
            behaviours.add(pumpMode);
        }

        isEncased = isCurrentlyEncased;

        registerAwardables(behaviours, FluidPropagator.getSharedTriggers());
        registerAwardables(behaviours, AllAdvancements.PUMP);
    }

    @Override
    public void initialize() {
        super.initialize();
        networkInitialized = false;
        pressureUpdate = true;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level != null && !level.isClientSide) {
            networkInitialized = false;
            pressureUpdate = true;
        }
    }

    public void onModeChanged() {
        if (!level.isClientSide || isVirtual()) {
            updatePressureChange();
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (!level.isClientSide || isVirtual()) {
            if (!networkInitialized) {
                networkInitialized = true;
                updatePressureChange();
                checkForFluidContainers();
                return;
            }

            if (++networkCheckTimer >= CHECK_INTERVAL) {
                networkCheckTimer = 0;
                checkForMissingConnections();
            }

            if (pressureUpdate) {
                updatePressureChange();
                return;
            }

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

    private void checkForMissingConnections() {
        Direction primary = getFront();
        Direction secondary = getSecondaryFront();

        if (primary == null || secondary == null) return;

        FluidTransportBehaviour behaviour = getBehaviour(FluidTransportBehaviour.TYPE);
        if (behaviour == null) return;

        checkDirectionForContainer(primary, behaviour);
        checkDirectionForContainer(secondary, behaviour);
    }

    private void checkDirectionForContainer(Direction dir, FluidTransportBehaviour behaviour) {
        PipeConnection connection = behaviour.getConnection(dir);

        if (connection != null && connection.hasFlow()) {
            return;
        }

        BlockPos targetPos = worldPosition.relative(dir);
        if (!level.isLoaded(targetPos)) return;

        BlockEntity targetBE = level.getBlockEntity(targetPos);
        if (targetBE == null) return;

        LazyOptional<IFluidHandler> capability = targetBE.getCapability(
                ForgeCapabilities.FLUID_HANDLER, dir.getOpposite()
        );
        if (!capability.isPresent()) {
            capability = targetBE.getCapability(ForgeCapabilities.FLUID_HANDLER, null);
        }

        if (capability.isPresent()) {
            if (connection == null) {
                pressureUpdate = true;
            } else {
                PipeConnectionAccessor accessor = (PipeConnectionAccessor) connection;
                if (!accessor.getSource().isPresent()) {
                    connection.determineSource(level, worldPosition);
                }
            }
        }
    }

    private void checkForFluidContainers() {
        Direction primary = getFront();
        Direction secondary = getSecondaryFront();

        if (primary == null || secondary == null) return;

        boolean foundContainer = false;

        if (hasFluidContainer(primary)) foundContainer = true;
        if (hasFluidContainer(secondary)) foundContainer = true;

        if (foundContainer) {
            pressureUpdate = true;
        }
    }

    private boolean hasFluidContainer(Direction dir) {
        BlockPos targetPos = worldPosition.relative(dir);
        if (!level.isLoaded(targetPos)) return false;

        BlockEntity targetBE = level.getBlockEntity(targetPos);
        if (targetBE == null) return false;

        LazyOptional<IFluidHandler> capability = targetBE.getCapability(
                ForgeCapabilities.FLUID_HANDLER, dir.getOpposite()
        );
        if (!capability.isPresent()) {
            capability = targetBE.getCapability(ForgeCapabilities.FLUID_HANDLER, null);
        }

        return capability.isPresent();
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

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
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

    public void onPipeNetworkChanged() {
        if (!level.isClientSide || isVirtual()) {
            FluidTransportBehaviour behaviour = getBehaviour(FluidTransportBehaviour.TYPE);
            if (behaviour != null) {
                behaviour.wipePressure();
            }

            pressureUpdate = true;
            sidesToUpdate.forEach(MutableBoolean::setTrue);
        }
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);

        // 读取封装状态
        if (compound.contains("Encased")) {
            isEncased = compound.getBoolean("Encased");
        }

        // 只在非封装状态下读取泵模式
        if (compound.contains("PumpMode") && !isEncased && pumpMode != null) {
            try {
                String mode = compound.getString("PumpMode");
                PumpMode pMode = PumpMode.valueOf(mode);
                pumpMode.setValue(pMode.ordinal());
            } catch (Exception e) {
                if (compound.contains("PumpModeOrdinal")) {
                    pumpMode.setValue(compound.getInt("PumpModeOrdinal"));
                } else {
                    pumpMode.setValue(0);
                }
            }
        }

        if (!clientPacket) {
            networkInitialized = false;
            pressureUpdate = true;
        }
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);

        compound.putBoolean("Encased", isEncased);

        // 只在非封装状态下保存泵模式
        if (pumpMode != null && !isEncased) {
            compound.putString("PumpMode", pumpMode.get().name());
            compound.putInt("PumpModeOrdinal", pumpMode.getValue());
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

        FluidTransportBehaviour behaviour = getBehaviour(FluidTransportBehaviour.TYPE);
        if (behaviour != null) {
            PipeConnection connection = behaviour.getConnection(side);
            if (connection != null) {
                connection.determineSource(level, worldPosition);
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

        if (connectedState.getBlock() instanceof CentrifugalPumpBlock) {
            if (blockEntity instanceof CentrifugalPumpBlockEntity otherPump) {
                Direction otherPrimary = CentrifugalPumpBlock.getPrimaryFluidDirection(connectedState);
                Direction otherSecondary = CentrifugalPumpBlock.getSecondaryFluidDirection(connectedState);

                boolean connectsToPrimary = (face.getOpposite() == otherPrimary);
                boolean connectsToSecondary = (face.getOpposite() == otherSecondary);

                if (connectsToPrimary || connectsToSecondary) {
                    boolean otherPull = otherPump.isPullingOnSide(connectsToPrimary);
                    return otherPull != pull;
                }

                return false;
            }
        }

        if (PumpBlock.isPump(connectedState) && connectedState.getValue(PumpBlock.FACING).getAxis() == face.getAxis()) {
            return true;
        }

        FluidTransportBehaviour pipe = FluidPropagator.getPipe(world, connectedPos);
        if (pipe != null && pipe.canHaveFlowToward(connectedState, blockFace.getOppositeFace())) {
            return false;
        }

        if (blockEntity != null) {
            LazyOptional<IFluidHandler> capability = blockEntity.getCapability(
                    ForgeCapabilities.FLUID_HANDLER,
                    face.getOpposite()
            );
            if (!capability.isPresent()) {
                capability = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, null);
            }
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
        BlockState state = getBlockState();

        // 如果是封装状态，根据旋转方向决定
        if (state.hasProperty(CentrifugalPumpBlock.ENCASED) && state.getValue(CentrifugalPumpBlock.ENCASED)) {
            // 逆时针（负速度）= 泵入，顺时针（正速度）= 泵出
            boolean pumpIn = getSpeed() < 0;
            return pumpIn ? isPrimaryDirection : !isPrimaryDirection;
        }

        // 非封装状态，使用手动控制
        if (pumpMode == null) return !isPrimaryDirection;

        boolean pumpIn = pumpMode.get() == PumpMode.PUMP_IN;
        return pumpIn ? isPrimaryDirection : !isPrimaryDirection;
    }
    class CentrifugalPumpFluidTransferBehaviour extends FluidTransportBehaviour {

        public CentrifugalPumpFluidTransferBehaviour(SmartBlockEntity be) {
            super(be);
        }

        @Override
        public void tick() {
            super.tick();

            if (CentrifugalPumpBlockEntity.this.getSpeed() == 0) return;

            Direction primary = CentrifugalPumpBlockEntity.this.getFront();
            Direction secondary = CentrifugalPumpBlockEntity.this.getSecondaryFront();

            if (primary == null || secondary == null) return;

            updatePressures(primary, secondary);
        }

        private void updatePressures(Direction primary, Direction secondary) {
            for (Map.Entry<Direction, PipeConnection> entry : interfaces.entrySet()) {
                Direction dir = entry.getKey();
                PipeConnectionAccessor accessor = (PipeConnectionAccessor) entry.getValue();
                Couple<Float> pressure = accessor.getPressure();

                float pumpPressure = Math.abs(CentrifugalPumpBlockEntity.this.getSpeed()) * SPEED_MULTIPLIER;

                if (dir == primary) {
                    boolean pull = CentrifugalPumpBlockEntity.this.isPullingOnSide(true);
                    pressure.set(pull, pumpPressure);
                    pressure.set(!pull, 0f);
                } else if (dir == secondary) {
                    boolean pull = CentrifugalPumpBlockEntity.this.isPullingOnSide(false);
                    pressure.set(pull, pumpPressure);
                    pressure.set(!pull, 0f);
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
    }

    public static class CentrifugalPumpValueBox extends ValueBoxTransform.Sided {

        @Override
        protected boolean isSideActive(BlockState state, Direction side) {
            // 封装状态下不显示ValueBox
            if (state.hasProperty(CentrifugalPumpBlock.ENCASED) && state.getValue(CentrifugalPumpBlock.ENCASED)) {
                return false;
            }

            if (!(state.getBlock() instanceof CentrifugalPumpBlock)) {
                return false;
            }

            AttachFace face = state.getValue(CentrifugalPumpBlock.FACE);
            Direction facing = state.getValue(CentrifugalPumpBlock.FACING);

            if (face == AttachFace.WALL) {
                if (facing == Direction.NORTH || facing == Direction.SOUTH) {
                    return side == Direction.WEST || side == Direction.EAST;
                } else {
                    return side == Direction.NORTH || side == Direction.SOUTH;
                }
            } else {
                if (facing == Direction.NORTH || facing == Direction.SOUTH) {
                    return side == Direction.WEST || side == Direction.EAST;
                } else {
                    return side == Direction.NORTH || side == Direction.SOUTH;
                }
            }
        }

        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8, 8, 16);
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            Direction side = getSide();
            if (side == null) {
                AttachFace face = state.getValue(CentrifugalPumpBlock.FACE);
                Direction facing = state.getValue(CentrifugalPumpBlock.FACING);

                if (face == AttachFace.WALL) {
                    side = (facing == Direction.NORTH || facing == Direction.SOUTH) ? Direction.WEST : Direction.NORTH;
                } else {
                    side = (facing == Direction.NORTH || facing == Direction.SOUTH) ? Direction.WEST : Direction.NORTH;
                }
            }

            float offset = 15.5f;
            switch (side) {
                case NORTH:
                    return VecHelper.voxelSpace(8, 8, 16 - offset);
                case SOUTH:
                    return VecHelper.voxelSpace(8, 8, offset);
                case WEST:
                    return VecHelper.voxelSpace(16 - offset, 8, 8);
                case EAST:
                    return VecHelper.voxelSpace(offset, 8, 8);
                default:
                    return VecHelper.voxelSpace(8, 8, offset);
            }
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            Direction side = getSide();
            if (side == null) {
                AttachFace face = state.getValue(CentrifugalPumpBlock.FACE);
                Direction facing = state.getValue(CentrifugalPumpBlock.FACING);

                if (face == AttachFace.WALL) {
                    side = (facing == Direction.NORTH || facing == Direction.SOUTH) ? Direction.WEST : Direction.NORTH;
                } else {
                    side = (facing == Direction.NORTH || facing == Direction.SOUTH) ? Direction.WEST : Direction.NORTH;
                }
            }

            AttachFace face = state.getValue(CentrifugalPumpBlock.FACE);

            float yRot = AngleHelper.horizontalAngle(side) + 180;
            TransformStack.of(ms).rotateYDegrees(yRot);

            if (face == AttachFace.CEILING) {
                TransformStack.of(ms).rotateZDegrees(180);
            }
        }
    }
}