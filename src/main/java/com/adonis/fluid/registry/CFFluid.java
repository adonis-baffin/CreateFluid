package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.fluid.powdersnow.PowderSnowFluid;
import com.simibubi.create.AllTags;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.FluidEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

public class CFFluid {

    private static final CreateRegistrate REGISTRATE = CreateFluid.REGISTRATE;

    // 细雪流体贴图路径
    public static final ResourceLocation POWDER_SNOW_STILL_RL =
            CreateFluid.asResource("block/powder_snow_fluid_still");
    public static final ResourceLocation POWDER_SNOW_FLOW_RL =
            CreateFluid.asResource("block/powder_snow_fluid_flow");

    // 注册细雪虚拟流体 - 与TEA对齐，无特殊效果，无桶
    public static final FluidEntry<PowderSnowFluid> POWDER_SNOW = REGISTRATE
            .virtualFluid("powder_snow",
                    POWDER_SNOW_STILL_RL,
                    POWDER_SNOW_FLOW_RL,
                    CreateRegistrate::defaultFluidType,
                    PowderSnowFluid::createSource,
                    PowderSnowFluid::createFlowing)
            .lang("Powder Snow")
            .properties(builder -> builder
                    .temperature(250)  // 低温
                    .viscosity(2000)   // 较高粘度
                    .density(600)      // 比水轻
                    .lightLevel(0))    // 不发光
            .tag(AllTags.forgeFluidTag("powder_snow"))
            .register();

    // 注册方法
    public static void register() {
    }

    // 辅助方法
    public static FluidStack getPowderSnowFluidStack(int amount) {
        return new FluidStack(POWDER_SNOW.get(), amount);
    }

    public static boolean isPowderSnowFluid(Fluid fluid) {
        return fluid == POWDER_SNOW.get() || fluid == POWDER_SNOW.get().getFlowing();
    }
}