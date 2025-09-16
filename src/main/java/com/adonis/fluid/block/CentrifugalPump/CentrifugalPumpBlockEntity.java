package com.adonis.fluid.block.CentrifugalPump;

import com.adonis.fluid.mixin.accessor.PipeConnectionAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
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
        // 初始化时触发流体网络更新
        if (!level.isClientSide) {
            updatePressureChange();
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
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);

        if (pumpMode != null) {
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

        // 在分配压力后，确保流体网络知道这个泵是流体源或目标
        FluidTransportBehaviour behaviour = getBehaviour(FluidTransportBehaviour.TYPE);
        if (behaviour != null) {
            PipeConnection connection = behaviour.getConnection(side);
            if (connection != null) {
                // 设置这个连接为活动源/汇
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

        // 检查是否连接到另一个离心泵
        if (connectedState.getBlock() instanceof CentrifugalPumpBlock) {
            if (blockEntity instanceof CentrifugalPumpBlockEntity pumpBE) {
                // 检查是否是有效的连接
                Direction pumpPrimary = pumpBE.getFront();
                Direction pumpSecondary = pumpBE.getSecondaryFront();

                if (face.getOpposite() == pumpPrimary || face.getOpposite() == pumpSecondary) {
                    boolean otherPull = pumpBE.isPullingOnSide(face.getOpposite() == pumpPrimary);
                    return otherPull != pull;
                }
            }
        }

        // 检查原版机械动力的泵
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

    // 内部流体传输行为类
    class CentrifugalPumpFluidTransferBehaviour extends FluidTransportBehaviour implements IFluidHandler {

        // 内部流体缓存 - 作为中转站
        private FluidTank internalTank;
        private static final int TANK_CAPACITY = 2000; // 2桶容量

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

            // 更新压力
            updatePressures(primary, secondary);

            // 执行泵送操作
            performPumping(primary, secondary);
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

            int transferRate = (int)(Math.abs(CentrifugalPumpBlockEntity.this.getSpeed()) * 50); // mB/tick

            // 步骤1：从输入端抽取流体到内部缓存
            extractFromInput(inputDir, transferRate);

            // 步骤2：从内部缓存输出到输出端
            insertToOutput(outputDir, transferRate);
        }

        private void extractFromInput(Direction inputDir, int maxAmount) {
            BlockPos inputPos = worldPosition.relative(inputDir);
            BlockEntity be = level.getBlockEntity(inputPos);

            if (be != null) {
                LazyOptional<IFluidHandler> capability = be.getCapability(
                        ForgeCapabilities.FLUID_HANDLER,
                        inputDir.getOpposite()
                );

                capability.ifPresent(handler -> {
                    // 计算可以抽取的量
                    int spaceAvailable = internalTank.getSpace();
                    int toExtract = Math.min(maxAmount, spaceAvailable);

                    // 模拟抽取
                    FluidStack simulated = handler.drain(toExtract, FluidAction.SIMULATE);
                    if (!simulated.isEmpty()) {
                        // 检查是否可以接受这种流体
                        if (internalTank.isEmpty() || internalTank.getFluid().isFluidEqual(simulated)) {
                            // 实际抽取
                            FluidStack extracted = handler.drain(toExtract, FluidAction.EXECUTE);
                            if (!extracted.isEmpty()) {
                                internalTank.fill(extracted, FluidAction.EXECUTE);

                                // 更新流体动画
                                updateFlowAnimation(inputDir, extracted, true);
                            }
                        }
                    }
                });
            }
        }

        private void insertToOutput(Direction outputDir, int maxAmount) {
            if (internalTank.isEmpty()) return;

            BlockPos outputPos = worldPosition.relative(outputDir);
            BlockEntity be = level.getBlockEntity(outputPos);

            if (be != null) {
                LazyOptional<IFluidHandler> capability = be.getCapability(
                        ForgeCapabilities.FLUID_HANDLER,
                        outputDir.getOpposite()
                );

                capability.ifPresent(handler -> {
                    // 尝试输出
                    FluidStack toOutput = internalTank.getFluid().copy();
                    toOutput.setAmount(Math.min(maxAmount, toOutput.getAmount()));

                    int inserted = handler.fill(toOutput, FluidAction.EXECUTE);
                    if (inserted > 0) {
                        internalTank.drain(inserted, FluidAction.EXECUTE);

                        // 更新流体动画
                        FluidStack outputted = toOutput.copy();
                        outputted.setAmount(inserted);
                        updateFlowAnimation(outputDir, outputted, false);
                    }
                });
            }
        }

        private void updateFlowAnimation(Direction dir, FluidStack fluid, boolean inbound) {
            PipeConnection connection = interfaces.get(dir);
            if (connection != null) {
                // 使用Accessor更新流动画
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

        // IFluidHandler 实现 - 允许管道直接与泵交互
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
        public int fill(FluidStack resource, FluidAction action) {
            // 只允许从输入方向填充
            return 0; // 通过extractFromInput处理
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            // 只允许从输出方向抽取
            return FluidStack.EMPTY; // 通过insertToOutput处理
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY; // 通过insertToOutput处理
        }

        // 添加NBT存储
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

    // ValueBox实现保持不变
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