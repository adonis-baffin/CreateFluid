package com.adonis.fluid.block.Pipette;

import com.adonis.fluid.content.pipette.DepotFluidInteractionPoint;
import com.adonis.fluid.content.pipette.FluidInteractionPoint;
import com.simibubi.create.api.contraption.transformable.TransformableBlockEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Direction.Axis;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemStackHandler;

public class PipetteBlockEntity extends KineticBlockEntity implements TransformableBlockEntity {
    public List<FluidInteractionPoint> inputs = new ArrayList<>();
    public List<FluidInteractionPoint> outputs = new ArrayList<>();
    public ListTag interactionPointTag = null;
    float chasedPointProgress;
    int chasedPointIndex;
    public FluidStack heldFluid;
    Phase phase;
    public boolean goggles;
    PipetteAngleTarget previousTarget;
    LerpedFloat lowerArmAngle;
    LerpedFloat upperArmAngle;
    LerpedFloat baseAngle;
    LerpedFloat headAngle;
    LerpedFloat clawAngle;
    float previousBaseAngle;
    boolean updateInteractionPoints;
    int tooltipWarmup;
    protected ScrollOptionBehaviour<SelectionMode> selectionMode;
    protected int lastInputIndex = -1;
    protected int lastOutputIndex = -1;
    protected boolean redstoneLocked;

    // 流体相关设置
    private static final int TRANSFER_AMOUNT = 1000; // 每次传输的流体量(mB)
    private static final int FLUID_CAPACITY = 1000;  // 移液器流体容量(mB)

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

    // ==================== 流体渲染支持方法 ====================

    /**
     * 获取移液器的流体容量
     * @return 移液器能容纳的最大流体量(mB)
     */
    public int getFluidCapacity() {
        return FLUID_CAPACITY;
    }

    /**
     * 检查移液器是否处于注射模式
     * 当移液器正在寻找输出目标或移动到输出目标时，认为是注射模式
     * @return true表示注射模式，false表示抽取模式
     */
    public boolean isInjectMode() {
        return this.phase == Phase.SEARCH_OUTPUTS || this.phase == Phase.MOVE_TO_OUTPUT;
    }

    /**
     * 检查移液器是否正在工作（移动中）
     * @return true表示正在移动，false表示静止或搜索中
     */
    public boolean isWorking() {
        return this.phase == Phase.MOVE_TO_INPUT || this.phase == Phase.MOVE_TO_OUTPUT;
    }

    /**
     * 获取当前的工作进度（0.0-1.0）
     * @return 当前工作进度
     */
    public float getWorkProgress() {
        return this.chasedPointProgress;
    }

    /**
     * 获取移液器当前持有的流体
     * @return 当前持有的流体堆栈
     */
    public FluidStack getHeldFluid() {
        return this.heldFluid.copy();
    }

    /**
     * 检查移液器是否持有流体
     * @return true表示持有流体，false表示空的
     */
    public boolean hasFluid() {
        return !this.heldFluid.isEmpty();
    }

    /**
     * 获取流体填充比例
     * @return 0.0-1.0的填充比例
     */
    public float getFluidFillRatio() {
        if (this.heldFluid.isEmpty()) return 0.0f;
        return (float) this.heldFluid.getAmount() / FLUID_CAPACITY;
    }

    /**
     * 获取当前工作阶段的描述（用于调试）
     * @return 阶段描述字符串
     */
    public String getPhaseDescription() {
        switch (this.phase) {
            case SEARCH_INPUTS: return "Searching for input";
            case MOVE_TO_INPUT: return "Moving to input";
            case SEARCH_OUTPUTS: return "Searching for output";
            case MOVE_TO_OUTPUT: return "Moving to output";
            default: return "Unknown";
        }
    }

    // ==================== 原有方法保持不变 ====================

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

