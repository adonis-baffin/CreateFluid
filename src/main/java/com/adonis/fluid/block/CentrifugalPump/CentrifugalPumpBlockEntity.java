package com.adonis.fluid.block.CentrifugalPump;

import com.simibubi.create.foundation.gui.AllIcons;
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
            sidesToUpdate.forEachWithContext((update, isFront) -> {
                if (!update.isFalse()) {
                    update.setFalse();
                    distributePressureTo(isFront ? getFront() : getFront().getOpposite());
                }
            });
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

    public void updatePressureChange() {
        pressureUpdate = false;

        BlockPos frontPos = worldPosition.relative(getFront());
        BlockPos backPos = worldPosition.relative(getFront().getOpposite());

        FluidPropagator.propagateChangedPipe(level, frontPos, level.getBlockState(frontPos));
        FluidPropagator.propagateChangedPipe(level, backPos, level.getBlockState(backPos));

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
                // 默认值
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
        boolean pull = isPullingOnSide(isFront(side));

        Set<BlockFace> targets = new HashSet<>();
        Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph = new HashMap<>();

        if (!pull) {
            FluidPropagator.resetAffectedFluidNetworks(level, worldPosition, side.getOpposite());
        }

        // 使用固定的传输距离
        int maxDistance = BASE_PUMP_RANGE;

        // 探索管道网络
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

        // 应用压力
        Map<Integer, Set<BlockFace>> validFaces = new HashMap<>();
        searchForEndpointRecursively(pipeGraph, targets, validFaces,
                new BlockFace(start.getPos(), start.getOppositeFace()), pull);

        // 离心泵的压力是速度的两倍
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

        // 检查是否连接到其他泵
        if (PumpBlock.isPump(connectedState) && connectedState.getValue(PumpBlock.FACING).getAxis() == face.getAxis()) {
            if (blockEntity instanceof CentrifugalPumpBlockEntity pumpBE) {
                return pumpBE.isPullingOnSide(pumpBE.isFront(blockFace.getOppositeFace())) != pull;
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
            updatePipeNetwork(isFront(side));
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

        CentrifugalPumpBlock.Orientation orientation = blockState.getValue(CentrifugalPumpBlock.ORIENTATION);
        if (orientation == CentrifugalPumpBlock.Orientation.VERTICAL) {
            // 垂直模式：下方是前方
            return side == Direction.DOWN;
        } else {
            // 水平模式：facing方向是前方
            return side == blockState.getValue(CentrifugalPumpBlock.FACING);
        }
    }

    @Nullable
    protected Direction getFront() {
        BlockState blockState = getBlockState();
        if (!(blockState.getBlock() instanceof CentrifugalPumpBlock)) {
            return null;
        }

        CentrifugalPumpBlock.Orientation orientation = blockState.getValue(CentrifugalPumpBlock.ORIENTATION);
        if (orientation == CentrifugalPumpBlock.Orientation.VERTICAL) {
            return Direction.DOWN;
        } else {
            return blockState.getValue(CentrifugalPumpBlock.FACING);
        }
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

    public boolean isPullingOnSide(boolean front) {
        // 根据传输方向设置决定拉/推
        // REVERSED 相当于反向
        return transferDirection != null &&
                transferDirection.get() == TransferDirection.REVERSED ?
                front : !front;
    }

    // 内部流体传输行为类
    class CentrifugalPumpFluidTransferBehaviour extends FluidTransportBehaviour {
        public CentrifugalPumpFluidTransferBehaviour(SmartBlockEntity be) {
            super(be);
        }

        @Override
        public void tick() {
            super.tick();

            for (Map.Entry<Direction, PipeConnection> entry : interfaces.entrySet()) {
                boolean pull = CentrifugalPumpBlockEntity.this.isPullingOnSide(
                        CentrifugalPumpBlockEntity.this.isFront(entry.getKey()));
                Couple<Float> pressure = entry.getValue().getPressure();

                // 离心泵的压力是速度的两倍
                float pumpPressure = Math.abs(CentrifugalPumpBlockEntity.this.getSpeed()) * SPEED_MULTIPLIER;
                pressure.set(pull, pumpPressure);
                pressure.set(!pull, 0f);
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

    // 值盒子变换类（用于显示传输方向）
    static class CentrifugalPumpValueBoxTransform extends ValueBoxTransform.Sided {

        @Override
        protected Vec3 getSouthLocation() {
            return Vec3.ZERO.add(0.5, 0.5, 0.5);
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            // 在侧面显示控制面板
            if (!(state.getBlock() instanceof CentrifugalPumpBlock)) {
                return false;
            }

            CentrifugalPumpBlock.Orientation orientation = state.getValue(CentrifugalPumpBlock.ORIENTATION);
            Direction facing = state.getValue(CentrifugalPumpBlock.FACING);

            if (orientation == CentrifugalPumpBlock.Orientation.VERTICAL) {
                // 垂直模式：在水平侧面显示（不在上下面，也不在主朝向面）
                return direction.getAxis() != Direction.Axis.Y && direction != facing;
            } else {
                // 水平模式：在左右两侧显示
                return direction.getAxis() != facing.getAxis() && direction.getAxis() != Direction.Axis.Y;
            }
        }
    }
}