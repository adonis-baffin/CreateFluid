package com.adonis.fluid.block.GutterOutlet;

import com.adonis.fluid.registry.CFBlockEntity;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 集水器方块实体
 * 处理流体存储、雨雪收集、向下排出
 */
public class GutterOutletBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    // 容量：1500 mB
    public static final int CAPACITY = 1500;

    // 每秒收集 50 mB（每 tick 约 2.5 mB，用累积方式处理）
    private static final int COLLECT_RATE_PER_SECOND = 50;

    // 每秒排出 1000 mB（每 tick 50 mB）
    private static final int DRAIN_RATE_PER_TICK = 50;

    protected SmartFluidTankBehaviour tankBehaviour;
    protected LazyOptional<IFluidHandler> fluidCapability;

    // 用于收集累积（因为每 tick 2.5 mB 不是整数）
    private int collectAccumulator = 0;

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

        // 添加 DirectBeltInputBehaviour 以支持工作盆侧面输出检测
        // 这个行为本身用于物品传输，但工作盆的 canOutputTo 会检查它
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
            // 客户端：更新液面动画
            if (fluidLevel != null) {
                fluidLevel.tickChaser();
            }
            return;
        }

        // 服务端逻辑
        handlePrecipitationCollection();
        handleDrainToBelow();
    }

    /**
     * 处理雨雪收集
     */
    private void handlePrecipitationCollection() {
        if (level == null || level.isClientSide) return;

        // 检查是否能看到天空
        if (!level.canSeeSky(worldPosition.above())) return;

        // 检查是否在下雨/雪
        if (!level.isRaining()) return;

        Biome.Precipitation precipitation = level.getBiome(worldPosition).value()
                .getPrecipitationAt(worldPosition);

        if (precipitation == Biome.Precipitation.NONE) return;

        FluidStack currentFluid = getFluid();

        if (precipitation == Biome.Precipitation.RAIN) {
            // 下雨：收集水
            // 如果容器内有非水流体，不收集
            if (!currentFluid.isEmpty() && !currentFluid.getFluid().isSame(Fluids.WATER)) {
                return;
            }

            accumulateAndFill(new FluidStack(Fluids.WATER, 1));

        } else if (precipitation == Biome.Precipitation.SNOW) {
            // 下雪：收集细雪流体
            // 如果容器内有非细雪流体，不收集
            if (!currentFluid.isEmpty() && !CFFluid.isPowderSnowFluid(currentFluid.getFluid())) {
                return;
            }

            accumulateAndFill(CFFluid.getPowderSnowFluidStack(1));
        }
    }

    /**
     * 累积收集流体（处理非整数每 tick 收集量）
     */
    private void accumulateAndFill(FluidStack template) {
        // 每秒 50 mB = 每 tick 2.5 mB
        // 使用累积器：每 tick 加 50，每累积到 20 就填充 1 mB
        collectAccumulator += COLLECT_RATE_PER_SECOND;

        int toFill = collectAccumulator / 20; // 20 tick = 1 秒
        if (toFill > 0) {
            collectAccumulator -= toFill * 20;

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
    private void handleDrainToBelow() {
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
            // 尝试无方向获取
            belowCapability = belowBE.getCapability(ForgeCapabilities.FLUID_HANDLER);
        }

        belowCapability.ifPresent(targetTank -> {
            IFluidHandler myTank = getFluidHandler();
            if (myTank == null) return;

            // 尝试排出
            int drainAmount = Math.min(DRAIN_RATE_PER_TICK, currentFluid.getAmount());
            FluidStack toDrain = new FluidStack(currentFluid.getFluid(), drainAmount);

            // 先模拟填充目标
            int accepted = targetTank.fill(toDrain.copy(), IFluidHandler.FluidAction.SIMULATE);
            if (accepted > 0) {
                // 实际执行
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

    // ============== Capability ==============

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            // 根据方向决定是否暴露能力
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

            // 东西窄面：可输入输出
            if (GutterOutletBlock.isNarrowSide(state, side)) {
                return fluidCapability.cast();
            }

            // 南北宽面：不暴露
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

        collectAccumulator = compound.getInt("CollectAccumulator");

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

        compound.putInt("CollectAccumulator", collectAccumulator);

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

        return true;
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
            return false; // 不允许输入
        }

        @Override
        public int fill(@Nonnull FluidStack resource, FluidAction action) {
            return 0; // 不允许输入
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