    @Override
    public void destroy() {
        super.destroy();
        // 流体不需要掉落物处理
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

    protected void searchForFluid() {
        if (!this.redstoneLocked) {
            boolean foundInput = false;
            int startIndex = this.selectionMode.get() == SelectionMode.PREFER_FIRST ? 0 : this.lastInputIndex + 1;
            int scanRange = this.selectionMode.get() == SelectionMode.FORCED_ROUND_ROBIN ?
                    this.lastInputIndex + 2 : this.inputs.size();
            if (scanRange > this.inputs.size()) {
                scanRange = this.inputs.size();
            }

            // 检查是否有烈焰人燃烧室作为输出端，如果有则优先寻找岩浆
            boolean hasBlazeBurnerOutput = this.outputs.stream().anyMatch(output -> {
                BlockState state = this.level.getBlockState(output.getPos());
                return isBlazeBurner(state);
            });

            for(int i = startIndex; i < scanRange; ++i) {
                FluidInteractionPoint point = this.inputs.get(i);
                if (point.isValid() && point.canExtract()) {
                    // 预检查：模拟取出流体，看是否能被任何输出端接受
                    FluidStack simulatedFluid = point.extract(TRANSFER_AMOUNT, true);

                    if (!simulatedFluid.isEmpty()) {
                        // 如果有烈焰人燃烧室输出端，优先选择岩浆
                        if (hasBlazeBurnerOutput && simulatedFluid.getFluid() == net.minecraft.world.level.material.Fluids.LAVA) {
                            if (canFluidBeOutputted(simulatedFluid)) {
                                this.selectIndex(true, i);
                                foundInput = true;
                                break;
                            }
                        } else if (!hasBlazeBurnerOutput && canFluidBeOutputted(simulatedFluid)) {
                            // 如果没有烈焰人燃烧室，或者有但这不是岩浆，检查其他输出端
                            this.selectIndex(true, i);
                            foundInput = true;
                            break;
                        } else if (hasBlazeBurnerOutput && simulatedFluid.getFluid() != net.minecraft.world.level.material.Fluids.LAVA) {
                            // 有烈焰人燃烧室但这不是岩浆，检查是否有其他输出端可以接受
                            if (canFluidBeOutputted(simulatedFluid)) {
                                this.selectIndex(true, i);
                                foundInput = true;
                                break;
                            }
                        }
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

    /**
     * 检查指定的流体是否能被任何输出端接受
     * 特别考虑烈焰人燃烧室只接受岩浆的情况
     */
    private boolean canFluidBeOutputted(FluidStack fluid) {
        if (fluid.isEmpty()) return false;

        for (FluidInteractionPoint output : this.outputs) {
            if (output.isValid()) {
                // 特殊检查：如果输出端是烈焰人燃烧室，只有岩浆可以输出
                BlockState outputState = this.level.getBlockState(output.getPos());
                if (isBlazeBurner(outputState)) {
                    if (fluid.getFluid() != net.minecraft.world.level.material.Fluids.LAVA) {
                        continue; // 烈焰人燃烧室只接受岩浆
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
     * 检查方块是否为烈焰人燃烧室
     */
    private boolean isBlazeBurner(BlockState state) {
        return com.simibubi.create.AllBlocks.BLAZE_BURNER.has(state) ||
                com.simibubi.create.AllBlocks.LIT_BLAZE_BURNER.has(state);
    }

    protected void searchForDestination() {
        FluidStack held = this.heldFluid.copy();
        boolean foundOutput = false;

        if (!this.level.isClientSide) {
            System.out.println("Searching for destination with fluid: " + held);
            System.out.println("Number of outputs: " + this.outputs.size());
        }

        // 首先检查是否有需要注液加工的置物台
        for (int i = 0; i < this.outputs.size(); i++) {
            FluidInteractionPoint point = this.outputs.get(i);

            // 调试日志
            if (!this.level.isClientSide) {
                System.out.println("Checking output " + i + " at " + point.getPos() +
                        ", type: " + point.getClass().getSimpleName());
            }

            if (point instanceof DepotFluidInteractionPoint depotPoint) {
                if (depotPoint.isValid() && depotPoint.hasItemForFilling()) {
                    ItemStack itemOnDepot = depotPoint.getHeldItem();
                    if (itemOnDepot != null && !itemOnDepot.isEmpty()) {
                        int required = com.simibubi.create.content.fluids.spout.FillingBySpout
                                .getRequiredAmountForItem(this.level, itemOnDepot, held);

                        // 调试日志
                        if (!this.level.isClientSide) {
                            System.out.println("Depot has item: " + itemOnDepot +
                                    ", required fluid: " + required);
                        }

                        if (required > 0 && required <= held.getAmount()) {
                            this.selectIndex(false, i);
                            foundOutput = true;
                            break;
                        }
                    }
                }
            }
        }

        // 如果没有找到需要注液的置物台，使用原有逻辑搜索其他输出端
        if (!foundOutput) {
            int startIndex = this.selectionMode.get() == SelectionMode.PREFER_FIRST ? 0 : this.lastOutputIndex + 1;
            int scanRange = this.selectionMode.get() == SelectionMode.FORCED_ROUND_ROBIN ?
                    this.lastOutputIndex + 2 : this.outputs.size();
            if (scanRange > this.outputs.size()) {
                scanRange = this.outputs.size();
            }

            for(int i = startIndex; i < scanRange; ++i) {
                FluidInteractionPoint point = this.outputs.get(i);

                // 跳过已经检查过的置物台
                if (point instanceof DepotFluidInteractionPoint) {
                    continue;
                }

                if (point.isValid() && point.canInsert(held)) {
                    this.selectIndex(false, i);
                    foundOutput = true;
                    break;
                }
            }

            if (!foundOutput && this.selectionMode.get() == SelectionMode.ROUND_ROBIN) {
                this.lastOutputIndex = -1;
            }
        }

        if (this.lastOutputIndex == this.outputs.size() - 1) {
            this.lastOutputIndex = -1;
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
                // 检查单个物品的注液需求
                ItemStack singleItem = itemOnDepot.copy();
                singleItem.setCount(1);

                int requiredAmount = com.simibubi.create.content.fluids.spout.FillingBySpout
                        .getRequiredAmountForItem(this.level, singleItem, this.heldFluid);

                if (requiredAmount > 0 && requiredAmount <= this.heldFluid.getAmount()) {
                    if (!this.level.isClientSide) {
                        System.out.println("Processing filling: " + itemOnDepot.getCount() + " " + itemOnDepot.getItem());
                        System.out.println("Required fluid: " + requiredAmount);
                    }

                    // 创建单个物品用于处理
                    ItemStack toProcess = itemOnDepot.copy();
                    toProcess.setCount(1);

                    FluidStack fluidForFilling = this.heldFluid.copy();
                    fluidForFilling.setAmount(requiredAmount);

                    // 执行注液
                    ItemStack result = com.simibubi.create.content.fluids.spout.FillingBySpout
                            .fillItem(this.level, requiredAmount, toProcess, fluidForFilling);

                    if (!result.isEmpty()) {
                        // 减少主槽位的物品数量（只处理了1个）
                        itemOnDepot.shrink(1);

                        // 如果主槽位空了，清除它
                        if (itemOnDepot.isEmpty()) {
                            behaviour.setHeldItem(null);
                        } else {
                            // 更新主槽位的数量
                            com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack updatedStack =
                                    new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(itemOnDepot);
                            updatedStack.beltPosition = 0.5f;
                            updatedStack.prevBeltPosition = 0.5f;
                            behaviour.setHeldItem(updatedStack);
                        }

                        // 将产物添加到处理输出缓冲区
                        // 使用反射或访问器来访问 processingOutputBuffer
                        try {
                            java.lang.reflect.Field bufferField = DepotBehaviour.class.getDeclaredField("processingOutputBuffer");
                            bufferField.setAccessible(true);
                            net.minecraftforge.items.ItemStackHandler outputBuffer =
                                    (net.minecraftforge.items.ItemStackHandler) bufferField.get(behaviour);

                            // 尝试将结果插入输出缓冲区
                            ItemStack remainder = result.copy();
                            for (int slot = 0; slot < outputBuffer.getSlots() && !remainder.isEmpty(); slot++) {
                                remainder = outputBuffer.insertItem(slot, remainder, false);
                            }

                            // 如果输出缓冲区满了，掉落剩余物品
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

                            if (!this.level.isClientSide) {
                                System.out.println("Added result to output buffer: " + result);
                                System.out.println("Remaining in main slot: " + itemOnDepot.getCount());
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                            // 如果反射失败，回退到原来的行为
                            com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack newTIS =
                                    new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(result);
                            newTIS.beltPosition = 0.5f;
                            newTIS.prevBeltPosition = 0.5f;
                            behaviour.setHeldItem(newTIS);
                        }

                        // 通知更新
                        behaviour.blockEntity.notifyUpdate();

                        // 减少持有的流体
                        this.heldFluid.shrink(requiredAmount);

                        // 播放注液音效
                        this.level.playSound(null, point.getPos(),
                                com.simibubi.create.AllSoundEvents.SPOUTING.getMainEvent(),
                                net.minecraft.sounds.SoundSource.BLOCKS,
                                0.75F, 0.9F + 0.2F * this.level.random.nextFloat());

                        // 发送粒子效果
                        if (!this.level.isClientSide) {
                            sendFillingParticles(point.getPos(), this.heldFluid);
                        }
                    }
                }
            }
        } else {
            // 其他交互点的处理
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

        // 置物台高度是 13/16 格，粒子应该在置物台表面上方
        // 13/16 = 0.8125
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
            FluidStack extracted = point.extract(TRANSFER_AMOUNT, false);
            if (!extracted.isEmpty()) {
                // 双重检查：确保取出的流体能被输出端接受
                if (!canFluidBeOutputted(extracted)) {
                    // 如果不能输出，尝试将流体放回
                    FluidStack remainder = point.insert(extracted, false);
                    if (!remainder.isEmpty()) {
                        // 如果放不回去，只能丢弃了（这种情况应该很少见）
                        this.heldFluid = remainder;
                    }
                    // 回到搜索输入阶段
                    this.phase = Phase.SEARCH_INPUTS;
                    this.chasedPointProgress = 0.0F;
                    this.chasedPointIndex = -1;
                    this.sendData();
                    this.setChanged();
                    return;
                }

                this.heldFluid = extracted;
                this.phase = Phase.SEARCH_OUTPUTS;
                this.chasedPointProgress = 0.0F;
                this.chasedPointIndex = -1;
                this.sendData();
                this.setChanged();

                // 检查是否从蜂巢取出蜂蜜，播放特殊音效
                BlockState inputState = this.level.getBlockState(point.getPos());
                if (inputState.getBlock() instanceof net.minecraft.world.level.block.BeehiveBlock) {
                    // 播放收集蜂蜜的音效
                    this.level.playSound(null, this.worldPosition, net.minecraft.sounds.SoundEvents.BOTTLE_FILL,
                            SoundSource.BLOCKS, 0.125F, 1.0F);
                } else {
                    // 默认的流体收集音效
                    this.level.playSound(null, this.worldPosition, SoundEvents.BUCKET_FILL,
                            SoundSource.BLOCKS, 0.125F, 0.5F + this.level.random.nextFloat() * 0.25F);
                }
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
        this.writeInteractionPoints(compound);
        NBTHelper.writeEnum(compound, "Phase", this.phase);
        compound.putBoolean("Powered", this.redstoneLocked);
        compound.putBoolean("Goggles", this.goggles);
        compound.put("HeldFluid", this.heldFluid.writeToNBT(new CompoundTag()));
        compound.putInt("TargetPointIndex", this.chasedPointIndex);
        compound.putFloat("MovementProgress", this.chasedPointProgress);
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
            if (hadGoggles != this.goggles) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> VisualizationHelper.queueUpdate(this));
            }

            boolean ceiling = this.isOnCeiling();
            if (interactionPointTagBefore == null || interactionPointTagBefore.size() != this.interactionPointTag.size()) {
                this.updateInteractionPoints = true;
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
    public void setLevel(Level level) {
        super.setLevel(level);
        for (FluidInteractionPoint input : this.inputs) {
            input.setLevel(level);
        }
        for (FluidInteractionPoint output : this.outputs) {
            output.setLevel(level);
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
            location = VecHelper.rotateCentered(location, AngleHelper.horizontalAngle(this.getSide()), Axis.Y);
            return location;
        }

        @Override
        public float getScale() {
            return super.getScale();
        }
    }
}