package com.adonis.fluid.block.GutterOutlet;

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

import javax.annotation.Nullable;
import java.util.List;

/**
 * 智能集水器方块实体
 * 继承集水器，添加流体过滤和红石控制功能
 */
public class SmartGutterOutletBlockEntity extends GutterOutletBlockEntity {

    protected FilteringBehaviour filtering;

    public SmartGutterOutletBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // 先添加过滤行为
        filtering = new FilteringBehaviour(this, new SmartGutterOutletFilterSlot())
                .forFluids();
        behaviours.add(filtering);

        // 再调用父类添加其他行为
        super.addBehaviours(behaviours);
    }

    /**
     * 检查是否可以激活（未被红石信号控制）
     */
    protected boolean canActivate() {
        BlockState blockState = getBlockState();
        return blockState.hasProperty(SmartGutterOutletBlock.POWERED)
                && !blockState.getValue(SmartGutterOutletBlock.POWERED);
    }

    /**
     * 检查流体是否通过过滤器
     */
    protected boolean testFluidFilter(FluidStack fluid) {
        if (filtering == null) return true;
        return filtering.test(fluid);
    }

    /**
     * 检查流体是否通过过滤器（通过 Fluid 类型）
     */
    protected boolean testFluidFilter(Fluid fluid) {
        if (fluid == null || fluid == Fluids.EMPTY) return false;
        return testFluidFilter(new FluidStack(fluid, 1000));
    }

    @Override
    public void tick() {
        if (level == null) return;

        // 客户端：更新液面动画
        if (level.isClientSide) {
            if (getFluidLevel() != null) {
                getFluidLevel().tickChaser();
            }
            forEachBehaviour(BlockEntityBehaviour::tick);
            return;
        }

        // 调用行为的tick（包括drainer）
        forEachBehaviour(BlockEntityBehaviour::tick);

        // 如果被红石信号控制，只处理排出，停止所有收集
        if (!canActivate()) {
            handleDrainToBelow();
            return;
        }

        // 服务端逻辑
        boolean collectedWorldFluid = false;

        // 世界流体收集（受配置和过滤器控制）
        if (CFCommonConfig.canGutterCollectWorldFluid()) {
            collectedWorldFluid = handleWorldFluidCollectionFiltered();
        }

        // 雨雪收集（受配置和过滤器控制）
        if (!collectedWorldFluid && !getDrainer().hasFluidToDrain()) {
            handlePrecipitationCollectionFiltered();
        }

        // 滴水石锥收集（受配置和过滤器控制）
        if (CFCommonConfig.canGutterCollectDripstone()) {
            handleDripstoneCollectionFiltered();
        }

        handleDrainToBelow();
    }

    /**
     * 处理世界流体收集（带过滤）
     */
    private boolean handleWorldFluidCollectionFiltered() {
        if (level == null || level.isClientSide) return false;

        if (!getDrainer().hasFluidToDrain()) return false;

        FluidStack drainableFluid = getDrainer().getDrainableFluid(getDrainer().getRootPos());
        if (drainableFluid.isEmpty()) return false;

        // 过滤器检查
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

    /**
     * 处理雨雪收集（带过滤）
     */
    private void handlePrecipitationCollectionFiltered() {
        if (level == null || level.isClientSide) return;

        if (!level.canSeeSky(worldPosition.above())) return;
        if (!level.isRaining()) return;

        Biome.Precipitation precipitation = level.getBiome(worldPosition).value()
                .getPrecipitationAt(worldPosition);

        if (precipitation == Biome.Precipitation.NONE) return;

        FluidStack currentFluid = getFluid();

        if (precipitation == Biome.Precipitation.RAIN) {
            if (!CFCommonConfig.canGutterCollectRain()) return;

            // 过滤器检查
            if (!testFluidFilter(Fluids.WATER)) return;

            if (!currentFluid.isEmpty() && !currentFluid.getFluid().isSame(Fluids.WATER)) {
                return;
            }
            accumulateAndFill(new FluidStack(Fluids.WATER, 1), true);

        } else if (precipitation == Biome.Precipitation.SNOW) {
            if (!CFCommonConfig.canGutterCollectSnow()) return;

            FluidStack snowFluid = CFFluid.getPowderSnowFluidStack(1);
            // 过滤器检查
            if (!testFluidFilter(snowFluid)) return;

            if (!currentFluid.isEmpty() && !CFFluid.isPowderSnowFluid(currentFluid.getFluid())) {
                return;
            }
            accumulateAndFill(snowFluid, true);
        }
    }

    /**
     * 处理滴水石锥收集（带过滤）
     */
    private void handleDripstoneCollectionFiltered() {
        if (level == null || level.isClientSide) return;

        BlockPos tipPos = findStalactiteTipAboveInternal();
        if (tipPos == null) return;

        Fluid dripFluid = PointedDripstoneBlock.getCauldronFillFluidType((ServerLevel) level, tipPos);
        if (dripFluid == Fluids.EMPTY) return;

        // 过滤器检查
        if (!testFluidFilter(dripFluid)) return;

        FluidStack currentFluid = getFluid();

        if (!currentFluid.isEmpty() && !currentFluid.getFluid().isSame(dripFluid)) {
            return;
        }

        accumulateAndFill(new FluidStack(dripFluid, 1), false);
    }

    /**
     * 查找上方滴水石锥尖端
     */
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