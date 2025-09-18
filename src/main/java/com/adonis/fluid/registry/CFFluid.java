// java/com/adonis/fluid/registry/CFFluid.java
package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.fluid.powdersnow.PowderSnowFluid;
import com.adonis.fluid.fluid.powdersnow.PowderSnowFluidType;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class CFFluid {
    private static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, CreateFluid.MODID);

    public static final RegistryObject<Fluid> POWDER_SNOW =
            FLUIDS.register("powder_snow", PowderSnowFluid.Source::new);

    public static final RegistryObject<Fluid> POWDER_SNOW_FLOWING =
            FLUIDS.register("powder_snow_flowing", PowderSnowFluid.Flowing::new);

    public static void register() {
        IEventBus modEventBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        FLUIDS.register(modEventBus);
        PowderSnowFluidType.FLUID_TYPES.register(modEventBus);
        CreateFluid.LOGGER.info("Registering CF Fluids");
    }

    /**
     * 检查是否为细雪流体
     */
    public static boolean isPowderSnowFluid(Fluid fluid) {
        return fluid == POWDER_SNOW.get() || fluid == POWDER_SNOW_FLOWING.get();
    }

    /**
     * 创建细雪流体堆
     */
    public static FluidStack getPowderSnowFluidStack(int amount) {
        return new FluidStack(POWDER_SNOW.get(), amount);
    }
}