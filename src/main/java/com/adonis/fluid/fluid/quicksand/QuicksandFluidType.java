package com.adonis.fluid.fluid.quicksand;

import com.adonis.fluid.CreateFluid;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.function.Consumer;

/**
 * 流沙流体类型 - 完全参照细雪流体类型设计
 */
public class QuicksandFluidType {

    public static final FluidType.Properties PROPERTIES = FluidType.Properties.create()
            .density(1600)
            .temperature(300)
            .viscosity(3500)
            .canSwim(false)
            .canDrown(true);

    public static FluidType create() {
        return new FluidType(PROPERTIES) {
            @Override
            public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                consumer.accept(new IClientFluidTypeExtensions() {
                    @Override
                    public ResourceLocation getStillTexture() {
                        return CreateFluid.asResource("fluid/quicksand_fluid_still");
                    }

                    @Override
                    public ResourceLocation getFlowingTexture() {
                        return CreateFluid.asResource("fluid/quicksand_fluid_flow");
                    }
                });
            }
        };
    }
}
