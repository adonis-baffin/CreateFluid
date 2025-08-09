package com.adonis.fluid.block.Pipette;

import com.adonis.fluid.CreateFluid;
import com.simibubi.create.api.contraption.transformable.TransformableBlockEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.*;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint.Mode;
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
import net.minecraft.world.entity.player.Player;
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

public class PipetteBlockEntity extends KineticBlockEntity implements TransformableBlockEntity {
    public List<ArmInteractionPoint> inputs = new ArrayList<>();
    public List<ArmInteractionPoint> outputs = new ArrayList<>();
    public ListTag interactionPointTag = null;
    float chasedPointProgress;
    int chasedPointIndex;
    public ItemStack heldItem;
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

    public PipetteBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        this.heldItem = ItemStack.EMPTY;
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

        // 添加这行确保初始化完成
        CreateFluid.LOGGER.debug("PipetteBlockEntity initialized at {}", pos);
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
        this.initInteractionPoints();
        boolean targetReached = this.tickMovementProgress();
        if (this.tooltipWarmup > 0) {
            --this.tooltipWarmup;
        }

        if (this.chasedPointProgress < 1.0F) {
            if (this.phase == Phase.MOVE_TO_INPUT) {
                ArmInteractionPoint point = this.getTargetedInteractionPoint();
                if (point != null) {
                    point.keepAlive();
                }
            }
        } else if (!this.level.isClientSide) {
            if (this.phase == Phase.MOVE_TO_INPUT) {
                this.collectItem();
            } else if (this.phase == Phase.MOVE_TO_OUTPUT) {
                this.depositItem();
            } else if (this.phase == Phase.SEARCH_INPUTS) {
                this.searchForItem();
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
            ArmInteractionPoint targetedInteractionPoint = this.getTargetedInteractionPoint();
            PipetteAngleTarget previousTarget = this.previousTarget;
            PipetteAngleTarget target = targetedInteractionPoint == null ? PipetteAngleTarget.NO_TARGET :
                    this.createAngleTarget(targetedInteractionPoint);
            this.baseAngle.setValue(AngleHelper.angleLerp(this.chasedPointProgress, this.previousBaseAngle,
                    target == PipetteAngleTarget.NO_TARGET ? this.previousBaseAngle : target.baseAngle));

            if (this.chasedPointProgress < 0.5F) {
                target = PipetteAngleTarget.NO_TARGET;
            } else {
                previousTarget = PipetteAngleTarget.NO_TARGET;
            }

            float progress = this.chasedPointProgress == 1.0F ? 1.0F : this.chasedPointProgress % 0.5F * 2.0F;
            this.lowerArmAngle.setValue(Mth.lerp(progress, previousTarget.lowerArmAngle, target.lowerArmAngle));
            this.upperArmAngle.setValue(Mth.lerp(progress, previousTarget.upperArmAngle, target.upperArmAngle));
            this.headAngle.setValue(AngleHelper.angleLerp(progress, previousTarget.headAngle % 360.0F, target.headAngle % 360.0F));
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
        if (!this.heldItem.isEmpty()) {
            Block.popResource(this.level, this.worldPosition, this.heldItem);
        }
    }

    // 在PipetteBlockEntity类中添加这些public方法
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
    private ArmInteractionPoint getTargetedInteractionPoint() {
        if (this.chasedPointIndex == -1) {
            return null;
        } else if (this.phase == Phase.MOVE_TO_INPUT && this.chasedPointIndex < this.inputs.size()) {
            return this.inputs.get(this.chasedPointIndex);
        } else {
            return this.phase == Phase.MOVE_TO_OUTPUT && this.chasedPointIndex < this.outputs.size() ?
                    this.outputs.get(this.chasedPointIndex) : null;
        }
    }

    // 创建AngleTarget的辅助方法
    private PipetteAngleTarget createAngleTarget(ArmInteractionPoint point) {
        return new PipetteAngleTarget(this.worldPosition, VecHelper.getCenterOf(point.getPos()),
                net.minecraft.core.Direction.DOWN, this.isOnCeiling());
    }

    protected void searchForItem() {
        if (!this.redstoneLocked) {
            boolean foundInput = false;
            int startIndex = this.selectionMode.get() == SelectionMode.PREFER_FIRST ? 0 : this.lastInputIndex + 1;
            int scanRange = this.selectionMode.get() == SelectionMode.FORCED_ROUND_ROBIN ?
                    this.lastInputIndex + 2 : this.inputs.size();
            if (scanRange > this.inputs.size()) {
                scanRange = this.inputs.size();
            }

            for(int i = startIndex; i < scanRange; ++i) {
                ArmInteractionPoint armInteractionPoint = this.inputs.get(i);
                if (armInteractionPoint.isValid()) {
                    for(int j = 0; j < armInteractionPoint.getSlotCount(); ++j) {
                        if (this.getDistributableAmount(armInteractionPoint, j) != 0) {
                            this.selectIndex(true, i);
                            foundInput = true;
                            break;
                        }
                    }
                    if (foundInput) break;
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
        ItemStack held = this.heldItem.copy();
        boolean foundOutput = false;
        int startIndex = this.selectionMode.get() == SelectionMode.PREFER_FIRST ? 0 : this.lastOutputIndex + 1;
        int scanRange = this.selectionMode.get() == SelectionMode.FORCED_ROUND_ROBIN ?
                this.lastOutputIndex + 2 : this.outputs.size();
        if (scanRange > this.outputs.size()) {
            scanRange = this.outputs.size();
        }

        for(int i = startIndex; i < scanRange; ++i) {
            ArmInteractionPoint armInteractionPoint = this.outputs.get(i);
            if (armInteractionPoint.isValid()) {
                ItemStack remainder = armInteractionPoint.insert(held, true);
                if (!remainder.equals(this.heldItem, false)) {
                    this.selectIndex(false, i);
                    foundOutput = true;
                    break;
                }
            }
        }

        if (!foundOutput && this.selectionMode.get() == SelectionMode.ROUND_ROBIN) {
            this.lastOutputIndex = -1;
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

    protected int getDistributableAmount(ArmInteractionPoint armInteractionPoint, int i) {
        ItemStack stack = armInteractionPoint.extract(i, true);
        ItemStack remainder = this.simulateInsertion(stack);
        return ItemStack.isSameItem(stack, remainder) ? stack.getCount() - remainder.getCount() : stack.getCount();
    }

    private ItemStack simulateInsertion(ItemStack stack) {
        for (ArmInteractionPoint armInteractionPoint : this.outputs) {
            if (armInteractionPoint.isValid()) {
                stack = armInteractionPoint.insert(stack, true);
            }
            if (stack.isEmpty()) {
                break;
            }
        }
        return stack;
    }

    protected void depositItem() {
        ArmInteractionPoint armInteractionPoint = this.getTargetedInteractionPoint();
        if (armInteractionPoint != null && armInteractionPoint.isValid()) {
            ItemStack toInsert = this.heldItem.copy();
            ItemStack remainder = armInteractionPoint.insert(toInsert, false);
            this.heldItem = remainder;
        }

        this.phase = this.heldItem.isEmpty() ? Phase.SEARCH_INPUTS : Phase.SEARCH_OUTPUTS;
        this.chasedPointProgress = 0.0F;
        this.chasedPointIndex = -1;
        this.sendData();
        this.setChanged();
    }

    protected void collectItem() {
        ArmInteractionPoint armInteractionPoint = this.getTargetedInteractionPoint();
        if (armInteractionPoint != null && armInteractionPoint.isValid()) {
            for(int i = 0; i < armInteractionPoint.getSlotCount(); ++i) {
                int amountExtracted = this.getDistributableAmount(armInteractionPoint, i);
                if (amountExtracted != 0) {
                    ItemStack prevHeld = this.heldItem;
                    this.heldItem = armInteractionPoint.extract(i, amountExtracted, false);
                    this.phase = Phase.SEARCH_OUTPUTS;
                    this.chasedPointProgress = 0.0F;
                    this.chasedPointIndex = -1;
                    this.sendData();
                    this.setChanged();
                    if (!ItemStack.isSameItem(this.heldItem, prevHeld)) {
                        this.level.playSound(null, this.worldPosition, SoundEvents.ITEM_PICKUP,
                                SoundSource.BLOCKS, 0.125F, 0.5F + CreateFluid.RANDOM.nextFloat() * 0.25F);
                    }
                    return;
                }
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
                    this.searchForItem();
                }
            }
        }
    }

    @Override
    public void transform(BlockEntity be, StructureTransform transform) {
        if (this.interactionPointTag != null) {
            for (Tag tag : this.interactionPointTag) {
                ArmInteractionPoint.transformPos((CompoundTag)tag, transform);
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
                    ArmInteractionPoint point = ArmInteractionPoint.deserialize((CompoundTag)tag, this.level, this.worldPosition);
                    if (point != null) {
                        if (point.getMode() == Mode.DEPOSIT) {
                            this.outputs.add(point);
                        } else if (point.getMode() == Mode.TAKE) {
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
            this.inputs.stream().map(aip -> aip.serialize(this.worldPosition)).forEach(pointsNBT::add);
            this.outputs.stream().map(aip -> aip.serialize(this.worldPosition)).forEach(pointsNBT::add);
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
        compound.put("HeldItem", this.heldItem.serializeNBT());
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
        this.heldItem = ItemStack.of(compound.getCompound("HeldItem"));
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
                ArmInteractionPoint previousPoint = null;
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

                ArmInteractionPoint targetedPoint = this.getTargetedInteractionPoint();
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
            TooltipHelper.addHint(tooltip, "hint.mechanical_arm_no_targets");
            return true;
        }
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        for (ArmInteractionPoint input : this.inputs) {
            input.setLevel(level);
        }
        for (ArmInteractionPoint output : this.outputs) {
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