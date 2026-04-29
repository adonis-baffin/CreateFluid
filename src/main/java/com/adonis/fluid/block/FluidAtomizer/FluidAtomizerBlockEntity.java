package com.adonis.fluid.block.FluidAtomizer;

import com.simibubi.create.AllTags.AllFluidTags;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

public class FluidAtomizerBlockEntity extends KineticBlockEntity implements IAirCurrentSource {

    public static final int CAPACITY = 1000;
    private static final int MINIMUM_FLUID_AMOUNT = 250;

    public SmartFluidTankBehaviour tankBehaviour;
    public FluidAtomizerAirCurrent airCurrent;
    protected int airCurrentUpdateCooldown;
    protected int entitySearchCooldown;
    protected boolean updateAirFlow;

    public FluidAtomizerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        airCurrent = new FluidAtomizerAirCurrent(this);
        updateAirFlow = true;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        tankBehaviour = new SmartFluidTankBehaviour(SmartFluidTankBehaviour.INPUT, this, 1, CAPACITY, true)
                .whenFluidUpdates(this::onFluidChanged);
        behaviours.add(tankBehaviour);
        registerAwardables(behaviours, AllAdvancements.FAN_PROCESSING);
    }

    private void onFluidChanged() {
        setChanged();
        updateAirFlow = true;
        if (level != null && !level.isClientSide) {
            sendData();
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        if (clientPacket)
            airCurrent.rebuild();
    }

    @Override
    public void tick() {
        super.tick();

        if (!level.isClientSide) {
            if (airCurrentUpdateCooldown-- <= 0) {
                airCurrentUpdateCooldown = 20;
                updateAirFlow = true;
            }
        }

        if (updateAirFlow) {
            updateAirFlow = false;
            airCurrent.rebuild();
            sendData();
        }

        if (getSpeed() == 0)
            return;

        if (entitySearchCooldown-- <= 0) {
            entitySearchCooldown = 5;
            airCurrent.findEntities();
        }

        airCurrent.tick();
    }

    public IFluidHandler getTankInventory() {
        return tankBehaviour == null ? null : tankBehaviour.getCapability();
    }

    public FluidStack getFluid() {
        IFluidHandler handler = getTankInventory();
        if (handler == null || handler.getTanks() == 0) {
            return FluidStack.EMPTY;
        }
        return handler.getFluidInTank(0);
    }

    public FanProcessingType getInternalProcessingType() {
        FluidStack fluid = getFluid();
        if (fluid.isEmpty() || fluid.getAmount() < MINIMUM_FLUID_AMOUNT) {
            return null;
        }

        Fluid containedFluid = fluid.getFluid();

        // 查询通用注册表（支持其他模组扩展）
        FanProcessingType registered = AtomizerProcessingRegistry.getProcessingType(containedFluid);
        if (registered != null) {
            return registered;
        }

        // Fallback 硬编码（水和岩浆）
        if (containedFluid.isSame(Fluids.WATER)) {
            return AllFanProcessingTypes.SPLASHING;
        }
        if (containedFluid.isSame(Fluids.LAVA)) {
            return AllFanProcessingTypes.BLASTING;
        }
        return null;
    }

    public boolean isPotionFluid() {
        FluidStack fluid = getFluid();
        if (fluid.isEmpty()) return false;
        return fluid.getComponents().has(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
    }

    @Nullable
    public java.util.List<net.minecraft.world.effect.MobEffectInstance> getPotionEffects() {
        FluidStack fluid = getFluid();
        if (fluid.isEmpty()) return null;
        var contents = fluid.getComponents().get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        if (contents instanceof net.minecraft.world.item.alchemy.PotionContents potion) {
            java.util.List<net.minecraft.world.effect.MobEffectInstance> list = new java.util.ArrayList<>();
            potion.getAllEffects().forEach(list::add);
            return list;
        }
        return null;
    }

    public int getPotionColor() {
        FluidStack fluid = getFluid();
        if (fluid.isEmpty()) return 0xFF00FF;
        var contents = fluid.getComponents().get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        if (contents instanceof net.minecraft.world.item.alchemy.PotionContents potion) {
            return potion.getColor();
        }
        return 0xFF00FF;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        IFluidHandler handler = getTankInventory();
        if (handler != null) {
            return containedFluidTooltip(tooltip, isPlayerSneaking, handler) || added;
        }

        CreateLang.translate("gui.goggles.fluid_container")
                .forGoggles(tooltip);
        CreateLang.translate("gui.goggles.fluid_container.capacity")
                .add(CreateLang.number(CAPACITY)
                        .add(CreateLang.translate("generic.unit.millibuckets"))
                        .style(ChatFormatting.DARK_GREEN))
                .style(ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        return true;
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                com.adonis.fluid.registry.CFBlockEntities.FLUID_ATOMIZER.get(),
                (be, side) -> be.getTankInventory()
        );
    }

    // IAirCurrentSource implementation

    @Override
    public AirCurrent getAirCurrent() {
        return airCurrent;
    }

    @Override
    public Level getAirCurrentWorld() {
        return level;
    }

    @Override
    public BlockPos getAirCurrentPos() {
        return worldPosition;
    }

    @Override
    public Direction getAirflowOriginSide() {
        return getBlockState().getValue(com.adonis.fluid.block.FluidAtomizer.FluidAtomizerBlock.FACING);
    }

    @Override
    public Direction getAirFlowDirection() {
        if (getSpeed() == 0)
            return null;
        return getBlockState().getValue(com.adonis.fluid.block.FluidAtomizer.FluidAtomizerBlock.FACING);
    }

    @Override
    public boolean isSourceRemoved() {
        return isRemoved();
    }
}
