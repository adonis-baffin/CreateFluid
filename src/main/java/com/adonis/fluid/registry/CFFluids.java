package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.fluid.powdersnow.PowderSnowFluid;
import com.adonis.fluid.fluid.powdersnow.PowderSnowFluidType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * 流体注册
 */
public class CFFluids {

    // 流体类型注册
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, CreateFluid.MOD_ID);

    // 流体注册
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, CreateFluid.MOD_ID);

    // 细雪流体类型
    public static final Supplier<FluidType> POWDER_SNOW_TYPE = FLUID_TYPES.register("powder_snow",
            PowderSnowFluidType::create);

    // 细雪流体（源）和（流动）需要一起注册以避免循环引用
    public static final DeferredHolder<Fluid, Fluid> POWDER_SNOW = FLUIDS.register("powder_snow",
            () -> new PowderSnowFluid.Source(PowderSnowFluid.PROPERTIES));

    public static final DeferredHolder<Fluid, Fluid> POWDER_SNOW_FLOWING = FLUIDS.register("flowing_powder_snow",
            () -> new PowderSnowFluid.Flowing(PowderSnowFluid.PROPERTIES));

    public static void register() {
        // 实际注册在事件总线上进行
    }
}
