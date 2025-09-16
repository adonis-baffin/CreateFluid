package com.adonis.fluid.block.CentrifugalPump;

import com.adonis.fluid.mixin.accessor.PipeConnectionAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.fluids.*;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.apache.commons.lang3.mutable.MutableBoolean;

import javax.annotation.Nullable;
import java.util.*;

public class CentrifugalPumpBlockEntity extends KineticBlockEntity {

    protected ScrollOptionBehaviour<PumpMode> pumpMode;
    Couple<MutableBoolean> sidesToUpdate = Couple.create(MutableBoolean::new);
    boolean pressureUpdate;

    private static final int BASE_PUMP_RANGE = 20;
    private static final float SPEED_MULTIPLIER = 2.0f;

    // 添加定期检查机制
    private int networkCheckTimer = 0;
    private static final int CHECK_INTERVAL = 20; // 每秒检查一次
    private boolean networkInitialized = false; // 标记网络是否已初始化

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

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        // 添加流体传输行为
        behaviours.add(new CentrifugalPumpFluidTransferBehaviour(this));

        // 使用最简单的实现方式，直接用匿名类
        pumpMode = new ScrollOptionBehaviour<>(
                PumpMode.class,
                Component.translatable("create_fluid.centrifugal_pump.pump_mode"),
                this,
                new CentrifugalPumpValueBox()
        );

        pumpMode.withCallback(i -> onModeChanged());
        behaviours.add(pumpMode);

