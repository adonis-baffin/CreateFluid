package com.adonis.fluid.block.GutterOutlet;

import com.adonis.fluid.compat.TwilightForestHelper;
import com.adonis.fluid.config.CFCommonConfig;
import com.adonis.fluid.registry.CFFluid;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * 集水器方块实体
 * 处理流体存储、世界流体收集、雨雪收集、滴水石锥收集、向下排出
 */
public class GutterOutletBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    // 容量：2000 mB
    public static final int CAPACITY = 2000;

    // 每秒收集雨雪 50 mB（每 tick 约 2.5 mB，用累积方式处理）
    private static final int PRECIPITATION_COLLECT_RATE_PER_SECOND = 50;

    // 滴水石锥收集速率：每秒 10 mB
    private static final int DRIPSTONE_COLLECT_RATE_PER_SECOND = 10;

    // 每秒排出 1000 mB（每 tick 50 mB）
    private static final int DRAIN_RATE_PER_TICK = 50;

    protected SmartFluidTankBehaviour tankBehaviour;
    protected LazyOptional<IFluidHandler> fluidCapability;

    // 世界流体收集行为
    protected GutterFluidDrainingBehaviour drainer;

    // 用于雨雪收集累积
    private int precipitationAccumulator = 0;

    // 用于滴水石锥收集累积
    private int dripstoneAccumulator = 0;

    // 用于平滑液面渲染
    private LerpedFloat fluidLevel;
    private boolean forceFluidLevelUpdate;

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

        // 添加世界流体收集行为
        drainer = new GutterFluidDrainingBehaviour(this);
        behaviours.add(drainer);

        // 添加 DirectBeltInputBehaviour 以支持工作盆侧面输出检测
        behaviours.add(new DirectBeltInputBehaviour(this).allowingBeltFunnels());

        fluidCapability = LazyOptional.of(this::getFluidHandler);
    }

    private IFluidHandler getFluidHandler() {
        return tankBehaviour.getCapability()
                .resolve()
                .map(handler -> (IFluidHandler) handler)
                .orElseGet(() -> new FluidTank(0));
    }

    private void onFluidChanged() {
        if (level != null && !level.isClientSide) {
            setChanged();
            sendData();
        }

        // 更新液面渲染
        float fillState = getFillState();
        if (fluidLevel != null) {
            fluidLevel.chase(fillState, 0.5f, LerpedFloat.Chaser.EXP);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null) return;

        if (level.isClientSide) {
            if (fluidLevel != null) {
                fluidLevel.tickChaser();
            }
            return;
        }

        // 服务端逻辑
        boolean collectedWorldFluid = false;

        // 世界流体收集（受配置控制）
        if (CFCommonConfig.canGutterCollectWorldFluid()) {
            collectedWorldFluid = handleWorldFluidCollection();
        }

        // 雨雪收集（受配置控制）
        if (!collectedWorldFluid && !drainer.hasFluidToDrain()) {
            handlePrecipitationCollection();
        }

        // 滴水石锥收集（受配置控制）
        if (CFCommonConfig.canGutterCollectDripstone()) {
            handleDripstoneCollection();
        }

        handleDrainToBelow();
    }

    /**
     * 处理世界流体收集（从上方收集流体源）
     * @return 是否成功收集到流体
     */
    private boolean handleWorldFluidCollection() {
        if (level == null || level.isClientSide) return false;

        // 检查是否有流体可收集
        if (!drainer.hasFluidToDrain()) return false;

        FluidStack drainableFluid = drainer.getDrainableFluid(drainer.getRootPos());
        if (drainableFluid.isEmpty()) return false;

        FluidStack currentFluid = getFluid();

        // 检查是否与当前流体兼容
        if (!currentFluid.isEmpty() && !currentFluid.getFluid().isSame(drainableFluid.getFluid())) {
            return false;
        }

        // 检查是否有空间
        IFluidHandler tank = tankBehaviour.getCapability().orElse(null);
        if (tank == null) return false;

        int space = CAPACITY - currentFluid.getAmount();
        if (space < 1000 && !drainer.isInfinite()) {
            return false;
        }

        // 尝试收集
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

    /**
     * 处理雨雪收集（完全软兼容暮色森林的雨云使/雪云使，不强依赖）
     */
    private void handlePrecipitationCollection() {
        if (level == null || level.isClientSide) return;
        if (!level.canSeeSky(worldPosition.above())) return;

        // === 第一优先级：尝试调用暮色森林的云判断（软依赖）===
        if (TwilightForestHelper.isTwilightForestLoaded()) {
            Pair<Biome.Precipitation, Float> tfPrecip = TwilightForestHelper.getCloudPrecipitationAt(level, worldPosition.above());
            if (tfPrecip.getLeft() != Biome.Precipitation.NONE) {
                FluidStack current = getFluid();

                if (tfPrecip.getLeft() == Biome.Precipitation.RAIN) {
                    if (CFCommonConfig.canGutterCollectRain()
                            && (current.isEmpty() || current.getFluid().isSame(Fluids.WATER))) {
                        accumulateAndFill(new FluidStack(Fluids.WATER, 1), true);
                    }
                    return; // 已经处理了暮色雨，直接跳过原版判断
                } else if (tfPrecip.getLeft() == Biome.Precipitation.SNOW) {
                    if (CFCommonConfig.canGutterCollectSnow()
                            && (current.isEmpty() || CFFluid.isPowderSnowFluid(current.getFluid()))) {
                        accumulateAndFill(CFFluid.getPowderSnowFluidStack(1), true);
                    }
                    return;
                }
            }
        }

        // === 降级：原版天气判断 ===
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
                    && (current.isEmpty() || CFFluid.isPowderSnowFluid(current.getFluid()))) {
                accumulateAndFill(CFFluid.getPowderSnowFluidStack(1), true);
            }
        }
    }

    /**
     * 处理滴水石锥收集
     * 与原版炼药锅机制对齐
     */
    private void handleDripstoneCollection() {
        if (level == null || level.isClientSide) return;

        // 查找上方的滴水石锥尖端（使用原版方法）
        BlockPos tipPos = findStalactiteTipAbove();
        if (tipPos == null) return;

        // 获取滴水石锥能滴落的流体类型（使用原版方法）
        Fluid dripFluid = getDripFluid((ServerLevel) level, tipPos);
        if (dripFluid == Fluids.EMPTY) return;

        FluidStack currentFluid = getFluid();

        // 检查流体兼容性
        if (!currentFluid.isEmpty() && !currentFluid.getFluid().isSame(dripFluid)) {
            return;
        }

        // 累积收集（使用独立的累积器）
        accumulateAndFill(new FluidStack(dripFluid, 1), false);
    }

    /**
     * 查找集水器上方的滴水石锥尖端
     * 参考原版 PointedDripstoneBlock.findStalactiteTipAboveCauldron
     */
    @Nullable
    private BlockPos findStalactiteTipAbove() {
        BlockPos abovePos = worldPosition.above();
        BlockState aboveState = level.getBlockState(abovePos);

        // 检查上方是否是滴水石锥
        if (!aboveState.is(Blocks.POINTED_DRIPSTONE)) return null;

        // 检查是否尖端朝下
        if (aboveState.getValue(PointedDripstoneBlock.TIP_DIRECTION) != Direction.DOWN) return null;

        // 检查是否是尖端（TIP 或 TIP_MERGE）
        DripstoneThickness thickness = aboveState.getValue(PointedDripstoneBlock.THICKNESS);
        if (thickness != DripstoneThickness.TIP && thickness != DripstoneThickness.TIP_MERGE) return null;

        return abovePos;
    }

    /**
     * 获取滴水石锥能滴落的流体
     * 参考原版 PointedDripstoneBlock.getCauldronFillFluidType
     */
    private Fluid getDripFluid(ServerLevel level, BlockPos tipPos) {
        return PointedDripstoneBlock.getCauldronFillFluidType(level, tipPos);
    }

    /**
     * 累积收集流体
     * @param template 流体模板
     * @param isPrecipitation true=雨雪收集(50mB/s), false=滴水石锥收集(10mB/s)
     */
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

        int toFill = accumulator / 20; // 20 tick = 1 秒
        if (toFill > 0) {
            if (isPrecipitation) {
                precipitationAccumulator -= toFill * 20;
            } else {
                dripstoneAccumulator -= toFill * 20;
            }

            FluidStack toInsert = template.copy();
            toInsert.setAmount(toFill);

            IFluidHandler tank = tankBehaviour.getCapability().orElse(null);
            if (tank != null) {
                tank.fill(toInsert, IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    /**
     * 处理向下排出流体
     */
    protected void handleDrainToBelow() {
        if (level == null || level.isClientSide) return;

        FluidStack currentFluid = getFluid();
        if (currentFluid.isEmpty()) return;

        // 检测下方是否有流体容器
        BlockPos belowPos = worldPosition.below();
        BlockEntity belowBE = level.getBlockEntity(belowPos);
        if (belowBE == null) return;

        LazyOptional<IFluidHandler> belowCapability = belowBE.getCapability(
                ForgeCapabilities.FLUID_HANDLER, Direction.UP);

        if (!belowCapability.isPresent()) {
            belowCapability = belowBE.getCapability(ForgeCapabilities.FLUID_HANDLER);
        }

        belowCapability.ifPresent(targetTank -> {
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
        });
    }

    /**
     * 获取当前流体
     */
    public FluidStack getFluid() {
        if (tankBehaviour == null) return FluidStack.EMPTY;
        IFluidHandler tank = tankBehaviour.getCapability().orElse(null);
        if (tank == null) return FluidStack.EMPTY;
        return tank.getFluidInTank(0);
    }

    /**
     * 获取填充比例 (0-1)
     */
    public float getFillState() {
        FluidStack fluid = getFluid();
        if (fluid.isEmpty()) return 0;
        return (float) fluid.getAmount() / CAPACITY;
    }

    /**
     * 获取渲染用的液面高度（带插值）
     */
    public float getRenderedFluidLevel(float partialTicks) {
        if (fluidLevel == null) return getFillState();
        return fluidLevel.getValue(partialTicks);
    }

    /**
     * 获取液面 LerpedFloat（供渲染器使用）
     */
    public LerpedFloat getFluidLevel() {
        return fluidLevel;
    }

    /**
     * 获取流体收集行为（供外部使用）
     */
    public GutterFluidDrainingBehaviour getDrainer() {
        return drainer;
    }

    // ============== Capability ==============

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            if (side == null) {
                return fluidCapability.cast();
            }

            BlockState state = getBlockState();

            // 顶面：可输入
            if (side == Direction.UP) {
                return fluidCapability.cast();
            }

            if (side == Direction.DOWN) {
                return LazyOptional.of(() -> new OutputOnlyFluidHandler(getFluidHandler())).cast();
            }

            // 窄面：可输入输出
            if (GutterOutletBlock.isNarrowSide(state, side)) {
                return fluidCapability.cast();
            }

            // 宽面：不暴露
            return LazyOptional.empty();
        }

        return super.getCapability(cap, side);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (fluidCapability != null) {
            fluidCapability.invalidate();
        }
    }

    // ============== NBT ==============

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);

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
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);

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

    /**
     * 在工程师护目镜信息中添加收集状态
     */
    private void addCollectionStatusInfo(List<Component> tooltip) {
        // 世界流体收集状态
        if (drainer != null) {
            if (drainer.isCurrentlySearching()) {
                CreateLang.text("Searching...")
                        .style(ChatFormatting.YELLOW)
                        .forGoggles(tooltip, 1);
            } else if (drainer.isInfinite()) {
                CreateLang.text("∞ ")
                        .style(ChatFormatting.AQUA)
                        .add(CreateLang.text("Infinite Source")
                                .style(ChatFormatting.DARK_AQUA))
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

        // 滴水石锥收集状态（客户端安全版本）
        if (level != null) {
            BlockPos tipPos = findStalactiteTipAbove();
            if (tipPos != null) {
                // 客户端不调用 getCauldronFillFluidType，直接根据当前流体或简单判断显示
                FluidStack currentFluid = getFluid();
                if (!currentFluid.isEmpty()) {
                    String fluidName = currentFluid.getFluid().isSame(Fluids.LAVA) ? "Lava" : "Water";
                    CreateLang.text("☵ ")
                            .style(ChatFormatting.GRAY)
                            .add(CreateLang.text("Dripping " + fluidName)
                                    .style(currentFluid.getFluid().isSame(Fluids.LAVA) ? ChatFormatting.GOLD : ChatFormatting.AQUA))
                            .forGoggles(tooltip, 1);
                } else {
                    // 容器为空时，显示正在滴落
                    CreateLang.text("☵ ")
                            .style(ChatFormatting.GRAY)
                            .add(CreateLang.text("Dripstone Active")
                                    .style(ChatFormatting.GREEN))
                            .forGoggles(tooltip, 1);
                }
            }
        }
    }

    // ============== 内部类：仅输出的流体处理器包装 ==============

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
            return 0;
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
}