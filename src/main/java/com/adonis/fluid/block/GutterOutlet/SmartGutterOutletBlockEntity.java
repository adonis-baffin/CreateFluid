package com.adonis.fluid.block.GutterOutlet;

import com.adonis.fluid.compat.TwilightForestHelper;
import com.adonis.fluid.config.CFCommonConfig;
import com.adonis.fluid.registry.CFFluid;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.List;


public class SmartGutterOutletBlockEntity extends GutterOutletBlockEntity {

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

        IFluidHandler tank = tankBehaviour.getCapability().orElse(null);
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

    private void handlePrecipitationCollectionFiltered() {
        if (level == null || level.isClientSide) return;
        if (!level.canSeeSky(worldPosition.above())) return;

        FluidStack currentFluid = getFluid();

        if (TwilightForestHelper.isTwilightForestLoaded()) {
            Pair<Biome.Precipitation, Float> tfPrecip = TwilightForestHelper.getCloudPrecipitationAt(level, worldPosition.above());
            if (tfPrecip.getLeft() != Biome.Precipitation.NONE) {

                if (tfPrecip.getLeft() == Biome.Precipitation.RAIN) {
                    if (!CFCommonConfig.canGutterCollectRain()) return;
                    if (!testFluidFilter(Fluids.WATER)) return;
                    if (!currentFluid.isEmpty() && !currentFluid.getFluid().isSame(Fluids.WATER)) return;

                    accumulateAndFill(new FluidStack(Fluids.WATER, 1), true);
                    return;
                }
                if (tfPrecip.getLeft() == Biome.Precipitation.SNOW) {
                    if (!CFCommonConfig.canGutterCollectSnow()) return;
                    FluidStack snowStack = CFFluid.getPowderSnowFluidStack(1);
                    if (!testFluidFilter(snowStack)) return;
                    if (!currentFluid.isEmpty() && !CFFluid.isPowderSnowFluid(currentFluid.getFluid())) return;

                    accumulateAndFill(snowStack, true);
                    return;
                }
            }
        }

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
            FluidStack snowStack = CFFluid.getPowderSnowFluidStack(1);
            if (!testFluidFilter(snowStack)) return;
            if (!currentFluid.isEmpty() && !CFFluid.isPowderSnowFluid(currentFluid.getFluid())) return;

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
        if (level == null) return null;

        BlockPos abovePos = worldPosition.above();
        BlockState aboveState = level.getBlockState(abovePos);

        if (!aboveState.is(Blocks.POINTED_DRIPSTONE)) return null;

        if (aboveState.getValue(PointedDripstoneBlock.TIP_DIRECTION) != Direction.DOWN) return null;

        DripstoneThickness thickness = aboveState.getValue(PointedDripstoneBlock.THICKNESS);
        if (thickness != DripstoneThickness.TIP && thickness != DripstoneThickness.TIP_MERGE) return null;

        return abovePos;
    }
}