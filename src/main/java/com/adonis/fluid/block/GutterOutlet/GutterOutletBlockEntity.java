package com.adonis.fluid.block.GutterOutlet;

import com.adonis.fluid.config.CFCommonConfig;
import com.adonis.fluid.registry.CFBlockEntities;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
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
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class GutterOutletBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    public static final int CAPACITY = 2000;

    private static final int PRECIPITATION_COLLECT_RATE_PER_SECOND = 50;

    private static final int DRIPSTONE_COLLECT_RATE_PER_SECOND = 5;

    protected static final int DRAIN_RATE_PER_TICK = 50;

    private static final int MAX_DRIP_DISTANCE = 10; // 可配置，模仿原版最多10格

    public SmartFluidTankBehaviour tankBehaviour;

    protected GutterFluidDrainingBehaviour drainer;

    private int precipitationAccumulator = 0;

    private int dripstoneAccumulator = 0;

    private LerpedFloat fluidLevel;
    public boolean forceFluidLevelUpdate;

    private static final int SYNC_RATE = 8;
    protected int syncCooldown;
    protected boolean queuedSync;

    public GutterOutletBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        fluidLevel = LerpedFloat.linear().startWithValue(0);
        forceFluidLevelUpdate = true;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        tankBehaviour = new SmartFluidTankBehaviour(SmartFluidTankBehaviour.INPUT, this, 1, CAPACITY, true)
                .whenFluidUpdates(this::onFluidChanged);
        behaviours.add(tankBehaviour);

        drainer = new GutterFluidDrainingBehaviour(this);
        behaviours.add(drainer);

        behaviours.add(new DirectBeltInputBehaviour(this).allowingBeltFunnels());
    }

    public void onFluidChanged() {
        if (level != null && !level.isClientSide) {
            setChanged();
            sendData();
        }
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
            if (fluidLevel != null) {
                fluidLevel.tickChaser();
            }
            return;
        }

        tickCollection();
        handleDrainToBelow();
    }

    protected void tickCollection() {
        boolean collectedWorldFluid = false;

        if (CFCommonConfig.canGutterCollectWorldFluid()) {
            collectedWorldFluid = handleWorldFluidCollection();
        }

        if (!collectedWorldFluid && !drainer.hasFluidToDrain()) {
            handlePrecipitationCollection();
        }

        if (CFCommonConfig.canGutterCollectDripstone()) {
            handleDripstoneCollection();
        }
    }

    private boolean handleWorldFluidCollection() {
        if (level == null || level.isClientSide) return false;

        if (!drainer.hasFluidToDrain()) return false;

        FluidStack drainableFluid = drainer.getDrainableFluid(drainer.getRootPos());
        if (drainableFluid.isEmpty()) return false;

        FluidStack currentFluid = getFluid();

        if (!currentFluid.isEmpty() && !currentFluid.getFluid().isSame(drainableFluid.getFluid())) {
            return false;
        }

        IFluidHandler tank = tankBehaviour.getCapability();
        if (tank == null) return false;

        int space = CAPACITY - currentFluid.getAmount();
        if (space < 1000 && !drainer.isInfinite()) {
            return false;
        }

        FluidStack toFill = new FluidStack(drainableFluid.getFluid(), Math.min(1000, space));
        int accepted = tank.fill(toFill.copy(), IFluidHandler.FluidAction.SIMULATE);

        if (accepted <= 0) return false;

        if (drainer.isInfinite()) {
            if (drainer.pullNext(drainer.getRootPos(), false)) {
                tank.fill(new FluidStack(drainableFluid.getFluid(), accepted), IFluidHandler.FluidAction.EXECUTE);
                return true;
            }
        } else {
            if (accepted >= 1000) {
                if (drainer.pullNext(drainer.getRootPos(), false)) {
                    tank.fill(new FluidStack(drainableFluid.getFluid(), 1000), IFluidHandler.FluidAction.EXECUTE);
                    return true;
                }
            }
        }

        return false;
    }

    private void handlePrecipitationCollection() {
        if (level == null || level.isClientSide) return;
        if (!level.canSeeSky(worldPosition.above())) return;

        if (!level.isRaining()) return;

        Biome.Precipitation precipitation = level.getBiome(worldPosition).value()
                .getPrecipitationAt(worldPosition.above());
        if (precipitation == Biome.Precipitation.NONE) return;

        FluidStack current = getFluid();
        if (precipitation == Biome.Precipitation.RAIN) {
            if (CFCommonConfig.canGutterCollectRain()
                    && (current.isEmpty() || current.getFluid().isSame(Fluids.WATER))) {
                accumulateAndFill(new FluidStack(Fluids.WATER, 1), true);
            }
        } else if (precipitation == Biome.Precipitation.SNOW) {
            if (CFCommonConfig.canGutterCollectSnow()
                    && (current.isEmpty() || isPowderSnowFluid(current.getFluid()))) {
                accumulateAndFill(getPowderSnowFluidStack(1), true);
            }
        }
    }

    private boolean isPowderSnowFluid(Fluid fluid) {
        // 1.21.1 版本简化处理 - 粉雪流体检测
        // 在实际项目中可能需要根据模组添加的流体来判断
        return false;
    }

    private FluidStack getPowderSnowFluidStack(int amount) {
        // 1.21.1 版本简化处理 - 返回水作为替代
        return new FluidStack(Fluids.WATER, amount);
    }

    private void handleDripstoneCollection() {
        if (level == null || level.isClientSide) return;

        BlockPos tipPos = findStalactiteTipAbove();
        if (tipPos == null) return;

        Fluid dripFluid = getDripFluid((ServerLevel) level, tipPos);
        if (dripFluid == Fluids.EMPTY) return;

        FluidStack currentFluid = getFluid();

        if (!currentFluid.isEmpty() && !currentFluid.getFluid().isSame(dripFluid)) {
            return;
        }

        accumulateAndFill(new FluidStack(dripFluid, 1), false);
    }

    @Nullable
    private BlockPos findStalactiteTipAbove() {
        BlockPos current = worldPosition.above(); // 从正上方一格开始向上搜

        for (int i = 0; i <= MAX_DRIP_DISTANCE; i++) { // 向上最多搜10格（可调整）
            if (current.getY() > level.getMaxBuildHeight()) break;

            BlockState state = level.getBlockState(current);

            if (state.is(Blocks.POINTED_DRIPSTONE)) {
                if (state.getValue(PointedDripstoneBlock.TIP_DIRECTION) == Direction.DOWN) {
                    DripstoneThickness thickness = state.getValue(PointedDripstoneBlock.THICKNESS);
                    if (thickness == DripstoneThickness.TIP || thickness == DripstoneThickness.TIP_MERGE) {
                        // 找到尖端了，现在检查从这个 tip 向下到集水器的路径是否通畅（全是空气）
                        if (isPathClear(current, worldPosition)) {
                            return current; // 返回尖端位置
                        } else {
                            return null; // 中间有阻挡，不行
                        }
                    }
                }
                // 如果不是 tip，继续向上搜（因为尖端在更上面）
            } else if (!state.isAir()) {
                // 遇到非空气、非滴石的方块，直接停止搜索
                return null;
            }

            current = current.above();
        }

        return null;
    }

    private boolean isPathClear(BlockPos tipPos, BlockPos gutterPos) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        mutable.set(tipPos.below()); // 从 tip 正下方开始

        int distance = 0;
        while (!mutable.equals(gutterPos) && distance <= MAX_DRIP_DISTANCE) {
            if (!level.getBlockState(mutable).isAir()) {
                return false; // 中间有非空气方块阻挡
            }
            mutable.move(Direction.DOWN);
            distance++;
        }

        return mutable.equals(gutterPos); // 必须正好到达集水器位置
    }

    private Fluid getDripFluid(ServerLevel level, BlockPos tipPos) {
        return PointedDripstoneBlock.getCauldronFillFluidType(level, tipPos);
    }

    protected void accumulateAndFill(FluidStack template, boolean isPrecipitation) {
        int rate;
        int accumulator;

        if (isPrecipitation) {
            rate = PRECIPITATION_COLLECT_RATE_PER_SECOND;
            precipitationAccumulator += rate;
            accumulator = precipitationAccumulator;
        } else {
            rate = DRIPSTONE_COLLECT_RATE_PER_SECOND;
            dripstoneAccumulator += rate;
            accumulator = dripstoneAccumulator;
        }

        int toFill = accumulator / 20;
        if (toFill > 0) {
            if (isPrecipitation) {
                precipitationAccumulator -= toFill * 20;
            } else {
                dripstoneAccumulator -= toFill * 20;
            }

            FluidStack toInsert = template.copy();
            toInsert.setAmount(toFill);

            IFluidHandler tank = tankBehaviour.getCapability();
            if (tank != null) {
                tank.fill(toInsert, IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }


    protected void handleDrainToBelow() {
        if (level == null || level.isClientSide) return;

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

    protected IFluidHandler getFluidHandler() {
        IFluidHandler handler = tankBehaviour.getCapability();
        return handler != null ? handler : new FluidTank(0);
    }

    public FluidStack getFluid() {
        if (tankBehaviour == null) return FluidStack.EMPTY;
        IFluidHandler tank = tankBehaviour.getCapability();
        if (tank == null) return FluidStack.EMPTY;
        return tank.getFluidInTank(0);
    }

    public FluidTank getTankInventory() {
        if (tankBehaviour == null) return new FluidTank(0);
        return tankBehaviour.getPrimaryHandler();
    }

    public float getFillState() {
        FluidStack fluid = getFluid();
        if (fluid.isEmpty()) return 0;
        return (float) fluid.getAmount() / CAPACITY;
    }

    public float getRenderedFluidLevel(float partialTicks) {
        if (fluidLevel == null) return getFillState();
        return fluidLevel.getValue(partialTicks);
    }

    public LerpedFloat getFluidLevel() {
        return fluidLevel;
    }

    public GutterFluidDrainingBehaviour getDrainer() {
        return drainer;
    }

    // ============== Capability Registration ==============

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                CFBlockEntities.GUTTER_OUTLET.get(),
                (be, side) -> {
                    if (side == null) {
                        return be.getFluidHandler();
                    }

                    BlockState state = be.getBlockState();

                    if (side == Direction.UP) {
                        return be.getFluidHandler();
                    }

                    if (side == Direction.DOWN) {
                        IFluidHandler handler = be.getFluidHandler();
                        if (handler instanceof FluidTank && ((FluidTank) handler).getCapacity() == 0) {
                            return null;
                        }
                        return new OutputOnlyFluidHandler(be.getFluidHandler());
                    }

                    if (GutterOutletBlock.isNarrowSide(state, side)) {
                        return be.getFluidHandler();
                    }

                    return null;
                }
        );
    }

    // ============== Output Only Fluid Handler ==============

    /**
     * 只输出不输入的流体处理器包装类
     * 用于集水器的底面，允许向下排水但不允许从底部输入
     */
    protected static class OutputOnlyFluidHandler implements IFluidHandler {
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
            return false; // 不允许输入，所以返回false
        }

        @Override
        public int fill(@Nonnull FluidStack resource, FluidAction action) {
            return 0; // 不允许输入，返回0
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

    // ============== NBT ==============

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);

        precipitationAccumulator = compound.getInt("PrecipitationAccumulator");
        dripstoneAccumulator = compound.getInt("DripstoneAccumulator");

        if (clientPacket) {
            float fillState = getFillState();
            if (compound.contains("ForceFluidLevel") || fluidLevel == null) {
                fluidLevel = LerpedFloat.linear().startWithValue(fillState);
            }
            fluidLevel.chase(fillState, 0.5f, LerpedFloat.Chaser.EXP);
        }
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);

        compound.putInt("PrecipitationAccumulator", precipitationAccumulator);
        compound.putInt("DripstoneAccumulator", dripstoneAccumulator);

        if (clientPacket && forceFluidLevelUpdate) {
            compound.putBoolean("ForceFluidLevel", true);
            forceFluidLevelUpdate = false;
        }
    }

    // ============== Goggle ==============

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.translate("gui.goggles.fluid_container")
                .forGoggles(tooltip);

        FluidStack fluid = getFluid();
        if (fluid.isEmpty()) {
            CreateLang.translate("gui.goggles.fluid_container.capacity")
                    .add(CreateLang.number(CAPACITY)
                            .add(CreateLang.translate("generic.unit.millibuckets"))
                            .style(ChatFormatting.DARK_GREEN))
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);

            addCollectionStatusInfo(tooltip);
            return true;
        }

        LangBuilder mb = CreateLang.translate("generic.unit.millibuckets");
        CreateLang.text("")
                .add(CreateLang.fluidName(fluid)
                        .add(CreateLang.text(" "))
                        .style(ChatFormatting.GRAY)
                        .add(CreateLang.number(fluid.getAmount())
                                .add(mb)
                                .style(ChatFormatting.BLUE)))
                .forGoggles(tooltip, 1);

        CreateLang.translate("gui.goggles.fluid_container.capacity")
                .add(CreateLang.number(CAPACITY)
                        .add(mb)
                        .style(ChatFormatting.DARK_GREEN))
                .style(ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);

        addCollectionStatusInfo(tooltip);

        return true;
    }

    private void addCollectionStatusInfo(List<Component> tooltip) {
        if (drainer != null && drainer.isInfinite()) {
            TooltipHelper.addHint(tooltip, "hint.hose_pulley");
            return;
        }

        if (drainer != null) {
            if (drainer.isCurrentlySearching()) {
                CreateLang.text("Searching...")
                        .style(ChatFormatting.YELLOW)
                        .forGoggles(tooltip, 1);
            } else if (drainer.hasFluidToDrain()) {
                int sourceCount = drainer.getSourceCount();
                CreateLang.text("")
                        .add(CreateLang.number(sourceCount)
                                .style(ChatFormatting.GOLD))
                        .add(CreateLang.text(" Source Blocks")
                                .style(ChatFormatting.GRAY))
                        .forGoggles(tooltip, 1);
            }
        }

        if (level != null) {
            BlockPos tipPos = findStalactiteTipAbove();
            if (tipPos != null) {
                FluidStack currentFluid = getFluid();
                if (!currentFluid.isEmpty()) {
                    boolean isLava = currentFluid.getFluid().isSame(Fluids.LAVA);
                    CreateLang.translate("gui.goggles.gutter_outlet.dripping")
                            .style(ChatFormatting.GRAY)
                            .add(CreateLang.fluidName(currentFluid)  // 自动使用流体的本地化名称，并带正确颜色
                                    .style(isLava ? ChatFormatting.GOLD : ChatFormatting.AQUA))
                            .forGoggles(tooltip, 1);
                } else {
                    CreateLang.translate("gui.goggles.gutter_outlet.dripstone_active")
                            .style(ChatFormatting.GREEN)
                            .forGoggles(tooltip, 1);
                }
            }
        }
    }
}
