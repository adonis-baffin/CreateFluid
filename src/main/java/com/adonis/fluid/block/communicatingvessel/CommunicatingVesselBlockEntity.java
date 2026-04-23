package com.adonis.fluid.block.communicatingvessel;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.block.CopperSink.CopperSinkBlockEntity;
import com.adonis.fluid.config.CFCommonConfig;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CommunicatingVesselBlockEntity extends SmartBlockEntity {

    private static final int TOPOLOGY_RESCAN_INTERVAL = 20;
    private static final int NON_MASTER_LIGHT_RESCAN_INTERVAL = 40;

    private static final int TRANSFER_INTERVAL = 1;

    private static final int TAIL_TOTAL_THRESHOLD = 8;
    private static final int MAX_TAIL_BIAS = 1;
    private static final int CONSUMER_MEMORY_TICKS = 40;

    private int consumerBiasTicks = 0;
    private BiasSide rememberedConsumerSide = BiasSide.NONE;

    private boolean topologyDirty = true;
    private int topologyRescanCooldown = 0;
    private int nonMasterRescanCooldown = 0;
    private int transferCooldown = 0;

    @Nullable
    private Direction.Axis cachedAxis;
    @Nullable
    private BlockPos cachedMasterPos;
    @Nullable
    private BlockPos cachedEndAPos;
    @Nullable
    private BlockPos cachedEndBPos;

    public CommunicatingVesselBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public void markTopologyDirty() {
        this.topologyDirty = true;
        this.topologyRescanCooldown = 0;
        this.nonMasterRescanCooldown = 0;
    }

    @Override
    public void initialize() {
        super.initialize();
        markTopologyDirty();
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide) return;
        if (!(getBlockState().getBlock() instanceof CommunicatingVesselBlock)) return;

        if (consumerBiasTicks > 0) {
            consumerBiasTicks--;
            if (consumerBiasTicks <= 0) {
                rememberedConsumerSide = BiasSide.NONE;
            }
        }

        if (topologyRescanCooldown > 0) topologyRescanCooldown--;
        if (nonMasterRescanCooldown > 0) nonMasterRescanCooldown--;
        if (transferCooldown > 0) transferCooldown--;

        if (topologyDirty || topologyRescanCooldown <= 0 || cachedAxis != getBlockState().getValue(CommunicatingVesselBlock.AXIS)) {
            rebuildTopologyCache();
        }

        if (cachedMasterPos == null || cachedEndAPos == null || cachedEndBPos == null) {
            return;
        }

        boolean isMaster = worldPosition.equals(cachedMasterPos);

        // 非 master 不参与真正转移，只偶尔轻量检查一次缓存是否仍可信
        if (!isMaster) {
            if (nonMasterRescanCooldown <= 0) {
                if (!isStillConnectedToCachedMaster()) {
                    markTopologyDirty();
                }
                nonMasterRescanCooldown = NON_MASTER_LIGHT_RESCAN_INTERVAL;
            }
            return;
        }

        if (transferCooldown > 0) {
            return;
        }
        transferCooldown = TRANSFER_INTERVAL;

        attemptTransferUsingCache();
    }

    public void onScheduledTick() {
        markTopologyDirty();
    }

    private void rebuildTopologyCache() {
        Direction.Axis axis = getBlockState().getValue(CommunicatingVesselBlock.AXIS);

        Direction positive = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        Direction negative = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);

        BlockPos minConnector = worldPosition;
        BlockPos maxConnector = worldPosition;

        BlockPos p = worldPosition.relative(negative);
        while (isValidConnector(p, axis)) {
            minConnector = p;
            p = p.relative(negative);
        }

        p = worldPosition.relative(positive);
        while (isValidConnector(p, axis)) {
            maxConnector = p;
            p = p.relative(positive);
        }

        BlockPos endB = minConnector.relative(negative);
        BlockPos endA = maxConnector.relative(positive);

        cachedAxis = axis;
        cachedMasterPos = minConnector.compareTo(maxConnector) <= 0 ? minConnector : maxConnector;
        cachedEndAPos = endA;
        cachedEndBPos = endB;

        topologyDirty = false;
        topologyRescanCooldown = TOPOLOGY_RESCAN_INTERVAL;
        nonMasterRescanCooldown = NON_MASTER_LIGHT_RESCAN_INTERVAL;
    }

    private boolean isStillConnectedToCachedMaster() {
        if (cachedMasterPos == null || cachedAxis == null) {
            return false;
        }

        if (worldPosition.equals(cachedMasterPos)) {
            return true;
        }

        Direction.Axis axis = getBlockState().getValue(CommunicatingVesselBlock.AXIS);
        if (axis != cachedAxis) {
            return false;
        }

        Direction positive = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        Direction negative = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);

        BlockPos p = worldPosition.relative(positive);
        while (isValidConnector(p, axis)) {
            if (p.equals(cachedMasterPos)) return true;
            p = p.relative(positive);
        }

        p = worldPosition.relative(negative);
        while (isValidConnector(p, axis)) {
            if (p.equals(cachedMasterPos)) return true;
            p = p.relative(negative);
        }

        return false;
    }

    private void attemptTransferUsingCache() {
        if (cachedAxis == null || cachedEndAPos == null || cachedEndBPos == null) {
            return;
        }

        IFluidHandler handlerA = getHandlerAtEnd(cachedEndAPos, cachedAxis, Direction.AxisDirection.POSITIVE);
        IFluidHandler handlerB = getHandlerAtEnd(cachedEndBPos, cachedAxis, Direction.AxisDirection.NEGATIVE);

        if (handlerA == null || handlerB == null || !hasAnyTank(handlerA) || !hasAnyTank(handlerB)) {
            return;
        }

        boolean infiniteA = isInfiniteFluidSource(cachedEndAPos);
        boolean infiniteB = isInfiniteFluidSource(cachedEndBPos);

        if (infiniteA && infiniteB) {
            return;
        }

        if (infiniteA) {
            tryFillFromInfinite(handlerA, handlerB);
            rememberConsumer(BiasSide.B);
            return;
        }

        if (infiniteB) {
            tryFillFromInfinite(handlerB, handlerA);
            rememberConsumer(BiasSide.A);
            return;
        }

        FluidStack fluidA = getMainFluid(handlerA);
        FluidStack fluidB = getMainFluid(handlerB);

        boolean aEmpty = fluidA.isEmpty();
        boolean bEmpty = fluidB.isEmpty();

        if (aEmpty && bEmpty) {
            return;
        }

        if (!aEmpty && !bEmpty && !FluidStack.isSameFluidSameComponents(fluidA, fluidB)) {
            return;
        }

        EndpointInfo infoA = buildEndpointInfo(cachedEndAPos, handlerA);
        EndpointInfo infoB = buildEndpointInfo(cachedEndBPos, handlerB);
        if (infoA == null || infoB == null) {
            return;
        }

        double levelA = infoA.level();
        double levelB = infoB.level();
        double levelDiff = levelA - levelB;

        int total = infoA.amount() + infoB.amount();
        if (total <= 0) {
            return;
        }

        if (Math.abs(levelDiff) > 1.0e-4) {
            rememberConsumer(levelDiff > 0 ? BiasSide.B : BiasSide.A);
        }

        int targetA = computeTargetAmountForA(infoA, infoB, getCurrentBiasSide());

        int deltaA = infoA.amount() - targetA;
        if (deltaA == 0) {
            return;
        }

        boolean aToB = deltaA > 0;
        EndpointInfo source = aToB ? infoA : infoB;
        EndpointInfo target = aToB ? infoB : infoA;

        FluidStack transferFluid = getMainFluid(source.handler());
        if (transferFluid.isEmpty()) {
            return;
        }

        int maxToBalance = Math.abs(deltaA);
        if (maxToBalance <= 0) {
            return;
        }

        int rateLimit = calculateAdaptiveRate(Math.abs(levelDiff));
        if (total <= TAIL_TOTAL_THRESHOLD) {
            rateLimit = Math.min(rateLimit, 1);
        }

        int wanted = Math.min(rateLimit, maxToBalance);
        if (wanted <= 0) {
            return;
        }

        FluidStack simulatedDrain = source.handler().drain(transferFluid.copyWithAmount(wanted), IFluidHandler.FluidAction.SIMULATE);
        if (simulatedDrain.isEmpty()) {
            return;
        }

        int simulatedFill = target.handler().fill(simulatedDrain, IFluidHandler.FluidAction.SIMULATE);
        int move = Math.min(simulatedDrain.getAmount(), simulatedFill);
        move = Math.min(move, maxToBalance);

        if (move <= 0) {
            return;
        }

        FluidStack drained = source.handler().drain(transferFluid.copyWithAmount(move), IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) {
            return;
        }

        int filled = target.handler().fill(drained, IFluidHandler.FluidAction.EXECUTE);
        if (filled < drained.getAmount()) {
            CreateFluid.LOGGER.warn("[Vessel] fill mismatch at {} drained={} filled={}",
                    worldPosition, drained.getAmount(), filled);
        }
    }

    @Nullable
    private IFluidHandler getHandlerAtEnd(BlockPos endPos, Direction.Axis axis, Direction.AxisDirection direction) {
        Direction dir = Direction.fromAxisAndDirection(axis, direction);
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, endPos, dir.getOpposite());
        if (handler == null) {
            handler = level.getCapability(Capabilities.FluidHandler.BLOCK, endPos, null);
        }
        return handler;
    }

    private int computeTargetAmountForA(EndpointInfo a, EndpointInfo b, BiasSide biasSide) {
        int total = a.amount() + b.amount();
        if (total <= 0) {
            return 0;
        }

        int unbiasedTargetA = computeUnbiasedTargetAmountForA(a, b);

        if (total > TAIL_TOTAL_THRESHOLD) {
            return Mth.clamp(unbiasedTargetA, 0, total);
        }

        int bias = computeTailBias(total);
        if (bias <= 0 || biasSide == BiasSide.NONE) {
            return Mth.clamp(unbiasedTargetA, 0, total);
        }

        int targetA = unbiasedTargetA;
        if (biasSide == BiasSide.A) {
            targetA += bias;
        } else if (biasSide == BiasSide.B) {
            targetA -= bias;
        }

        return Mth.clamp(targetA, 0, total);
    }

    private int computeUnbiasedTargetAmountForA(EndpointInfo a, EndpointInfo b) {
        int total = a.amount() + b.amount();

        double invSlopeA = 1.0d / a.amountPerLevelUnit();
        double invSlopeB = 1.0d / b.amountPerLevelUnit();

        double targetAReal = (b.baseLevel() - a.baseLevel() + total * invSlopeB) / (invSlopeA + invSlopeB);
        int targetA = (int) Math.floor(targetAReal + 1.0e-9);

        return Mth.clamp(targetA, 0, total);
    }

    private int computeTailBias(int total) {
        if (total <= 1) return 0;
        return Math.min(MAX_TAIL_BIAS, 1);
    }

    private void rememberConsumer(BiasSide side) {
        if (side == BiasSide.NONE) return;
        rememberedConsumerSide = side;
        consumerBiasTicks = CONSUMER_MEMORY_TICKS;
    }

    private BiasSide getCurrentBiasSide() {
        if (consumerBiasTicks <= 0) {
            return BiasSide.NONE;
        }
        return rememberedConsumerSide;
    }

    private static int calculateAdaptiveRate(double levelDiff) {
        if (levelDiff > 1.0d) return 625;
        if (levelDiff > 0.5d) return 125;
        if (levelDiff > 0.1d) return 25;
        if (levelDiff > 0.01d) return 5;
        return 1;
    }

    private boolean isInfiniteFluidSource(BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof CreativeFluidTankBlockEntity) {
            return true;
        }
        if (be instanceof CopperSinkBlockEntity) {
            return CFCommonConfig.isCopperSinkInfinite();
        }
        return false;
    }

    private void tryFillFromInfinite(IFluidHandler infiniteHandler, IFluidHandler targetHandler) {
        FluidStack fluid = getMainFluid(infiniteHandler);
        if (fluid.isEmpty()) {
            return;
        }

        FluidStack targetFluid = getMainFluid(targetHandler);
        if (!targetFluid.isEmpty() && !FluidStack.isSameFluidSameComponents(fluid, targetFluid)) {
            return;
        }

        int capacity = getTotalCapacityForFluid(targetHandler, fluid);
        int current = getTotalAmountOfFluid(targetHandler, fluid);

        int toFill = capacity - current;
        if (toFill <= 0) {
            return;
        }

        FluidStack stack = fluid.copyWithAmount(toFill);
        int sim = targetHandler.fill(stack, IFluidHandler.FluidAction.SIMULATE);
        if (sim > 0) {
            targetHandler.fill(fluid.copyWithAmount(sim), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private boolean isValidConnector(BlockPos pos, Direction.Axis expectedAxis) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CommunicatingVesselBlock)) return false;
        return state.getValue(CommunicatingVesselBlock.AXIS) == expectedAxis;
    }

    private boolean hasAnyTank(IFluidHandler handler) {
        return handler.getTanks() > 0;
    }

    private FluidStack getMainFluid(IFluidHandler handler) {
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack fluid = handler.getFluidInTank(i);
            if (!fluid.isEmpty()) {
                return fluid;
            }
        }
        return FluidStack.EMPTY;
    }

    private int getTotalAmountOfFluid(IFluidHandler handler, FluidStack sample) {
        int total = 0;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack inTank = handler.getFluidInTank(i);
            if (!inTank.isEmpty() && FluidStack.isSameFluidSameComponents(inTank, sample)) {
                total += inTank.getAmount();
            }
        }
        return total;
    }

    private int getTotalCapacityForFluid(IFluidHandler handler, FluidStack sample) {
        int total = 0;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack inTank = handler.getFluidInTank(i);
            if (inTank.isEmpty() || FluidStack.isSameFluidSameComponents(inTank, sample)) {
                total += handler.getTankCapacity(i);
            }
        }
        return total;
    }

    @Nullable
    private EndpointInfo buildEndpointInfo(BlockPos pos, IFluidHandler handler) {
        FluidStack main = getMainFluid(handler);
        if (main.isEmpty()) {
            if (handler.getTanks() <= 0) return null;

            int fallbackCap = handler.getTankCapacity(0);
            if (fallbackCap <= 0) return null;

            return new EndpointInfo(pos, handler, 0, fallbackCap, pos.getY(), fallbackCap);
        }

        int amount = getTotalAmountOfFluid(handler, main);
        int capacity = getTotalCapacityForFluid(handler, main);
        if (capacity <= 0) {
            return null;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FluidTankBlockEntity tank) {
            FluidTankBlockEntity controller = tank.getControllerBE();
            if (controller != null) {
                int height = Math.max(controller.getHeight(), 1);
                int controllerCapacity = estimateControllerCapacity(controller, capacity);

                if (controllerCapacity > 0) {
                    return new EndpointInfo(
                            pos,
                            handler,
                            amount,
                            controllerCapacity,
                            controller.getBlockPos().getY(),
                            (double) controllerCapacity / (double) height
                    );
                }
            }
        }

        return new EndpointInfo(
                pos,
                handler,
                amount,
                capacity,
                pos.getY(),
                capacity
        );
    }

    private int estimateControllerCapacity(FluidTankBlockEntity controller, int fallbackCapacity) {
        return Math.max(fallbackCapacity, 1);
    }

    private record EndpointInfo(
            BlockPos pos,
            IFluidHandler handler,
            int amount,
            int capacity,
            double baseLevel,
            double amountPerLevelUnit
    ) {
        double level() {
            return baseLevel + (amountPerLevelUnit <= 0 ? 0 : ((double) amount / amountPerLevelUnit));
        }
    }

    private enum BiasSide {
        NONE,
        A,
        B
    }
}