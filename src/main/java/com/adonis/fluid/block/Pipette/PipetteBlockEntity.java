package com.adonis.fluid.block.Pipette;

import com.adonis.fluid.content.pipette.*;
import com.simibubi.create.AllPackets;
import com.simibubi.create.api.contraption.transformable.TransformableBlockEntity;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class PipetteBlockEntity extends KineticBlockEntity implements TransformableBlockEntity, IRemoteFluidProcessor, IHaveGoggleInformation {
    // 字段定义
    public List<FluidInteractionPoint> inputs = new ArrayList<>();
    public List<FluidInteractionPoint> outputs = new ArrayList<>();
    public ListTag interactionPointTag = null;
    float chasedPointProgress;
    int chasedPointIndex;
    public FluidStack heldFluid;
    Phase phase;
    public boolean goggles;
    PipetteAngleTarget previousTarget;
    public LerpedFloat lowerArmAngle;
    public LerpedFloat upperArmAngle;
    public LerpedFloat baseAngle;
    public LerpedFloat headAngle;
    public LerpedFloat clawAngle;
    float previousBaseAngle;
    boolean updateInteractionPoints;
    int tooltipWarmup;
    protected ScrollOptionBehaviour<SelectionMode> selectionMode;
    protected int lastInputIndex = -1;
    protected int lastOutputIndex = -1;
    protected boolean redstoneLocked;
    private ItemStack processingItem = ItemStack.EMPTY;
    private ItemStack processingResult = ItemStack.EMPTY;
    private int processingTicks = 0;
    private static final int PROCESSING_TIME = 20;
    private VirtualRelayManager.VirtualRelay activeRelay = null;

    // 修改速度阈值常量
    private static final float HIGH_SPEED_THRESHOLD = 64.0F;
    private static final float LOW_SPEED_THRESHOLD = 32.0F;
    private float previousProgressForInjection = 0.0F;
    private boolean continuousProcessing = false;
    private int continuousProcessingCount = 0;

    // 在字段定义区域添加
    protected int returnTargetIndex = -1;
    private FluidStack pendingFluidForItem = FluidStack.EMPTY;  // 记录需要取的流体类型
    private int pendingInputIndex = -1;  // 记录需要去取液的输入点索引

    // 添加速度模式枚举
    public enum SpeedMode {
        ULTRA_LOW,  // < 32
        LOW,        // 32-64
        HIGH        // >= 64
    }

    // 添加传送带处理相关字段
    private boolean processingBelt = false;
    private BlockPos processingBeltPos = null;
    private boolean isServingBelt = false;
    private int beltProcessingTicks = 0;
    private boolean isPerformingInjection = false;
    private float injectionStartProgress = 0.8f;

    // 流体相关常量
    private static final int TRANSFER_AMOUNT = 1000;
    private static final int FLUID_CAPACITY = 1000;

    private BlockPos pendingBeltRequest = null;
    private ItemStack pendingBeltItem = ItemStack.EMPTY;

    public PipetteBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        this.heldFluid = FluidStack.EMPTY;
        this.phase = Phase.SEARCH_INPUTS;
        this.previousTarget = PipetteAngleTarget.NO_TARGET;
        this.baseAngle = LerpedFloat.angular();
        this.baseAngle.startWithValue(this.previousTarget.baseAngle);
        this.lowerArmAngle = LerpedFloat.angular();
        this.lowerArmAngle.startWithValue(this.previousTarget.lowerArmAngle);
        this.upperArmAngle = LerpedFloat.angular();
        this.upperArmAngle.startWithValue(this.previousTarget.upperArmAngle);
        this.headAngle = LerpedFloat.angular();
        this.headAngle.startWithValue(this.previousTarget.headAngle);
        this.clawAngle = LerpedFloat.angular();
        this.previousBaseAngle = this.previousTarget.baseAngle;
        this.updateInteractionPoints = true;
        this.redstoneLocked = false;
        this.tooltipWarmup = 15;
        this.goggles = false;
    }

    public int getFluidCapacity() {
        return FLUID_CAPACITY;
    }

    public boolean isInjectMode() {
        return this.phase == Phase.SEARCH_OUTPUTS || this.phase == Phase.MOVE_TO_OUTPUT;
    }

    public boolean isWorking() {
        return this.phase == Phase.MOVE_TO_INPUT || this.phase == Phase.MOVE_TO_OUTPUT;
    }

    public float getWorkProgress() {
        return this.chasedPointProgress;
    }

    public boolean isReadyToInject() {
        return this.phase == Phase.MOVE_TO_OUTPUT &&
                this.chasedPointProgress >= injectionStartProgress;
    }

    public SpeedMode getSpeedMode() {
        float speed = Math.abs(this.getSpeed());
        if (speed < LOW_SPEED_THRESHOLD) return SpeedMode.ULTRA_LOW;
        if (speed < HIGH_SPEED_THRESHOLD) return SpeedMode.LOW;
        return SpeedMode.HIGH;
    }

    public boolean isHighSpeed() {
        return getSpeedMode() == SpeedMode.HIGH;
    }

    public boolean isLowSpeed() {
        return getSpeedMode() == SpeedMode.LOW;
    }

    public boolean isUltraLowSpeed() {
        return getSpeedMode() == SpeedMode.ULTRA_LOW;
    }

    public boolean isContinuousProcessing() {
        return continuousProcessing;
    }

    public void startContinuousProcessing() {
        continuousProcessing = true;
        continuousProcessingCount = 0;
    }

    public void incrementContinuousProcessing() {
        continuousProcessingCount++;
    }

    public void endContinuousProcessing() {
        continuousProcessing = false;
        continuousProcessingCount = 0;
    }

    // 高速处理
    private void handleHighSpeedInjection() {
        injectionStartProgress = 0.8f;

        if (this.phase == Phase.MOVE_TO_OUTPUT && processingBelt) {
            if (continuousProcessing && this.chasedPointProgress >= 0.9F) {
                this.chasedPointProgress = Math.min(this.chasedPointProgress, 0.95F);
            }

            if (!isPerformingInjection &&
                    previousProgressForInjection < injectionStartProgress &&
                    this.chasedPointProgress >= injectionStartProgress) {
                isPerformingInjection = true;
                notifyRelayInjectionReady(processingBeltPos);
            }

            if (this.chasedPointProgress >= 1.0F && !continuousProcessing) {
                isPerformingInjection = false;
            }
        }
    }

    // 低速处理
    private void handleLowSpeedInjection() {
        injectionStartProgress = 0.6f;

        if (this.phase == Phase.MOVE_TO_OUTPUT && processingBelt) {
            if (this.chasedPointProgress >= 0.9F && continuousProcessing) {
                this.chasedPointProgress = 1.0F;
            }

            if (!isPerformingInjection && this.chasedPointProgress >= injectionStartProgress) {
                isPerformingInjection = true;
                notifyRelayInjectionReady(processingBeltPos);

                if (!level.isClientSide) {
                    beltProcessingTicks = 30;
                }
            }

            if (this.chasedPointProgress >= 1.0F && !continuousProcessing) {
                isPerformingInjection = false;
            }
        }
    }

    // 超低速处理
    private void handleUltraLowSpeedInjection() {
        // 超低速时非常早触发
        injectionStartProgress = 0.3f;

        if (this.phase == Phase.MOVE_TO_OUTPUT && processingBelt) {
            // 超低速时，一旦开始移动就通知
            if (!isPerformingInjection && this.chasedPointProgress > 0.1F) {
                isPerformingInjection = true;
                // 立即通知，让物品等待移液器
                notifyRelayInjectionReady(processingBeltPos);

                if (!level.isClientSide) {
                    // 根据实际速度计算需要的时间
                    float speed = Math.abs(this.getSpeed());
                    int ticksToComplete = (int)((1.0F - this.chasedPointProgress) * 1024.0F / Math.max(speed, 1.0F));
                    beltProcessingTicks = Math.max(ticksToComplete + 10, 40);
                }
            }

            // 保持状态直到真正完成
            if (this.chasedPointProgress >= 1.0F) {
                isPerformingInjection = false;
            }
        }
    }

    public boolean willReachInjectionPoint() {
        if (this.phase != Phase.MOVE_TO_OUTPUT) return false;

        // 只在高速时使用预测
        if (!isHighSpeed()) return false;

        float speed = Math.abs(this.getSpeed());
        float increment = Math.min(256.0F, speed) / 1024.0F;

        if (speed >= 256.0F && increment > 0.2F) {
            increment = 0.2F;
        }

        float nextProgress = this.chasedPointProgress + increment;

        return this.chasedPointProgress < injectionStartProgress &&
                nextProgress >= injectionStartProgress;
    }

    public void notifyRelayInjectionReady(BlockPos beltPos) {
        // 通知虚拟中继器可以开始注液
        VirtualRelayManager.notifyInjectionReady(beltPos);
    }

    public void resetMovementState() {
        this.phase = Phase.SEARCH_INPUTS;
        this.chasedPointProgress = 0.0F;
        this.chasedPointIndex = -1;
    }

    public void forceInitInteractionPoints() {
        this.initInteractionPoints();
    }

    public void forceReloadInteractionPoints() {
        if (interactionPointTag != null && level != null) {
            inputs.clear();
            outputs.clear();

            for (Tag tag : interactionPointTag) {
                FluidInteractionPoint point = FluidInteractionPoint.deserialize(
                        (CompoundTag) tag, level, worldPosition);
                if (point != null) {
                    if (point.getMode() == FluidInteractionPoint.Mode.TAKE) {
                        inputs.add(point);
                    } else {
                        outputs.add(point);
                    }
                }
            }

            if (!level.isClientSide) {
                VirtualRelayManager.updateWorkstationRelays(worldPosition, level);
            }

            updateInteractionPoints = false;
            sendData();
            setChanged();
        }
    }

    public FluidStack getHeldFluid() {
        return this.heldFluid.copy();
    }

    public boolean hasFluid() {
        return !this.heldFluid.isEmpty();
    }

    @Override
    public void startFluidProcessing(ItemStack stack, VirtualRelayManager.VirtualRelay relay) {
        ItemStack singleItem = stack.copy();
        singleItem.setCount(1);

        processingItem = singleItem;
        activeRelay = relay;
        processingTicks = 0;

        int required = com.simibubi.create.content.fluids.spout.FillingBySpout
                .getRequiredAmountForItem(level, singleItem, heldFluid);

        if (required > 0 && required <= heldFluid.getAmount()) {
            FluidStack fluidForFilling = heldFluid.copy();
            fluidForFilling.setAmount(required);

            processingResult = com.simibubi.create.content.fluids.spout.FillingBySpout
                    .fillItem(level, required, singleItem, fluidForFilling);

            if (!processingResult.isEmpty()) {
                heldFluid.shrink(required);
            }
        } else {
            processingResult = singleItem;
        }

        sendData();
    }

    @Override
    public boolean isFluidProcessingComplete() {
        return processingTicks >= PROCESSING_TIME;
    }

    @Override
    public ItemStack getFluidProcessingResult() {
        return processingResult.copy();
    }

    @Override
    public void notifyProcessingStarted(BlockPos beltPos) {
        startBeltProcessing(beltPos);
    }

    @Override
    public void notifyProcessingCompleted(BlockPos beltPos) {
        processingItem = ItemStack.EMPTY;
        processingResult = ItemStack.EMPTY;
        processingTicks = 0;
        activeRelay = null;
    }

    public float getFluidFillRatio() {
        if (this.heldFluid.isEmpty()) return 0.0f;
        return (float) this.heldFluid.getAmount() / FLUID_CAPACITY;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!level.isClientSide) {
            VirtualRelayManager.registerWorkstation(worldPosition, level, getRange());
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (!level.isClientSide) {
            VirtualRelayManager.unregisterWorkstation(worldPosition);
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        if (!level.isClientSide) {
            VirtualRelayManager.unregisterWorkstation(worldPosition);
        }
    }

    @Override
    public boolean canProcessFluidItem(ItemStack stack) {
        if (heldFluid.isEmpty()) {
            return false;
        }

        if (!com.simibubi.create.content.fluids.spout.FillingBySpout.canItemBeFilled(level, stack)) {
            return false;
        }

        int required = com.simibubi.create.content.fluids.spout.FillingBySpout
                .getRequiredAmountForItem(level, stack, heldFluid);

        return required > 0 && required <= heldFluid.getAmount();
    }

    @Override
    public void syncFluid(FluidStack fluid) {
        this.heldFluid = fluid.copy();
        sendData();
        setChanged();
    }

    @Override
    public boolean requestFluidForItem(ItemStack stack, BlockPos sourcePos) {
        if (!heldFluid.isEmpty()) {
            int required = FillingBySpout.getRequiredAmountForItem(level, stack, heldFluid);
            if (required > 0 && required <= heldFluid.getAmount()) {
                return true;
            }
        }

        for (FluidInteractionPoint input : inputs) {
            if (input.isValid() && input.canExtract()) {
                FluidStack simulatedFluid = input.extract(TRANSFER_AMOUNT, true);

                if (!simulatedFluid.isEmpty()) {
                    int required = FillingBySpout.getRequiredAmountForItem(level, stack, simulatedFluid);
                    if (required > 0 && required <= simulatedFluid.getAmount()) {
                        pendingBeltRequest = sourcePos;
                        pendingBeltItem = stack.copy();

                        phase = Phase.MOVE_TO_INPUT;
                        chasedPointIndex = inputs.indexOf(input);
                        chasedPointProgress = 0.0F;
                        lastInputIndex = chasedPointIndex;

                        sendData();
                        setChanged();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void onFluidProcessingComplete() {
    }

    @Override
    public int getProcessingRange() {
        return getRange();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        this.selectionMode = new ScrollOptionBehaviour<>(SelectionMode.class,
                CreateLang.translateDirect("logistics.when_multiple_outputs_available"), this, new SelectionModeValueBox());
        behaviours.add(this.selectionMode);
    }

    @Override
    public void tick() {
        super.tick();

        if (!level.isClientSide && processingItem != ItemStack.EMPTY) {
            processingTicks++;
        }

        // 根据速度选择不同的处理策略
        SpeedMode mode = getSpeedMode();
        switch (mode) {
            case HIGH:
                handleHighSpeedInjection();
                break;
            case LOW:
                handleLowSpeedInjection();
                break;
            case ULTRA_LOW:
                handleUltraLowSpeedInjection();
                break;
        }

        if (processingBelt && beltProcessingTicks > 0) {
            beltProcessingTicks--;

            if (beltProcessingTicks == 15) {
                level.playSound(null, processingBeltPos,
                        com.simibubi.create.AllSoundEvents.SPOUTING.getMainEvent(),
                        net.minecraft.sounds.SoundSource.BLOCKS,
                        0.75F, 0.9F + 0.2F * level.random.nextFloat());
            }
        }

        // 保存进度用于下一tick的比较
        previousProgressForInjection = this.chasedPointProgress;

        this.initInteractionPoints();
        boolean targetReached = this.tickMovementProgress();
        if (this.tooltipWarmup > 0) {
            --this.tooltipWarmup;
        }

        if (this.chasedPointProgress < 1.0F) {
            if (this.phase == Phase.MOVE_TO_INPUT) {
                FluidInteractionPoint point = this.getTargetedInteractionPoint();
                if (point != null) {
                    point.keepAlive();
                }
            } else if (this.phase == Phase.MOVE_TO_RETURN) {
                // 归还移动中也保持目标活跃
                FluidInteractionPoint point = this.getReturnTargetPoint();
                if (point != null) {
                    point.keepAlive();
                }
            }
        } else if (!this.level.isClientSide) {
            if (this.phase == Phase.MOVE_TO_INPUT) {
                this.collectFluid();
            } else if (this.phase == Phase.MOVE_TO_OUTPUT) {
                this.depositFluid();
            } else if (this.phase == Phase.MOVE_TO_RETURN) {
                this.returnFluid();
            } else if (this.phase == Phase.SEARCH_INPUTS) {
                this.searchForFluid();
            } else if (this.phase == Phase.SEARCH_OUTPUTS) {
                // 新增：在 SEARCH_OUTPUTS 阶段也调用搜索
                this.searchForDestinationOrReturn();
            } else if (this.phase == Phase.RETURN_FLUID) {
                this.searchForReturnDestination();
            }

            if (targetReached) {
                this.lazyTick();
            }
        }
    }


    protected void searchForDestinationOrReturn() {
        if (this.heldFluid.isEmpty()) {
            this.phase = Phase.SEARCH_INPUTS;
            this.chasedPointProgress = 1.0F;
            this.chasedPointIndex = -1;
            this.sendData();
            this.setChanged();
            return;
        }

        // 终极保险：只要正在给传送带服务，就坚决不归还！（不管有没有物品）
        // 因为 VirtualRelayManager 会在最后一个物品处理完后调用 onBeltProcessingFinished()
        // 那时候我们再放开归还
        if (this.isServingBelt) {
            return;
        }

        // 下面才是真正的“空闲归还”逻辑
        // 1. 先看有没有非传送带的活干
        for (int i = 0; i < this.outputs.size(); i++) {
            FluidInteractionPoint point = this.outputs.get(i);
            if (point instanceof BeltFluidInteractionPoint) continue;

            if (point.isValid() && point.canInsert(this.heldFluid)) {
                this.selectIndex(false, i);
                return;
            }
        }

        // 2. 没有非传送带任务，看有没有需要换液的非传送带物品
        for (FluidInteractionPoint output : this.outputs) {
            if (output instanceof BeltFluidInteractionPoint) continue;
            if (output instanceof DepotFluidInteractionPoint depotPoint) {
                if (depotPoint.hasItemForFilling()) {
                    ItemStack item = depotPoint.getItemForFilling();
                    if (!item.isEmpty() && !canFluidProcessItem(this.heldFluid, item)) {
                        int[] inputIdx = new int[1];
                        FluidStack needed = findFluidForItem(item, inputIdx);
                        if (!needed.isEmpty()) {
                            FluidInteractionPoint returnTarget = findFluidReturnTarget(this.heldFluid);
                            if (returnTarget != null) {
                                this.pendingFluidForItem = needed;
                                this.pendingInputIndex = inputIdx[0];
                                startReturnFluidPhase();
                                return;
                            }
                        }
                    }
                }
            }
        }

        // 3. 完全空闲 → 归还流体
        FluidInteractionPoint returnTarget = findFluidReturnTarget(this.heldFluid);
        if (returnTarget != null) {
            this.pendingFluidForItem = FluidStack.EMPTY;
            this.pendingInputIndex = -1;
            startReturnFluidPhase();
            return;
        }

        // 4. 实在没地方还 → 丢掉（防止卡死）
        if (!level.isClientSide && !heldFluid.isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(
                    level,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5,
                    net.minecraftforge.fluids.FluidUtil.getFilledBucket(heldFluid)
            );
            this.heldFluid = FluidStack.EMPTY;
        }

        this.phase = Phase.SEARCH_INPUTS;
        this.chasedPointProgress = 1.0F;
        this.chasedPointIndex = -1;
        this.sendData();
        this.setChanged();
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (!this.level.isClientSide) {
            if (this.chasedPointProgress >= 0.5F && this.phase == Phase.SEARCH_OUTPUTS) {
                this.searchForDestination();
            }
        }
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return super.createRenderBoundingBox().inflate(3.0);
    }

    private boolean tickMovementProgress() {
        boolean targetReachedPreviously = this.chasedPointProgress >= 1.0F;

        float speed = Math.abs(this.getSpeed());
        float increment = Math.min(256.0F, speed) / 1024.0F;

        // 根据速度模式调整增量限制
        if (isHighSpeed()) {
            if (speed >= 256.0F && increment > 0.2F) {
                increment = 0.2F;
            }
        } else {
            increment *= 1.2F;
        }

        this.chasedPointProgress += increment;

        if (this.chasedPointProgress > 1.0F) {
            this.chasedPointProgress = 1.0F;
        }

        if (this.level.isClientSide) {
            FluidInteractionPoint targetedInteractionPoint = this.getTargetedInteractionPoint();
            PipetteAngleTarget previousTarget = this.previousTarget;
            PipetteAngleTarget target = targetedInteractionPoint == null ? PipetteAngleTarget.NO_TARGET :
                    this.createAngleTarget(targetedInteractionPoint);

            double currentBaseAngle = AngleHelper.angleLerp(this.chasedPointProgress, this.previousBaseAngle,
                    target == PipetteAngleTarget.NO_TARGET ? this.previousBaseAngle : target.baseAngle);
            this.baseAngle.setValue(currentBaseAngle);

            if (this.chasedPointProgress < 0.5F) {
                target = PipetteAngleTarget.NO_TARGET;
            } else {
                previousTarget = PipetteAngleTarget.NO_TARGET;
            }

            float progress = this.chasedPointProgress == 1.0F ? 1.0F : this.chasedPointProgress % 0.5F * 2.0F;

            double lowerAngle = Mth.lerp(progress, previousTarget.lowerArmAngle, target.lowerArmAngle);
            double upperAngle = Mth.lerp(progress, previousTarget.upperArmAngle, target.upperArmAngle);
            double headAngleValue = AngleHelper.angleLerp(progress, previousTarget.headAngle % 360.0F, target.headAngle % 360.0F);

            this.lowerArmAngle.setValue(lowerAngle);
            this.upperArmAngle.setValue(upperAngle);
            this.headAngle.setValue(headAngleValue);

            return false;
        } else {
            return !targetReachedPreviously && this.chasedPointProgress >= 1.0F;
        }
    }

    protected boolean isOnCeiling() {
        BlockState state = this.getBlockState();
        return this.hasLevel() && state.getOptionalValue(PipetteBlock.CEILING).orElse(false);
    }

    public void setInteractionPointTag(ListTag tag) {
        this.interactionPointTag = tag;
        this.updateInteractionPoints = true;
    }

    public void setUpdateInteractionPoints(boolean update) {
        this.updateInteractionPoints = update;
    }

    public boolean shouldUpdateInteractionPoints() {
        return this.updateInteractionPoints;
    }

    @Nullable
    private FluidInteractionPoint getTargetedInteractionPoint() {
        if (this.chasedPointIndex == -1) {
            return null;
        }

        if (this.phase == Phase.MOVE_TO_INPUT && this.chasedPointIndex < this.inputs.size()) {
            return this.inputs.get(this.chasedPointIndex);
        } else if (this.phase == Phase.MOVE_TO_OUTPUT && this.chasedPointIndex < this.outputs.size()) {
            return this.outputs.get(this.chasedPointIndex);
        } else if (this.phase == Phase.MOVE_TO_RETURN) {
            // 归还阶段使用 returnTargetIndex
            return this.getReturnTargetPoint();
        }

        return null;
    }

    private PipetteAngleTarget createAngleTarget(FluidInteractionPoint point) {
        return new PipetteAngleTarget(this.worldPosition, VecHelper.getCenterOf(point.getPos()),
                net.minecraft.core.Direction.DOWN, this.isOnCeiling());
    }

    private boolean canFluidBeOutputted(FluidStack fluid) {
        if (fluid.isEmpty()) return false;

        for (FluidInteractionPoint output : this.outputs) {
            if (output.isValid()) {
                BlockState outputState = this.level.getBlockState(output.getPos());
                if (isBlazeBurner(outputState)) {
                    if (fluid.getFluid() != net.minecraft.world.level.material.Fluids.LAVA) {
                        continue;
                    }
                }

                if (output.canInsert(fluid)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isBlazeBurner(BlockState state) {
        return com.simibubi.create.AllBlocks.BLAZE_BURNER.has(state) ||
                com.simibubi.create.AllBlocks.LIT_BLAZE_BURNER.has(state);
    }

    protected void searchForFluid() {
        if (this.redstoneLocked) return;

        for (FluidInteractionPoint output : outputs) {
            if (output instanceof BeltFluidInteractionPoint) continue;

            if (output instanceof DepotFluidInteractionPoint depotPoint && depotPoint.hasItemForFilling()) {
                ItemStack item = depotPoint.getItemForFilling();

                // 手里流体正好能填这个物品 → 直接去注液
                if (!heldFluid.isEmpty() && canFluidProcessItem(heldFluid, item)) {
                    this.phase = Phase.SEARCH_OUTPUTS;
                    this.chasedPointProgress = 1.0F;
                    sendData();
                    setChanged();
                    return;
                }

                // 手里流体不对 → 先归还，再取正确的
                int[] idx = new int[1];
                FluidStack needed = findFluidForItem(item, idx);
                if (!needed.isEmpty()) {
                    FluidInteractionPoint returnTarget = findFluidReturnTarget(heldFluid);
                    if (returnTarget != null) {
                        pendingFluidForItem = needed;
                        pendingInputIndex = idx[0];
                        startReturnFluidPhase();
                        return;
                    }
                }
            }
        }

        // Step 2: 手里有流体 → 优先尝试输出到任意能接受的地方（包括普通容器）
        if (!heldFluid.isEmpty()) {
            boolean canOutputSomewhere = false;
            for (FluidInteractionPoint output : outputs) {
                if (output instanceof BeltFluidInteractionPoint) continue; // 传送带另外处理
                if (output.isValid() && output.canInsert(heldFluid)) {
                    canOutputSomewhere = true;
                    break;
                }
            }

            if (canOutputSomewhere) {
                this.phase = Phase.SEARCH_OUTPUTS;
                this.chasedPointProgress = 1.0F;
                sendData();
                setChanged();
                return;
            }

            // 输出不了 → 尝试归还
            FluidInteractionPoint returnTarget = findFluidReturnTarget(heldFluid);
            if (returnTarget != null) {
                pendingFluidForItem = FluidStack.EMPTY;
                pendingInputIndex = -1;
                startReturnFluidPhase();
                return;
            }

            // 完全没地方放 → 只能等
            return;
        }

        // Step 3: 手里没流体 && 目前没有任何注液需求 → 才执行“普通流体转运”
        // 即：从输入点取液 → 只要有一个输出点能接受就行
        boolean foundInput = false;
        int startIndex = selectionMode.get() == SelectionMode.PREFER_FIRST ? 0 : lastInputIndex + 1;
        int size = inputs.size();

        for (int i = 0; i < size; i++) {
            int index = (startIndex + i) % size;
            FluidInteractionPoint point = inputs.get(index);

            if (!point.isValid() || !point.canExtract()) continue;

            FluidStack available = point.extract(TRANSFER_AMOUNT, true);
            if (available.isEmpty()) continue;

            // 关键：只要有一个非传送带的输出点能接受这瓶流体，就开始取液
            if (canFluidBeOutputtedToNonBelt(available)) {
                selectIndex(true, index);
                foundInput = true;
                break;
            }
        }

        if (!foundInput && selectionMode.get() != SelectionMode.PREFER_FIRST) {
            lastInputIndex = -1;
        }
    }
    /**
     * 检查流体是否可以输出到非传送带的目标
     * 传送带的流体输出由 VirtualRelayManager 通过 requestFluidForItem 请求
     */
    private boolean canFluidBeOutputtedToNonBelt(FluidStack fluid) {
        if (fluid.isEmpty()) return false;

        for (FluidInteractionPoint output : this.outputs) {
            // 跳过传送带
            if (output instanceof BeltFluidInteractionPoint) {
                continue;
            }

            if (output.isValid()) {
                BlockState outputState = this.level.getBlockState(output.getPos());
                if (isBlazeBurner(outputState)) {
                    if (fluid.getFluid() != net.minecraft.world.level.material.Fluids.LAVA) {
                        continue;
                    }
                }

                if (output.canInsert(fluid)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 开始归还流体阶段
     */
    private void startReturnFluidPhase() {
        if (this.heldFluid.isEmpty()) {
            this.phase = Phase.SEARCH_INPUTS;
            return;
        }

        FluidInteractionPoint returnTarget = findFluidReturnTarget(this.heldFluid);

        if (returnTarget == null) {
            return;
        }

        this.returnTargetIndex = getReturnTargetIndex(returnTarget);
        this.phase = Phase.RETURN_FLUID;
        this.chasedPointProgress = 0.0F;
        this.chasedPointIndex = -1;

        searchForReturnDestination();
    }

    /**
     * 搜索归还目标并开始移动
     */
    protected void searchForReturnDestination() {
        FluidInteractionPoint returnTarget = getReturnTargetPoint();

        if (returnTarget == null || !returnTarget.isValid() || !returnTarget.canInsert(this.heldFluid)) {
            // 归还目标无效，重置
            this.returnTargetIndex = -1;
            this.pendingFluidForItem = FluidStack.EMPTY;
            this.pendingInputIndex = -1;
            this.phase = Phase.SEARCH_INPUTS;
            this.sendData();
            this.setChanged();
            return;
        }

        this.phase = Phase.MOVE_TO_RETURN;
        this.chasedPointProgress = 0.0F;
        // chasedPointIndex 用于动画，设置为一个标记值
        this.chasedPointIndex = returnTargetIndex;

        this.sendData();
        this.setChanged();
    }

    /**
     * 执行流体归还
     */
    protected void returnFluid() {
        FluidInteractionPoint returnTarget = getReturnTargetPoint();

        if (returnTarget == null || !returnTarget.isValid()) {
            // 归还目标无效
            resetReturnState();
            this.phase = Phase.SEARCH_INPUTS;
            this.sendData();
            this.setChanged();
            return;
        }

        // 尝试归还流体
        FluidStack toReturn = this.heldFluid.copy();
        FluidStack remainder = returnTarget.insert(toReturn, false);
        this.heldFluid = remainder;

        // 播放声音
        if (remainder.getAmount() < toReturn.getAmount()) {
            this.level.playSound(null, this.worldPosition, SoundEvents.BUCKET_EMPTY,
                    SoundSource.BLOCKS, 0.125F, 0.5F + this.level.random.nextFloat() * 0.25F);
        }

        // 检查是否有待处理的取液任务
        if (this.heldFluid.isEmpty() && !this.pendingFluidForItem.isEmpty() && this.pendingInputIndex >= 0) {
            // 流体已归还完毕，去取需要的流体
            this.selectIndex(true, this.pendingInputIndex);
            resetReturnState();
        } else if (this.heldFluid.isEmpty()) {
            // 流体归还完毕，无待处理任务
            resetReturnState();
            this.phase = Phase.SEARCH_INPUTS;
        } else {
            // 还有流体没归还完（目标容器满了），尝试找其他目标
            FluidInteractionPoint newTarget = findFluidReturnTarget(this.heldFluid);
            if (newTarget != null && newTarget != returnTarget) {
                this.returnTargetIndex = getReturnTargetIndex(newTarget);
                this.phase = Phase.RETURN_FLUID;
                searchForReturnDestination();
            } else {
                // 无法继续归还，带着剩余流体搜索任务
                resetReturnState();
                this.phase = Phase.SEARCH_INPUTS;
            }
        }

        this.chasedPointProgress = 0.0F;
        this.chasedPointIndex = -1;
        this.sendData();
        this.setChanged();
    }

    /**
     * 重置归还状态
     */
    private void resetReturnState() {
        this.returnTargetIndex = -1;
        this.pendingFluidForItem = FluidStack.EMPTY;
        this.pendingInputIndex = -1;
    }

    /**
     * 检查流体是否可用于指定物品的加工
     */
    private boolean canFluidProcessItem(FluidStack fluid, ItemStack item) {
        if (fluid.isEmpty() || item.isEmpty()) return false;
        if (!FillingBySpout.canItemBeFilled(level, item)) return false;
        int required = FillingBySpout.getRequiredAmountForItem(level, item, fluid);
        return required > 0 && required <= fluid.getAmount();
    }

    /**
     * 检查输入点是否有可用于指定物品加工的流体
     * 返回流体副本，如果没有则返回 EMPTY
     */
    private FluidStack findFluidForItem(ItemStack item, int[] outInputIndex) {
        if (item.isEmpty() || !FillingBySpout.canItemBeFilled(level, item)) {
            return FluidStack.EMPTY;
        }

        for (int i = 0; i < inputs.size(); i++) {
            FluidInteractionPoint input = inputs.get(i);
            if (input.isValid() && input.canExtract()) {
                FluidStack simulatedFluid = input.extract(TRANSFER_AMOUNT, true);
                if (!simulatedFluid.isEmpty()) {
                    int required = FillingBySpout.getRequiredAmountForItem(level, item, simulatedFluid);
                    if (required > 0 && required <= simulatedFluid.getAmount()) {
                        if (outInputIndex != null && outInputIndex.length > 0) {
                            outInputIndex[0] = i;
                        }
                        return simulatedFluid.copy();
                    }
                }
            }
        }
        return FluidStack.EMPTY;
    }

    /**
     * 查找可以接收当前流体的容器
     * 优先查找纯流体容器，其次是输入源
     */
    @Nullable
    private FluidInteractionPoint findFluidReturnTarget(FluidStack fluid) {
        if (fluid.isEmpty()) return null;

        // 首先检查输出点中的纯流体容器（排除传送带和置物台）
        for (FluidInteractionPoint output : outputs) {
            if (output instanceof BeltFluidInteractionPoint) continue;
            if (output instanceof DepotFluidInteractionPoint) continue;

            if (output.isValid() && output.canInsert(fluid)) {
                return output;
            }
        }

        // 然后检查输入源是否可以放回
        for (FluidInteractionPoint input : inputs) {
            if (input.isValid() && input.canInsert(fluid)) {
                return input;
            }
        }

        return null;
    }

    /**
     * 获取归还目标在所有交互点中的索引
     * 用于动画系统定位
     */
    private int getReturnTargetIndex(FluidInteractionPoint target) {
        // 先在 outputs 中查找
        for (int i = 0; i < outputs.size(); i++) {
            if (outputs.get(i) == target) {
                return i;
            }
        }
        // 再在 inputs 中查找（使用负索引或特殊标记）
        for (int i = 0; i < inputs.size(); i++) {
            if (inputs.get(i) == target) {
                return -(i + 1);  // 使用负数表示是输入点
            }
        }
        return -1;
    }

    /**
     * 根据索引获取归还目标点
     */
    @Nullable
    private FluidInteractionPoint getReturnTargetPoint() {
        if (returnTargetIndex >= 0 && returnTargetIndex < outputs.size()) {
            return outputs.get(returnTargetIndex);
        } else if (returnTargetIndex < 0) {
            int inputIndex = -(returnTargetIndex + 1);
            if (inputIndex >= 0 && inputIndex < inputs.size()) {
                return inputs.get(inputIndex);
            }
        }
        return null;
    }

    /**
     * 检查是否有需要加工的物品（置物台或传送带上）
     */
    private boolean hasItemsNeedingProcessing() {
        for (FluidInteractionPoint output : outputs) {
            if (output instanceof DepotFluidInteractionPoint depotPoint) {
                if (depotPoint.hasItemForFilling()) {
                    return true;
                }
            } else if (output instanceof BeltFluidInteractionPoint) {
                // 传送带上的物品由 VirtualRelayManager 处理，这里简化判断
                // 如果传送带是有效的输出点，认为可能有物品
                if (output.isValid()) {
                    return true;
                }
            }
        }
        return false;
    }

    protected void searchForDestination() {
        FluidStack held = this.heldFluid.copy();
        boolean foundOutput = false;

        for (int i = 0; i < this.outputs.size(); i++) {
            FluidInteractionPoint point = this.outputs.get(i);

            if (com.simibubi.create.AllBlocks.BELT.has(level.getBlockState(point.getPos()))) {
                continue;
            }

            if (point.isValid() && point.canInsert(held)) {
                this.selectIndex(false, i);
                foundOutput = true;
                break;
            }
        }
    }

    public void onBeltProcessingFinished(BlockPos beltPos) {
        this.processingBelt = false;
        this.processingBeltPos = null;
        this.beltProcessingTicks = 0;
        this.isPerformingInjection = false;

        this.isServingBelt = false;  // 新增这行！清零标记，让系统能进入归还逻辑

        sendData();
        setChanged();
    }

    private void selectIndex(boolean input, int index) {
        this.phase = input ? Phase.MOVE_TO_INPUT : Phase.MOVE_TO_OUTPUT;
        this.chasedPointIndex = index;
        this.chasedPointProgress = 0.0F;
        if (input) {
            this.lastInputIndex = index;
        } else {
            this.lastOutputIndex = index;
        }

        this.sendData();
        this.setChanged();
    }

    private LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return heldFluid.copy();
        }

        @Override
        public int getTankCapacity(int tank) {
            return FLUID_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return true;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;

            int canFill = Math.min(resource.getAmount(), FLUID_CAPACITY - heldFluid.getAmount());
            if (canFill <= 0) return 0;

            if (action.execute()) {
                if (heldFluid.isEmpty()) {
                    heldFluid = resource.copy();
                    heldFluid.setAmount(canFill);
                } else if (heldFluid.isFluidEqual(resource)) {
                    heldFluid.grow(canFill);
                } else {
                    return 0;
                }
                setChanged();
                sendData();
            }
            return canFill;
        }

        @Override
        public FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action) {
            if (!resource.isFluidEqual(heldFluid)) return FluidStack.EMPTY;
            return drain(resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (heldFluid.isEmpty()) return FluidStack.EMPTY;

            int drained = Math.min(maxDrain, heldFluid.getAmount());
            FluidStack result = heldFluid.copy();
            result.setAmount(drained);

            if (action.execute()) {
                heldFluid.shrink(drained);
                setChanged();
                sendData();
            }
            return result;
        }
    });

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCapability.invalidate();
    }

    protected void depositFluid() {
        FluidInteractionPoint point = this.getTargetedInteractionPoint();
        if (point == null || !point.isValid()) {
            this.phase = this.heldFluid.isEmpty() ? Phase.SEARCH_INPUTS : Phase.SEARCH_OUTPUTS;
            this.chasedPointProgress = 0.0F;
            this.chasedPointIndex = -1;
            this.sendData();
            this.setChanged();
            return;
        }

        if (point instanceof DepotFluidInteractionPoint depotPoint) {
            DepotBehaviour behaviour = BlockEntityBehaviour.get(level, point.getPos(), DepotBehaviour.TYPE);
            if (behaviour == null) return;

            ItemStack itemOnDepot = behaviour.getHeldItemStack();
            if (itemOnDepot != null && !itemOnDepot.isEmpty()) {
                ItemStack singleItem = itemOnDepot.copy();
                singleItem.setCount(1);

                int requiredAmount = com.simibubi.create.content.fluids.spout.FillingBySpout
                        .getRequiredAmountForItem(this.level, singleItem, this.heldFluid);

                if (requiredAmount > 0 && requiredAmount <= this.heldFluid.getAmount()) {
                    ItemStack toProcess = itemOnDepot.copy();
                    toProcess.setCount(1);

                    FluidStack fluidForFilling = this.heldFluid.copy();
                    fluidForFilling.setAmount(requiredAmount);

                    ItemStack result = com.simibubi.create.content.fluids.spout.FillingBySpout
                            .fillItem(this.level, requiredAmount, toProcess, fluidForFilling);

                    if (!result.isEmpty()) {
                        FluidStack fluidForParticles = this.heldFluid.copy();

                        itemOnDepot.shrink(1);

                        if (itemOnDepot.isEmpty()) {
                            behaviour.setHeldItem(null);
                        } else {
                            com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack updatedStack =
                                    new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(itemOnDepot);
                            updatedStack.beltPosition = 0.5f;
                            updatedStack.prevBeltPosition = 0.5f;
                            behaviour.setHeldItem(updatedStack);
                        }

                        try {
                            java.lang.reflect.Field bufferField = DepotBehaviour.class.getDeclaredField("processingOutputBuffer");
                            bufferField.setAccessible(true);
                            net.minecraftforge.items.ItemStackHandler outputBuffer =
                                    (net.minecraftforge.items.ItemStackHandler) bufferField.get(behaviour);

                            ItemStack remainder = result.copy();
                            for (int slot = 0; slot < outputBuffer.getSlots() && !remainder.isEmpty(); slot++) {
                                remainder = outputBuffer.insertItem(slot, remainder, false);
                            }

                            if (!remainder.isEmpty()) {
                                net.minecraft.world.phys.Vec3 dropPos =
                                        net.createmod.catnip.math.VecHelper.getCenterOf(point.getPos());
                                net.minecraft.world.Containers.dropItemStack(
                                        this.level,
                                        dropPos.x,
                                        dropPos.y + 0.5,
                                        dropPos.z,
                                        remainder
                                );
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack newTIS =
                                    new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(result);
                            newTIS.beltPosition = 0.5f;
                            newTIS.prevBeltPosition = 0.5f;
                            behaviour.setHeldItem(newTIS);
                        }

                        behaviour.blockEntity.notifyUpdate();
                        this.heldFluid.shrink(requiredAmount);

                        this.level.playSound(null, point.getPos(),
                                com.simibubi.create.AllSoundEvents.SPOUTING.getMainEvent(),
                                net.minecraft.sounds.SoundSource.BLOCKS,
                                0.75F, 0.9F + 0.2F * this.level.random.nextFloat());

                        if (!this.level.isClientSide) {
                            sendFillingParticles(point.getPos(), fluidForParticles);
                        }
                    }
                }
            }
        } else {
            FluidStack toInsert = this.heldFluid.copy();
            FluidStack remainder = point.insert(toInsert, false);
            this.heldFluid = remainder;
        }

        // 修改这里：确保进入正确的搜索阶段
// 修改这里：
        if (this.heldFluid.isEmpty()) {
            this.phase = Phase.SEARCH_INPUTS;
        } else {
            // 永远进入 SEARCH_OUTPUTS，让 searchForDestinationOrReturn 决定要不要归还
            this.phase = Phase.SEARCH_OUTPUTS;
        }
        this.chasedPointProgress = 1.0F;
        this.chasedPointProgress = 1.0F;  // 设为1.0确保下一tick能触发搜索
        this.chasedPointIndex = -1;
        this.sendData();
        this.setChanged();
    }

    private void sendFillingParticles(BlockPos targetPos, FluidStack fluid) {
        if (this.level.isClientSide) return;

        Vec3 particlePos = VecHelper.getCenterOf(targetPos).add(0, 0.8125, 0);

        com.simibubi.create.AllPackets.getChannel().send(
                net.minecraftforge.network.PacketDistributor.TRACKING_CHUNK.with(
                        () -> this.level.getChunkAt(targetPos)
                ),
                new com.adonis.fluid.packet.PipetteParticlePacket(particlePos, fluid)
        );
    }

    protected void collectFluid() {
        FluidInteractionPoint point = this.getTargetedInteractionPoint();
        if (point != null && point.isValid()) {
            int maxCanExtract = FLUID_CAPACITY;

            if (!this.heldFluid.isEmpty()) {
                maxCanExtract = FLUID_CAPACITY - this.heldFluid.getAmount();

                if (maxCanExtract <= 0) {
                    // 改为 SEARCH_INPUTS，让系统决定下一步
                    this.phase = Phase.SEARCH_INPUTS;
                    this.chasedPointProgress = 1.0F;
                    this.chasedPointIndex = -1;
                    this.sendData();
                    this.setChanged();
                    return;
                }
            }

            int extractAmount = Math.min(TRANSFER_AMOUNT, maxCanExtract);

            // 为传送带请求取液
            if (pendingBeltRequest != null && !pendingBeltItem.isEmpty()) {
                FluidStack extracted = point.extract(extractAmount, false);
                if (!extracted.isEmpty()) {
                    if (!this.heldFluid.isEmpty() && this.heldFluid.isFluidEqual(extracted)) {
                        this.heldFluid.grow(extracted.getAmount());
                    } else if (this.heldFluid.isEmpty()) {
                        this.heldFluid = extracted;
                    } else {
                        this.phase = Phase.SEARCH_INPUTS;
                        this.chasedPointProgress = 1.0F;
                        this.chasedPointIndex = -1;
                        pendingBeltRequest = null;
                        pendingBeltItem = ItemStack.EMPTY;
                        this.sendData();
                        this.setChanged();
                        return;
                    }

                    // 为传送带取液完成，回到 SEARCH_INPUTS 等待 VirtualRelayManager
                    this.phase = Phase.SEARCH_INPUTS;
                    this.chasedPointProgress = 1.0F;
                    this.chasedPointIndex = -1;

                    // 清除待处理请求
                    pendingBeltRequest = null;
                    pendingBeltItem = ItemStack.EMPTY;

                    this.sendData();
                    this.setChanged();

                    this.level.playSound(null, this.worldPosition, SoundEvents.BUCKET_FILL,
                            SoundSource.BLOCKS, 0.125F, 0.5F + this.level.random.nextFloat() * 0.25F);
                    return;
                }
            }

            // 为非传送带目标取液（如置物台）
            FluidStack extracted = point.extract(extractAmount, false);
            if (!extracted.isEmpty()) {
                if (!this.heldFluid.isEmpty() && this.heldFluid.isFluidEqual(extracted)) {
                    this.heldFluid.grow(extracted.getAmount());
                } else if (this.heldFluid.isEmpty()) {
                    this.heldFluid = extracted;
                }

                // 进入 SEARCH_INPUTS，让 searchForFluid 决定下一步
                this.phase = Phase.SEARCH_INPUTS;
                this.chasedPointProgress = 1.0F;
                this.chasedPointIndex = -1;
                this.sendData();
                this.setChanged();

                this.level.playSound(null, this.worldPosition, SoundEvents.BUCKET_FILL,
                        SoundSource.BLOCKS, 0.125F, 0.5F + this.level.random.nextFloat() * 0.25F);
                return;
            }
        }

        this.phase = Phase.SEARCH_INPUTS;
        this.chasedPointProgress = 1.0F;
        this.chasedPointIndex = -1;
        this.sendData();
        this.setChanged();
    }

    public void redstoneUpdate() {
        if (!this.level.isClientSide) {
            boolean blockPowered = this.level.hasNeighborSignal(this.worldPosition);
            if (blockPowered != this.redstoneLocked) {
                this.redstoneLocked = blockPowered;
                this.sendData();
                if (!this.redstoneLocked) {
                    this.searchForFluid();
                }
            }
        }
    }

    @Override
    public void transform(BlockEntity be, StructureTransform transform) {
        if (this.interactionPointTag != null) {
            for (Tag tag : this.interactionPointTag) {
                FluidInteractionPoint.transformPos((CompoundTag)tag, transform);
            }
            this.notifyUpdate();
        }
    }

    protected boolean isAreaActuallyLoaded(BlockPos center, int range) {
        if (!this.level.isAreaLoaded(center, range)) {
            return false;
        } else {
            if (this.level.isClientSide) {
                int minY = center.getY() - range;
                int maxY = center.getY() + range;
                if (maxY < this.level.getMinBuildHeight() || minY >= this.level.getMaxBuildHeight()) {
                    return false;
                }

                int minX = center.getX() - range;
                int minZ = center.getZ() - range;
                int maxX = center.getX() + range;
                int maxZ = center.getZ() + range;
                int minChunkX = SectionPos.blockToSectionCoord(minX);
                int maxChunkX = SectionPos.blockToSectionCoord(maxX);
                int minChunkZ = SectionPos.blockToSectionCoord(minZ);
                int maxChunkZ = SectionPos.blockToSectionCoord(maxZ);
                ChunkSource chunkSource = this.level.getChunkSource();

                for(int chunkX = minChunkX; chunkX <= maxChunkX; ++chunkX) {
                    for(int chunkZ = minChunkZ; chunkZ <= maxChunkZ; ++chunkZ) {
                        if (!chunkSource.hasChunk(chunkX, chunkZ)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }
    }

    protected void initInteractionPoints() {
        if (this.updateInteractionPoints && this.interactionPointTag != null) {
            // 关键修改：客户端加载时直接跳过区域加载检查！
            boolean shouldCheckArea = !level.isClientSide;

            if (!shouldCheckArea || this.isAreaActuallyLoaded(this.worldPosition, getRange() + 1)) {
                this.inputs.clear();
                this.outputs.clear();

                for (Tag tag : this.interactionPointTag) {
                    FluidInteractionPoint point = FluidInteractionPoint.deserialize((CompoundTag)tag, this.level, this.worldPosition);
                    if (point != null) {
                        if (point.getMode() == FluidInteractionPoint.Mode.DEPOSIT) {
                            this.outputs.add(point);
                        } else if (point.getMode() == FluidInteractionPoint.Mode.TAKE) {
                            this.inputs.add(point);
                        }
                    }
                }

                this.updateInteractionPoints = false;

                if (!level.isClientSide) {
                    VirtualRelayManager.updateWorkstationRelays(worldPosition, level);
                }

                this.sendData();
                this.setChanged();
            }
        }
    }

    public void writeInteractionPoints(CompoundTag compound) {
        if (this.updateInteractionPoints && this.interactionPointTag != null) {
            compound.put("InteractionPoints", this.interactionPointTag);
        } else {
            ListTag pointsNBT = new ListTag();
            this.inputs.stream().map(fip -> fip.serialize(this.worldPosition)).forEach(pointsNBT::add);
            this.outputs.stream().map(fip -> fip.serialize(this.worldPosition)).forEach(pointsNBT::add);
            compound.put("InteractionPoints", pointsNBT);
        }
    }

    @Override
    public void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);

        if (clientPacket) {
            ListTag currentPoints = new ListTag();
            this.inputs.stream().map(fip -> fip.serialize(this.worldPosition)).forEach(currentPoints::add);
            this.outputs.stream().map(fip -> fip.serialize(this.worldPosition)).forEach(currentPoints::add);
            compound.put("InteractionPoints", currentPoints);
            compound.putBoolean("ForceUpdate", true);
        } else {
            this.writeInteractionPoints(compound);
        }

        NBTHelper.writeEnum(compound, "Phase", this.phase);
        compound.putBoolean("Powered", this.redstoneLocked);
        compound.putBoolean("Goggles", this.goggles);
        compound.put("HeldFluid", this.heldFluid.writeToNBT(new CompoundTag()));
        compound.putInt("TargetPointIndex", this.chasedPointIndex);
        compound.putFloat("MovementProgress", this.chasedPointProgress);

        // 新增：归还相关数据
        compound.putInt("ReturnTargetIndex", this.returnTargetIndex);
        if (!this.pendingFluidForItem.isEmpty()) {
            compound.put("PendingFluid", this.pendingFluidForItem.writeToNBT(new CompoundTag()));
        }
        compound.putInt("PendingInputIndex", this.pendingInputIndex);

        if (clientPacket && processingBelt) {
            compound.putBoolean("ProcessingBelt", true);
            compound.putLong("ProcessingBeltPos", processingBeltPos.asLong());
            compound.putInt("BeltProcessingTicks", beltProcessingTicks);

            // 新增：保存 isServingBelt 状态
            compound.putBoolean("IsServingBelt", this.isServingBelt);
        }
    }

    @Override
    public void writeSafe(CompoundTag compound) {
        super.writeSafe(compound);
        this.writeInteractionPoints(compound);
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        int previousIndex = this.chasedPointIndex;
        Phase previousPhase = this.phase;
        ListTag interactionPointTagBefore = this.interactionPointTag;

        super.read(compound, clientPacket);

        this.heldFluid = FluidStack.loadFluidStackFromNBT(compound.getCompound("HeldFluid"));
        this.phase = NBTHelper.readEnum(compound, "Phase", Phase.class);
        this.chasedPointIndex = compound.getInt("TargetPointIndex");
        this.chasedPointProgress = compound.getFloat("MovementProgress");
        this.interactionPointTag = compound.getList("InteractionPoints", 10);
        this.redstoneLocked = compound.getBoolean("Powered");
        boolean hadGoggles = this.goggles;
        this.goggles = compound.getBoolean("Goggles");

        // 新增：读取归还相关数据
        this.returnTargetIndex = compound.getInt("ReturnTargetIndex");
        if (compound.contains("PendingFluid")) {
            this.pendingFluidForItem = FluidStack.loadFluidStackFromNBT(compound.getCompound("PendingFluid"));
        } else {
            this.pendingFluidForItem = FluidStack.EMPTY;
        }
        this.pendingInputIndex = compound.getInt("PendingInputIndex");

        if (clientPacket) {
            processingBelt = compound.getBoolean("ProcessingBelt");
            if (processingBelt) {
                processingBeltPos = BlockPos.of(compound.getLong("ProcessingBeltPos"));
                beltProcessingTicks = compound.getInt("BeltProcessingTicks");
            }

            if (hadGoggles != this.goggles) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> VisualizationHelper.queueUpdate(this));
            }

            // ========== 关键修复：强制客户端重新解析交互点 ==========
            boolean forceUpdate = compound.contains("ForceUpdate") && compound.getBoolean("ForceUpdate");
            boolean hasInteractionPoints = this.interactionPointTag != null && !this.interactionPointTag.isEmpty();
            boolean tagChanged = interactionPointTagBefore == null ||
                    interactionPointTagBefore.size() != this.interactionPointTag.size();

            if (forceUpdate || hasInteractionPoints || tagChanged) {
                this.updateInteractionPoints = true;  // 强制下次tick重新初始化
                this.inputs.clear();                  // 清空旧列表，防止残留空数据
                this.outputs.clear();                 // 清空旧列表
            }
            // ========================================================

            boolean forceUpdateOld = compound.getBoolean("ForceUpdate");
            boolean tagChangedOld = interactionPointTagBefore == null ||
                    interactionPointTagBefore.size() != this.interactionPointTag.size();

            if (forceUpdateOld || tagChangedOld) {
                this.inputs.clear();
                this.outputs.clear();

                if (this.interactionPointTag != null && this.level != null) {
                    for (Tag tag : this.interactionPointTag) {
                        FluidInteractionPoint point = FluidInteractionPoint.deserialize(
                                (CompoundTag) tag, this.level, this.worldPosition);
                        if (point != null) {
                            if (point.getMode() == FluidInteractionPoint.Mode.TAKE) {
                                this.inputs.add(point);
                            } else {
                                this.outputs.add(point);
                            }
                        }
                    }
                }
            }

            if (previousIndex != this.chasedPointIndex || previousPhase != this.phase) {
                FluidInteractionPoint previousPoint = null;
                if (previousPhase == Phase.MOVE_TO_INPUT && previousIndex < this.inputs.size()) {
                    previousPoint = this.inputs.get(previousIndex);
                }
                if (previousPhase == Phase.MOVE_TO_OUTPUT && previousIndex < this.outputs.size()) {
                    previousPoint = this.outputs.get(previousIndex);
                }
                if (previousPhase == Phase.MOVE_TO_RETURN) {
                    previousPoint = this.getReturnTargetPoint();
                }

                this.previousTarget = previousPoint == null ? PipetteAngleTarget.NO_TARGET :
                        this.createAngleTarget(previousPoint);
                if (previousPoint != null) {
                    this.previousBaseAngle = this.previousTarget.baseAngle;
                }

                FluidInteractionPoint targetedPoint = this.getTargetedInteractionPoint();
                if (targetedPoint != null) {
                    targetedPoint.updateCachedState();
                }
            }
            this.isServingBelt = compound.contains("IsServingBelt") && compound.getBoolean("IsServingBelt");
        }
    }

    public static int getRange() {
        return AllConfigs.server().logistics.mechanicalArmRange.get();
    }

    @Override
    public boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (super.addToTooltip(tooltip, isPlayerSneaking)) {
            return true;
        } else if (isPlayerSneaking) {
            return false;
        } else if (this.tooltipWarmup > 0) {
            return false;
        } else if (!this.inputs.isEmpty()) {
            return false;
        } else if (!this.outputs.isEmpty()) {
            return false;
        } else {
            TooltipHelper.addHint(tooltip, "fluid.mechanical_pipette.no_targets");
            return true;
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);

        LazyOptional<IFluidHandler> handler = this.getCapability(ForgeCapabilities.FLUID_HANDLER);
        if (handler.isPresent()) {
            return this.containedFluidTooltip(tooltip, isPlayerSneaking, handler) || added;
        }

        return added;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        for (FluidInteractionPoint input : this.inputs) {
            input.setLevel(level);
        }
        for (FluidInteractionPoint output : this.outputs) {
            output.setLevel(level);
        }
    }

    public void startBeltProcessing(BlockPos beltPos) {
        this.processingBelt = true;
        this.processingBeltPos = beltPos;
        this.isServingBelt = true;
        this.isPerformingInjection = false;

        // 根据当前状态决定动画时长
        if (this.phase == Phase.SEARCH_OUTPUTS ||
                (this.phase == Phase.MOVE_TO_OUTPUT && this.chasedPointProgress < 0.5f)) {
            // 需要完整移动
            this.beltProcessingTicks = 20;
        } else {
            // 已经在路上或接近目标
            this.beltProcessingTicks = 10;
        }

        for (int i = 0; i < outputs.size(); i++) {
            if (outputs.get(i).getPos().equals(beltPos)) {
                this.phase = Phase.MOVE_TO_OUTPUT;
                this.chasedPointIndex = i;
                // 如果不是已经在移动，才重置进度
                if (this.chasedPointProgress >= 1.0F) {
                    this.chasedPointProgress = 0.0F;
                }
                break;
            }
        }

        sendData();
    }

    public void finishBeltProcessing(BlockPos beltPos) {
        this.processingBelt = false;
        this.processingBeltPos = null;
        this.beltProcessingTicks = 0;

        if (heldFluid.isEmpty()) {
            this.phase = Phase.SEARCH_INPUTS;
            this.chasedPointProgress = 0.0F;
            this.chasedPointIndex = -1;
        }

        sendData();
    }

    public void sendBeltProcessingEffects(BlockPos beltPos, FluidStack fluid) {
        if (!level.isClientSide) {
            net.minecraft.world.phys.Vec3 particlePos =
                    net.createmod.catnip.math.VecHelper.getCenterOf(beltPos).add(0, 0.5, 0);

            AllPackets.getChannel().send(
                    PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(beltPos)),
                    new com.adonis.fluid.packet.PipetteParticlePacket(particlePos, fluid)
            );
        }
    }

    public enum Phase {
        SEARCH_INPUTS,      // 搜索输入（取液源）
        MOVE_TO_INPUT,      // 移动到输入点取液
        SEARCH_OUTPUTS,     // 搜索输出（注液目标）
        MOVE_TO_OUTPUT,     // 移动到输出点注液
        RETURN_FLUID,       // 搜索归还目标
        MOVE_TO_RETURN      // 移动到归还位置
    }

    public enum SelectionMode implements INamedIconOptions {
        ROUND_ROBIN(AllIcons.I_ARM_ROUND_ROBIN),
        FORCED_ROUND_ROBIN(AllIcons.I_ARM_FORCED_ROUND_ROBIN),
        PREFER_FIRST(AllIcons.I_ARM_PREFER_FIRST);

        private final String translationKey;
        private final AllIcons icon;

        SelectionMode(AllIcons icon) {
            this.icon = icon;
            this.translationKey = "create.mechanical_arm.selection_mode." + Lang.asId(this.name());
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

    private class SelectionModeValueBox extends CenteredSideValueBoxTransform {
        public SelectionModeValueBox() {
            super((blockState, direction) -> !direction.getAxis().isVertical());
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            int yPos = state.getValue(PipetteBlock.CEILING) ? 13 : 3;
            Vec3 location = VecHelper.voxelSpace(8.0, yPos, 15.5);
            location = VecHelper.rotateCentered(location, AngleHelper.horizontalAngle(this.getSide()), Direction.Axis.Y);
            return location;
        }

        @Override
        public float getScale() {
            return super.getScale();
        }
    }
}