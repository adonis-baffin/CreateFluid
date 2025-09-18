// java/com/adonis/fluid/fluid/powdersnow/PowderSnowFluidType.java
package com.adonis.fluid.fluid.powdersnow;

import com.adonis.fluid.CreateFluid;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.util.function.Consumer;

public class PowderSnowFluidType extends FluidType {

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, CreateFluid.MODID);

    public static final RegistryObject<FluidType> POWDER_SNOW_TYPE =
            FLUID_TYPES.register("powder_snow", () -> new PowderSnowFluidType());

    public static FluidType INSTANCE;

    public PowderSnowFluidType() {
        super(Properties.create()
                .density(500)
                .temperature(250)
                .viscosity(2000)
                .canSwim(false)
                .canDrown(true));
        INSTANCE = this;
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return new ResourceLocation("minecraft", "block/powder_snow");
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return new ResourceLocation("minecraft", "block/powder_snow");
            }

            @Override
            public int getTintColor() {
                return 0xFFFFFFFF;
            }
        });
    }
}