package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.fluid.powdersnow.PowderSnowFluid;
import com.adonis.fluid.fluid.powdersnow.PowderSnowFluidType;
import com.adonis.fluid.fluid.quicksand.QuicksandFluid;
import com.adonis.fluid.fluid.quicksand.QuicksandFluidType;
import com.tterrag.registrate.util.entry.FluidEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

import static com.adonis.fluid.CreateFluid.REGISTRATE;

/**
 * 流体注册
 */
public class CFFluids {

    public static final TagKey<Fluid> FAN_PROCESSING_CATALYSTS_HAUNTING = TagKey.create(Registries.FLUID,
            ResourceLocation.fromNamespaceAndPath("create", "fan_processing_catalysts/haunting"));
    public static final TagKey<Fluid> FAN_PROCESSING_CATALYSTS_SMOKING = TagKey.create(Registries.FLUID,
            ResourceLocation.fromNamespaceAndPath("create", "fan_processing_catalysts/smoking"));

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

    // 流沙流体类型
    public static final Supplier<FluidType> QUICKSAND_FLUID_TYPE = FLUID_TYPES.register("quicksand_fluid", QuicksandFluidType::create);

    // 流沙流体（源）和（流动）需要一起注册以避免循环引用
    public static final DeferredHolder<Fluid, Fluid> QUICKSAND_SOURCE = FLUIDS.register("quicksand_fluid",
            () -> new QuicksandFluid.Source(QuicksandFluid.PROPERTIES));

    public static final DeferredHolder<Fluid, Fluid> QUICKSAND_FLOWING = FLUIDS.register("flowing_quicksand_fluid",
            () -> new QuicksandFluid.Flowing(QuicksandFluid.PROPERTIES));

    // 缠魂液
    public static final FluidEntry<BaseFlowingFluid.Flowing> HAUNTING_FLUID = REGISTRATE
            .fluid("haunting_fluid",
                    CreateFluid.asResource("fluid/haunting_fluid_still"),
                    CreateFluid.asResource("fluid/haunting_fluid_flow"))
            .properties(properties -> properties
                    .density(1800)
                    .viscosity(2500))
            .fluidProperties(properties -> properties
                    .levelDecreasePerBlock(2)
                    .tickRate(18))
            .source(BaseFlowingFluid.Source::new)
            .tag(FAN_PROCESSING_CATALYSTS_HAUNTING)
            .block()
            .build()
            .bucket()
            .build()
            .register();

    // 烟熏液
    public static final FluidEntry<BaseFlowingFluid.Flowing> SMOKING_FLUID = REGISTRATE
            .fluid("smoking_fluid",
                    CreateFluid.asResource("fluid/smoking_fluid_still"),
                    CreateFluid.asResource("fluid/smoking_fluid_flow"))
            .properties(properties -> properties
                    .density(1650)
                    .viscosity(2300))
            .fluidProperties(properties -> properties
                    .levelDecreasePerBlock(2)
                    .tickRate(18))
            .source(BaseFlowingFluid.Source::new)
            .tag(FAN_PROCESSING_CATALYSTS_SMOKING)
            .block()
            .build()
            .bucket()
            .build()
            .register();

    // 史莱姆黏液
    public static final FluidEntry<BaseFlowingFluid.Flowing> SLIME_FLUID = REGISTRATE
            .fluid("slime_fluid",
                    CreateFluid.asResource("fluid/slime_fluid_still"),
                    CreateFluid.asResource("fluid/slime_fluid_flow"))
            .properties(properties -> properties
                    .density(2200)
                    .viscosity(7200))
            .fluidProperties(properties -> properties
                    .levelDecreasePerBlock(2)
                    .tickRate(28))
            .source(BaseFlowingFluid.Source::new)
            .block()
            .build()
            .bucket()
            .build()
            .register();

    public static void register() {
        // 实际注册在事件总线上进行
    }
}
