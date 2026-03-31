package com.adonis.fluid.block.GutterOutlet;

import com.adonis.fluid.config.CFCommonConfig;
import com.adonis.fluid.registry.CFBlockEntities;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;


public class SmartGutterOutletBlockEntity extends GutterOutletBlockEntity {

    private static final int MAX_DRIP_DISTANCE = 10; // 可配置，建议 10 模仿原版坩埚
    private static final int SYNC_RATE = 8;
    protected int syncCooldown;
    protected boolean queuedSync;

    protected FilteringBehaviour filtering;

    public SmartGutterOutletBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        filtering = new FilteringBehaviour(this, new SmartGutterOutletFilterSlot())
                .forFluids();
        behaviours.add(filtering);

        super.addBehaviours(behaviours);
    }

    public void sendDataImmediately() {
        syncCooldown = 0;
        queuedSync = false;
        sendData();
    }

    @Override
    public void sendData() {
        if (syncCooldown > 0) {
            queuedSync = true;
            return;
        }
        super.sendData();
        queuedSync = false;
        syncCooldown = SYNC_RATE;
    }

    protected boolean canActivate() {
        BlockState blockState = getBlockState();
        return blockState.hasProperty(SmartGutterOutletBlock.POWERED)
                && !blockState.getValue(SmartGutterOutletBlock.POWERED);
    }

    protected boolean testFluidFilter(FluidStack fluid) {
        if (filtering == null) return true;
        return filtering.test(fluid);
    }

    protected boolean testFluidFilter(Fluid fluid) {
        if (fluid == null || fluid == Fluids.EMPTY) return false;
        return testFluidFilter(new FluidStack(fluid, 1000));
    }

    @Override
    public void tick() {
        super.tick();
        if (syncCooldown > 0) {
            syncCooldown--;
            if (syncCooldown == 0 && queuedSync)
                sendData();
        }
        if (level == null) return;

        if (level.isClientSide) {
            if (getFluidLevel() != null) {
                getFluidLevel().tickChaser();
            }
            forEachBehaviour(BlockEntityBehaviour::tick);
            return;
        }

        forEachBehaviour(BlockEntityBehaviour::tick);

        if (!canActivate()) {
            handleDrainToBelow();
            return;
        }

        boolean collectedWorldFluid = false;

        if (CFCommonConfig.canGutterCollectWorldFluid()) {
            collectedWorldFluid = handleWorldFluidCollectionFiltered();
        }

        if (!collectedWorldFluid && !getDrainer().hasFluidToDrain()) {
            handlePrecipitationCollectionFiltered();
        }

        if (CFCommonConfig.canGutterCollectDripstone()) {
            handleDripstoneCollectionFiltered();
        }

        handleDrainToBelow();
    }

    private boolean handleWorldFluidCollectionFiltered() {
        if (level == null || level.isClientSide) return false;

        if (!getDrainer().hasFluidToDrain()) return false;

        FluidStack drainableFluid = getDrainer().getDrainableFluid(getDrainer().getRootPos());
        if (drainableFluid.isEmpty()) return false;

        if (!testFluidFilter(drainableFluid)) return false;

        FluidStack currentFluid = getFluid();

        if (!currentFluid.isEmpty() && !currentFluid.getFluid().isSame(drainableFluid.getFluid())) {
            return false;
        }

        IFluidHandler tank = tankBehaviour.getCapability();
        if (tank == null) return false;

        int space = CAPACITY - currentFluid.getAmount();
        if (space < 1000 && !getDrainer().isInfinite()) {
            return false;
        }

        FluidStack toFill = new FluidStack(drainableFluid.getFluid(), Math.min(1000, space));
        int accepted = tank.fill(toFill.copy(), IFluidHandler.FluidAction.SIMULATE);

        if (accepted <= 0) return false;

        if (getDrainer().isInfinite()) {
            if (getDrainer().pullNext(getDrainer().getRootPos(), false)) {
                tank.fill(new FluidStack(drainableFluid.getFluid(), accepted), IFluidHandler.FluidAction.EXECUTE);
                return true;
            }
        } else {
            if (accepted >= 1000) {
                if (getDrainer().pullNext(getDrainer().getRootPos(), false)) {
                    tank.fill(new FluidStack(drainableFluid.getFluid(), 1000), IFluidHandler.FluidAction.EXECUTE);
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    protected void handleDrainToBelow() {
        if (level == null || level.isClientSide) return;

        // 添加智能集水器专属的红石关闭逻辑
        BlockState state = getBlockState();
        if (state.hasProperty(SmartGutterOutletBlock.POWERED)
                && state.getValue(SmartGutterOutletBlock.POWERED)) {
            return; // 有红石信号 = 关闭状态，不向下排水
        }

        // 原有排水逻辑（保持不变）
        FluidStack currentFluid = getFluid();
        if (currentFluid.isEmpty()) return;

        BlockPos belowPos = worldPosition.below();
        // NeoForge 1.21.1: 使用 level.getCapability 获取流体处理能力
        IFluidHandler targetTank = level.getCapability(Capabilities.FluidHandler.BLOCK, belowPos, Direction.UP);

        if (targetTank == null) {
            targetTank = level.getCapability(Capabilities.FluidHandler.BLOCK, belowPos, null);
        }

        if (targetTank != null) {
            IFluidHandler myTank = getFluidHandler();
            if (myTank == null) return;

            int drainAmount = Math.min(DRAIN_RATE_PER_TICK, currentFluid.getAmount());
            FluidStack toDrain = new FluidStack(currentFluid.getFluid(), drainAmount);

            int accepted = targetTank.fill(toDrain.copy(), IFluidHandler.FluidAction.SIMULATE);
            if (accepted > 0) {
                FluidStack drained = myTank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
                if (!drained.isEmpty()) {
                    targetTank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                }
            }
        }
    }

    @Nonnull
    public IFluidHandler getFluidHandlerForSide(@Nullable Direction side) {
        BlockState state = getBlockState();
        boolean isPowered = state.hasProperty(SmartGutterOutletBlock.POWERED)
                && state.getValue(SmartGutterOutletBlock.POWERED);

        if (isPowered) {
            // 有红石信号 = 关闭状态 → 完全不暴露 fluid capability
            return new EmptyFluidHandler();
        }

        // 无红石信号 = 开启状态 → 正常行为
        if (side == null) {
            return getFluidHandler();
        }

        if (side == Direction.UP) {
            return getFluidHandler();
        }

        if (side == Direction.DOWN) {
            // 返回只输出不输入的处理器
            IFluidHandler tank = tankBehaviour.getCapability();
            if (tank == null) {
                return new EmptyFluidHandler();
            }
            return new OutputOnlyFluidHandler(tank);
        }

        if (GutterOutletBlock.isNarrowSide(state, side)) {
            return getFluidHandler();
        }

        return new EmptyFluidHandler();
    }

    // 空流体处理器（用于红石关闭状态）
    private static class EmptyFluidHandler implements IFluidHandler {
        @Override
        public int getTanks() { return 0; }
        @Override
        public FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }
        @Override
        public int getTankCapacity(int tank) { return 0; }
        @Override
        public boolean isFluidValid(int tank, FluidStack stack) { return false; }
        @Override
        public int fill(FluidStack resource, FluidAction action) { return 0; }
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
    }

    // 在 SmartGutterOutletBlockEntity 类内部新增这个私有静态内部类
    private static class OutputOnlyFluidHandler implements IFluidHandler {
        private final IFluidHandler wrapped;

        public OutputOnlyFluidHandler(IFluidHandler wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public int getTanks() {
            return wrapped.getTanks();
        }

        @Override
        public @Nonnull FluidStack getFluidInTank(int tank) {
            return wrapped.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return wrapped.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
            return false;
        }

        @Override
        public int fill(@Nonnull FluidStack resource, FluidAction action) {
            return 0; // 只允许输出，不允许输入
        }

        @Override
        public @Nonnull FluidStack drain(FluidStack resource, FluidAction action) {
            return wrapped.drain(resource, action);
        }

        @Override
        public @Nonnull FluidStack drain(int maxDrain, FluidAction action) {
            return wrapped.drain(maxDrain, action);
        }
    }

    private void handlePrecipitationCollectionFiltered() {
        if (level == null || level.isClientSide) return;
        if (!level.canSeeSky(worldPosition.above())) return;

        FluidStack currentFluid = getFluid();

        if (!level.isRaining()) return;

        Biome.Precipitation precipitation = level.getBiome(worldPosition).value()
                .getPrecipitationAt(worldPosition.above());
        if (precipitation == Biome.Precipitation.NONE) return;

        if (precipitation == Biome.Precipitation.RAIN) {
            if (!CFCommonConfig.canGutterCollectRain()) return;
            if (!testFluidFilter(Fluids.WATER)) return;
            if (!currentFluid.isEmpty() && !currentFluid.getFluid().isSame(Fluids.WATER)) return;

            accumulateAndFill(new FluidStack(Fluids.WATER, 1), true);

        } else if (precipitation == Biome.Precipitation.SNOW) {
            if (!CFCommonConfig.canGutterCollectSnow()) return;
            // 简化的粉雪处理 - 使用水代替
            FluidStack snowStack = new FluidStack(Fluids.WATER, 1);
            if (!testFluidFilter(snowStack)) return;
            if (!currentFluid.isEmpty() && !currentFluid.getFluid().isSame(Fluids.WATER)) return;

            accumulateAndFill(snowStack, true);
        }
    }

    private void handleDripstoneCollectionFiltered() {
        if (level == null || level.isClientSide) return;

        BlockPos tipPos = findStalactiteTipAboveInternal();
        if (tipPos == null) return;

        Fluid dripFluid = PointedDripstoneBlock.getCauldronFillFluidType((ServerLevel) level, tipPos);
        if (dripFluid == Fluids.EMPTY) return;

        if (!testFluidFilter(dripFluid)) return;

        FluidStack currentFluid = getFluid();

        if (!currentFluid.isEmpty() && !currentFluid.getFluid().isSame(dripFluid)) {
            return;
        }

        accumulateAndFill(new FluidStack(dripFluid, 1), false);
    }

    @Nullable
    private BlockPos findStalactiteTipAboveInternal() {
        if (level == null || level.isClientSide) return null;

        BlockPos.MutableBlockPos searchPos = new BlockPos.MutableBlockPos();
        searchPos.set(worldPosition.above()); // 从正上方一格开始向上搜索

        for (int i = 0; i <= MAX_DRIP_DISTANCE; i++) {
            if (searchPos.getY() > level.getMaxBuildHeight()) break;

            BlockState state = level.getBlockState(searchPos);

            if (state.is(Blocks.POINTED_DRIPSTONE)) {
                if (state.getValue(PointedDripstoneBlock.TIP_DIRECTION) == Direction.DOWN) {
                    DripstoneThickness thickness = state.getValue(PointedDripstoneBlock.THICKNESS);
                    if (thickness == DripstoneThickness.TIP || thickness == DripstoneThickness.TIP_MERGE) {
                        // 找到尖端了，检查从尖端正下方到集水器是否路径通畅（全是空气）
                        if (isDripPathClear(searchPos, worldPosition)) {
                            return searchPos.immutable(); // 返回找到的尖端位置
                        } else {
                            return null; // 路径被阻挡，不行
                        }
                    }
                }
                // 不是尖端，继续向上搜索（滴石更长）
            } else if (!state.isAir() && state.getFluidState().isEmpty()) {
                // 遇到非空气、非流体（如固体方块）的阻挡，直接停止
                return null;
            }
            // 否则是空气或流体，继续向上
            searchPos.move(Direction.UP);
        }

        return null; // 没找到符合条件的尖端
    }

    private boolean isDripPathClear(BlockPos tipPos, BlockPos gutterPos) {
        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();
        checkPos.set(tipPos.below()); // 从尖端正下方开始向下检查

        int distance = 0;
        while (distance <= MAX_DRIP_DISTANCE) {
            if (checkPos.equals(gutterPos)) {
                return true; // 成功到达集水器位置
            }

            BlockState state = level.getBlockState(checkPos);
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                return false; // 被固体方块阻挡（流体不算阻挡，原版也允许穿过水）
            }

            checkPos.move(Direction.DOWN);
            distance++;
        }

        return false; // 超过最大距离还没到达
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
    }

    // ============== Capability Registration ==============

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                CFBlockEntities.SMART_GUTTER_OUTLET.get(),
                (be, side) -> be.getFluidHandlerForSide(side)
        );
    }
}
