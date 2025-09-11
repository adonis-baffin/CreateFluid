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

    // 添加传送带处理相关字段
    private boolean processingBelt = false;
    private BlockPos processingBeltPos = null;
    private int beltProcessingTicks = 0;

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

        finishBeltProcessing(beltPos);

        if (heldFluid.isEmpty()) {
            phase = Phase.SEARCH_INPUTS;
            chasedPointProgress = 0.0F;
            chasedPointIndex = -1;
            sendData();
        }
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

        if (processingBelt && beltProcessingTicks > 0) {
            beltProcessingTicks--;

            if (beltProcessingTicks == 15) {
                level.playSound(null, processingBeltPos,
                        com.simibubi.create.AllSoundEvents.SPOUTING.getMainEvent(),
                        net.minecraft.sounds.SoundSource.BLOCKS,
                        0.75F, 0.9F + 0.2F * level.random.nextFloat());
            }
        }

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
            }
        } else if (!this.level.isClientSide) {
            if (this.phase == Phase.MOVE_TO_INPUT) {
                this.collectFluid();
            } else if (this.phase == Phase.MOVE_TO_OUTPUT) {
                this.depositFluid();
            } else if (this.phase == Phase.SEARCH_INPUTS) {
                this.searchForFluid();
            }

            if (targetReached) {
                this.lazyTick();
            }
        }
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
        this.chasedPointProgress += Math.min(256.0F, Math.abs(this.getSpeed())) / 1024.0F;
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
        } else if (this.phase == Phase.MOVE_TO_INPUT && this.chasedPointIndex < this.inputs.size()) {
            return this.inputs.get(this.chasedPointIndex);
        } else {
            return this.phase == Phase.MOVE_TO_OUTPUT && this.chasedPointIndex < this.outputs.size() ?
                    this.outputs.get(this.chasedPointIndex) : null;
        }
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
        if (!this.redstoneLocked) {
            // 检查是否有需要流体的非传送带输出端
            boolean hasValidNonBeltOutput = false;

            for (FluidInteractionPoint output : this.outputs) {
                // 跳过传送带 - 传送带不应触发主动取液
                if (com.simibubi.create.AllBlocks.BELT.has(level.getBlockState(output.getPos()))) {
                    continue;
                }

                // 检查置物台
                if (output instanceof DepotFluidInteractionPoint depotPoint) {
                    if (depotPoint.hasItemForFilling()) {
                        hasValidNonBeltOutput = true;
                        break;
                    }
                }
                // 检查其他可以接受流体的输出端（如工作盆）
                else if (output.isValid() && !heldFluid.isEmpty() && output.canInsert(heldFluid)) {
                    hasValidNonBeltOutput = true;
                    break;
                }
            }

            // 只有在有非传送带的有效输出目标时才取液
            if (!hasValidNonBeltOutput) {
                return;
            }

            // 搜索可用的输入源
            boolean foundInput = false;
            int startIndex = this.selectionMode.get() == SelectionMode.PREFER_FIRST ? 0 : this.lastInputIndex + 1;
            int scanRange = this.selectionMode.get() == SelectionMode.FORCED_ROUND_ROBIN ?
                    this.lastInputIndex + 2 : this.inputs.size();
            if (scanRange > this.inputs.size()) {
                scanRange = this.inputs.size();
            }

            for(int i = startIndex; i < scanRange; ++i) {
                FluidInteractionPoint point = this.inputs.get(i);
                if (point.isValid() && point.canExtract()) {
                    FluidStack simulatedFluid = point.extract(TRANSFER_AMOUNT, true);

                    if (!simulatedFluid.isEmpty() && canFluidBeOutputted(simulatedFluid)) {
                        this.selectIndex(true, i);
                        foundInput = true;
                        break;
                    }
                }
            }

            if (!foundInput && this.selectionMode.get() == SelectionMode.ROUND_ROBIN) {
                this.lastInputIndex = -1;
            }

            if (this.lastInputIndex == this.inputs.size() - 1) {
                this.lastInputIndex = -1;
            }
        }
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

    // 在类的字段中添加
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

    // 添加getCapability方法
    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    // 在invalidateCaps中
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
                            sendFillingParticles(point.getPos(), this.heldFluid);
                        }
                    }
                }
            }
        } else {
            FluidStack toInsert = this.heldFluid.copy();
            FluidStack remainder = point.insert(toInsert, false);
            this.heldFluid = remainder;
        }

        this.phase = this.heldFluid.isEmpty() ? Phase.SEARCH_INPUTS : Phase.SEARCH_OUTPUTS;
        this.chasedPointProgress = 0.0F;
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
                    this.phase = Phase.SEARCH_OUTPUTS;
                    this.chasedPointProgress = 0.0F;
                    this.chasedPointIndex = -1;
                    this.sendData();
                    this.setChanged();
                    return;
                }
            }

            int extractAmount = Math.min(TRANSFER_AMOUNT, maxCanExtract);

            if (pendingBeltRequest != null && !pendingBeltItem.isEmpty()) {
                FluidStack extracted = point.extract(extractAmount, false);
                if (!extracted.isEmpty()) {
                    if (!this.heldFluid.isEmpty() && this.heldFluid.isFluidEqual(extracted)) {
                        this.heldFluid.grow(extracted.getAmount());
                    } else if (this.heldFluid.isEmpty()) {
                        this.heldFluid = extracted;
                    } else {
                        this.phase = Phase.SEARCH_INPUTS;
                        this.chasedPointProgress = 0.0F;
                        this.chasedPointIndex = -1;
                        pendingBeltRequest = null;
                        pendingBeltItem = ItemStack.EMPTY;
                        this.sendData();
                        this.setChanged();
                        return;
                    }

                    this.phase = Phase.SEARCH_INPUTS;
                    this.chasedPointProgress = 0.0F;
                    this.chasedPointIndex = -1;
                    pendingBeltRequest = null;
                    pendingBeltItem = ItemStack.EMPTY;
                    this.sendData();
                    this.setChanged();
                    return;
                }
            }

            FluidStack extracted = point.extract(extractAmount, false);
            if (!extracted.isEmpty()) {
                if (!this.heldFluid.isEmpty() && this.heldFluid.isFluidEqual(extracted)) {
                    this.heldFluid.grow(extracted.getAmount());
                } else if (this.heldFluid.isEmpty()) {
                    this.heldFluid = extracted;
                }

                this.phase = Phase.SEARCH_OUTPUTS;
                this.chasedPointProgress = 0.0F;
                this.chasedPointIndex = -1;
                this.sendData();
                this.setChanged();

                this.level.playSound(null, this.worldPosition, SoundEvents.BUCKET_FILL,
                        SoundSource.BLOCKS, 0.125F, 0.5F + this.level.random.nextFloat() * 0.25F);
                return;
            }
        }

        this.phase = Phase.SEARCH_INPUTS;
        this.chasedPointProgress = 0.0F;
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
            if (this.isAreaActuallyLoaded(this.worldPosition, getRange() + 1)) {
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

        if (clientPacket && processingBelt) {
            compound.putBoolean("ProcessingBelt", true);
            compound.putLong("ProcessingBeltPos", processingBeltPos.asLong());
            compound.putInt("BeltProcessingTicks", beltProcessingTicks);
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

        if (clientPacket) {
            processingBelt = compound.getBoolean("ProcessingBelt");
            if (processingBelt) {
                processingBeltPos = BlockPos.of(compound.getLong("ProcessingBeltPos"));
                beltProcessingTicks = compound.getInt("BeltProcessingTicks");
            }

            if (hadGoggles != this.goggles) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> VisualizationHelper.queueUpdate(this));
            }

            boolean forceUpdate = compound.getBoolean("ForceUpdate");
            boolean tagChanged = interactionPointTagBefore == null ||
                    interactionPointTagBefore.size() != this.interactionPointTag.size();

            if (forceUpdate || tagChanged) {
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

                this.updateInteractionPoints = false;
            }

            if (previousIndex != this.chasedPointIndex || previousPhase != this.phase) {
                FluidInteractionPoint previousPoint = null;
                if (previousPhase == Phase.MOVE_TO_INPUT && previousIndex < this.inputs.size()) {
                    previousPoint = this.inputs.get(previousIndex);
                }

                if (previousPhase == Phase.MOVE_TO_OUTPUT && previousIndex < this.outputs.size()) {
                    previousPoint = this.outputs.get(previousIndex);
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
        // 先调用父类的方法显示动力信息
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);

        // 使用机械动力的流体显示方法
        LazyOptional<IFluidHandler> handler = this.getCapability(ForgeCapabilities.FLUID_HANDLER);
        if (handler.isPresent()) {
            // 使用机械动力内置的流体显示辅助方法
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
        this.beltProcessingTicks = 20;

        for (int i = 0; i < outputs.size(); i++) {
            if (outputs.get(i).getPos().equals(beltPos)) {
                this.phase = Phase.MOVE_TO_OUTPUT;
                this.chasedPointIndex = i;
                this.chasedPointProgress = 0.0F;
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
        SEARCH_INPUTS,
        MOVE_TO_INPUT,
        SEARCH_OUTPUTS,
        MOVE_TO_OUTPUT
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