        // 注册成就
        registerAwardables(behaviours, FluidPropagator.getSharedTriggers());
        registerAwardables(behaviours, AllAdvancements.PUMP);
    }

    @Override
    public void initialize() {
        super.initialize();
        // 标记网络未初始化，需要在第一次tick时更新
        networkInitialized = false;
        pressureUpdate = true;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        // 当设置世界时，标记需要更新
        if (level != null && !level.isClientSide) {
            networkInitialized = false;
            pressureUpdate = true;
        }
    }

    private void onModeChanged() {
        if (!level.isClientSide || isVirtual()) {
            updatePressureChange();
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (!level.isClientSide || isVirtual()) {
            // 如果网络还未初始化，进行初始化
            if (!networkInitialized) {
                networkInitialized = true;
                updatePressureChange();
                return;
            }

            // 定期验证网络连接
            if (++networkCheckTimer >= CHECK_INTERVAL) {
                networkCheckTimer = 0;

                // 检查网络是否有效
                if (validateNetwork()) {
                    Direction primary = getFront();
                    Direction secondary = getSecondaryFront();

                    if (primary != null && secondary != null) {
                        // 检查两端的连接状态是否正常
                        FluidTransportBehaviour behaviour = getBehaviour(FluidTransportBehaviour.TYPE);
                        if (behaviour != null) {
                            boolean primaryConnected = behaviour.getConnection(primary) != null;
                            boolean secondaryConnected = behaviour.getConnection(secondary) != null;

                            // 如果任一端没有连接，触发更新
                            if (!primaryConnected || !secondaryConnected) {
                                pressureUpdate = true;
                            }
                        }
                    }
                }
            }

            // 检查是否需要更新压力
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

    // 处理管道网络变化的方法
    public void onPipeNetworkChanged() {
        if (!level.isClientSide || isVirtual()) {
            // 清除缓存的流体行为
            FluidTransportBehaviour behaviour = getBehaviour(FluidTransportBehaviour.TYPE);
            if (behaviour != null) {
                behaviour.wipePressure();
            }

            // 标记需要更新
            pressureUpdate = true;
            sidesToUpdate.forEach(MutableBoolean::setTrue);
        }
    }

    // 强化网络验证
    private boolean validateNetwork() {
        Direction primary = getFront();
        Direction secondary = getSecondaryFront();

        if (primary == null || secondary == null) return false;

        // 检查两端是否至少有一个有效连接
        BlockPos primaryPos = worldPosition.relative(primary);
        BlockPos secondaryPos = worldPosition.relative(secondary);

        boolean primaryValid = level.isLoaded(primaryPos) &&
                (FluidPropagator.isOpenEnd(level, worldPosition, primary) ||
                        FluidPropagator.hasFluidCapability(level, primaryPos, primary.getOpposite()) ||
                        FluidPropagator.getPipe(level, primaryPos) != null);

        boolean secondaryValid = level.isLoaded(secondaryPos) &&
                (FluidPropagator.isOpenEnd(level, worldPosition, secondary) ||
                        FluidPropagator.hasFluidCapability(level, secondaryPos, secondary.getOpposite()) ||
                        FluidPropagator.getPipe(level, secondaryPos) != null);

        return primaryValid || secondaryValid;
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);

        if (compound.contains("PumpMode") && pumpMode != null) {
            try {
                String mode = compound.getString("PumpMode");
                PumpMode pMode = PumpMode.valueOf(mode);
                pumpMode.setValue(pMode.ordinal());
            } catch (Exception e) {
                // 兼容旧的存储格式
                if (compound.contains("PumpModeOrdinal")) {
                    pumpMode.setValue(compound.getInt("PumpModeOrdinal"));
                } else {
                    pumpMode.setValue(0);
                }
            }
        }

        // 读取NBT后也需要更新网络
        if (!clientPacket) {
            networkInitialized = false;
            pressureUpdate = true;
        }
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);

        if (pumpMode != null) {
            compound.putString("PumpMode", pumpMode.get().name());
            compound.putInt("PumpModeOrdinal", pumpMode.getValue());
        }
    }

    // 其余代码保持不变...
    // [distributePressureTo, searchForEndpointRecursively, hasReachedValidEndpoint等方法保持原样]
    // [updatePipesOnSide, isFront, getFront, getSecondaryFront等方法保持原样]
    // [updatePipeNetwork, isSideAccessible, isPullingOnSide等方法保持原样]
    // [CentrifugalPumpFluidTransferBehaviour内部类保持原样]
    // [CentrifugalPumpValueBox内部类保持原样]

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
            if (blockEntity instanceof CentrifugalPumpBlockEntity pumpBE) {
                Direction pumpPrimary = pumpBE.getFront();
                Direction pumpSecondary = pumpBE.getSecondaryFront();

                if (face.getOpposite() == pumpPrimary || face.getOpposite() == pumpSecondary) {
                    boolean otherPull = pumpBE.isPullingOnSide(face.getOpposite() == pumpPrimary);
                    return otherPull != pull;
                }
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
        if (pumpMode == null) return !isPrimaryDirection;

        boolean pumpIn = pumpMode.get() == PumpMode.PUMP_IN;
        return pumpIn ? isPrimaryDirection : !isPrimaryDirection;
    }

    // CentrifugalPumpFluidTransferBehaviour 和 CentrifugalPumpValueBox 类保持原样
    class CentrifugalPumpFluidTransferBehaviour extends FluidTransportBehaviour implements IFluidHandler {

        private FluidTank internalTank;
        private static final int TANK_CAPACITY = 2000;

        public CentrifugalPumpFluidTransferBehaviour(SmartBlockEntity be) {
            super(be);
            this.internalTank = new FluidTank(TANK_CAPACITY);
        }

        @Override
        public void tick() {
            super.tick();

            if (CentrifugalPumpBlockEntity.this.getSpeed() == 0) return;

            Direction primary = CentrifugalPumpBlockEntity.this.getFront();
            Direction secondary = CentrifugalPumpBlockEntity.this.getSecondaryFront();

            if (primary == null || secondary == null) return;

            // 验证并更新连接
            validateConnections(primary, secondary);

            updatePressures(primary, secondary);
            performPumping(primary, secondary);
        }

        private void validateConnections(Direction primary, Direction secondary) {
            // 检查主要方向的连接
            validateConnection(primary);
            // 检查次要方向的连接
            validateConnection(secondary);
        }

        private void validateConnection(Direction dir) {
            PipeConnection connection = interfaces.get(dir);
            if (connection != null) {
                BlockPos targetPos = worldPosition.relative(dir);
                BlockState targetState = level.getBlockState(targetPos);

                // 强制重新确定源，如果目标改变了
                boolean needsUpdate = false;

                // 检查是否是储罐
                if (targetState.getBlock() instanceof FluidTankBlock) {
                    PipeConnectionAccessor accessor = (PipeConnectionAccessor) connection;
                    Optional<FlowSource> currentSource = accessor.getSource();

                    // 如果当前源不是FluidHandler类型，需要更新
                    if (!currentSource.isPresent() || !(currentSource.get() instanceof FlowSource.FluidHandler)) {
                        needsUpdate = true;
                    }
                }
                // 检查是否是管道
                else if (FluidPipeBlock.isPipe(targetState)) {
                    PipeConnectionAccessor accessor = (PipeConnectionAccessor) connection;
                    Optional<FlowSource> currentSource = accessor.getSource();

                    // 如果当前源不是OtherPipe类型，需要更新
                    if (!currentSource.isPresent() || !(currentSource.get() instanceof FlowSource.OtherPipe)) {
                        needsUpdate = true;
                    }
                }
                // 检查是否是开放端
                else if (FluidPropagator.isOpenEnd(level, worldPosition, dir)) {
                    PipeConnectionAccessor accessor = (PipeConnectionAccessor) connection;
                    Optional<FlowSource> currentSource = accessor.getSource();

                    // 如果当前源不是OpenEndedPipe类型，需要更新
                    if (!currentSource.isPresent() || !(currentSource.get() instanceof OpenEndedPipe)) {
                        needsUpdate = true;
                    }
                }

                if (needsUpdate) {
                    // 清除旧的流动状态
                    PipeConnectionAccessor accessor = (PipeConnectionAccessor) connection;
                    accessor.setFlow(Optional.empty());

                    // 强制重新确定源
                    connection.determineSource(level, worldPosition);
                }
            }
        }

        private void updatePressures(Direction primary, Direction secondary) {
            for (Map.Entry<Direction, PipeConnection> entry : interfaces.entrySet()) {
                Direction dir = entry.getKey();
                Couple<Float> pressure = entry.getValue().getPressure();

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

        private void performPumping(Direction primary, Direction secondary) {
            boolean pullFromPrimary = CentrifugalPumpBlockEntity.this.isPullingOnSide(true);

            Direction inputDir = pullFromPrimary ? primary : secondary;
            Direction outputDir = pullFromPrimary ? secondary : primary;

            int transferRate = (int)(Math.abs(CentrifugalPumpBlockEntity.this.getSpeed()) * 50);

            extractFromInput(inputDir, transferRate);
            insertToOutput(outputDir, transferRate);
        }

        private void extractFromInput(Direction inputDir, int maxAmount) {
            BlockPos inputPos = worldPosition.relative(inputDir);
            BlockEntity be = level.getBlockEntity(inputPos);

            if (be != null) {
                // 直接尝试获取流体处理能力
                LazyOptional<IFluidHandler> capability = be.getCapability(
                        ForgeCapabilities.FLUID_HANDLER,
                        inputDir.getOpposite()
                );

                if (capability.isPresent()) {
                    capability.ifPresent(handler -> {
                        int spaceAvailable = internalTank.getSpace();
                        int toExtract = Math.min(maxAmount, spaceAvailable);

                        FluidStack simulated = handler.drain(toExtract, IFluidHandler.FluidAction.SIMULATE);
                        if (!simulated.isEmpty()) {
                            if (internalTank.isEmpty() || internalTank.getFluid().isFluidEqual(simulated)) {
                                FluidStack extracted = handler.drain(toExtract, IFluidHandler.FluidAction.EXECUTE);
                                if (!extracted.isEmpty()) {
                                    internalTank.fill(extracted, IFluidHandler.FluidAction.EXECUTE);

                                    // 只在管道连接时更新动画
                                    BlockState targetState = level.getBlockState(inputPos);
                                    if (FluidPipeBlock.isPipe(targetState) || FluidPropagator.isOpenEnd(level, worldPosition, inputDir)) {
                                        updateFlowAnimation(inputDir, extracted, true);
                                    }
                                }
                            }
                        }
                    });
                }
            }
        }

        private void insertToOutput(Direction outputDir, int maxAmount) {
            if (internalTank.isEmpty()) return;

            BlockPos outputPos = worldPosition.relative(outputDir);
            BlockEntity be = level.getBlockEntity(outputPos);

            if (be != null) {
                // 直接尝试获取流体处理能力
                LazyOptional<IFluidHandler> capability = be.getCapability(
                        ForgeCapabilities.FLUID_HANDLER,
                        outputDir.getOpposite()
                );

                if (capability.isPresent()) {
                    capability.ifPresent(handler -> {
                        FluidStack toOutput = internalTank.getFluid().copy();
                        toOutput.setAmount(Math.min(maxAmount, toOutput.getAmount()));

                        int inserted = handler.fill(toOutput, IFluidHandler.FluidAction.EXECUTE);
                        if (inserted > 0) {
                            internalTank.drain(inserted, IFluidHandler.FluidAction.EXECUTE);

                            FluidStack outputted = toOutput.copy();
                            outputted.setAmount(inserted);

                            // 只在管道连接时更新动画
                            BlockState targetState = level.getBlockState(outputPos);
                            if (FluidPipeBlock.isPipe(targetState) || FluidPropagator.isOpenEnd(level, worldPosition, outputDir)) {
                                updateFlowAnimation(outputDir, outputted, false);
                            }
                        }
                    });
                }
            }
        }

        private void updateFlowAnimation(Direction dir, FluidStack fluid, boolean inbound) {
            PipeConnection connection = interfaces.get(dir);
            if (connection != null) {
                PipeConnectionAccessor accessor = (PipeConnectionAccessor) connection;

                if (!connection.hasFlow() || !accessor.getFlow().get().fluid.isFluidEqual(fluid)) {
                    PipeConnection.Flow newFlow = connection.new Flow(inbound, fluid);
                    accessor.setFlow(Optional.of(newFlow));
                }

                connection.tickFlowProgress(level, worldPosition);
            }
        }

        @Override
        public boolean canHaveFlowToward(BlockState state, Direction direction) {
            return CentrifugalPumpBlockEntity.this.isSideAccessible(direction);
        }

        // IFluidHandler 接口方法保持不变
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return internalTank.getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return TANK_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return true;
        }

        @Override
        public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public void write(CompoundTag compound, boolean clientPacket) {
            super.write(compound, clientPacket);
            compound.put("Tank", internalTank.writeToNBT(new CompoundTag()));
        }

        @Override
        public void read(CompoundTag compound, boolean clientPacket) {
            super.read(compound, clientPacket);
            if (compound.contains("Tank")) {
                internalTank.readFromNBT(compound.getCompound("Tank"));
            }
        }
    }

    public static class CentrifugalPumpValueBox extends ValueBoxTransform.Sided {

        @Override
        protected boolean isSideActive(BlockState state, Direction side) {
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