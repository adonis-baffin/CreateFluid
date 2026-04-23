package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.datacomponent.FluidManifestContent;
import com.adonis.fluid.datacomponent.CopperCanContent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class CFDataComponents {
	public static final DeferredRegister<DataComponentType<?>> REGISTER =
		DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CreateFluid.MOD_ID);

	public static final Supplier<DataComponentType<CopperCanContent>> COPPER_CAN_CONTENTS = REGISTER.register(
		"copper_can_contents",
		() -> DataComponentType.<CopperCanContent>builder()
			.persistent(CopperCanContent.CODEC)
			.networkSynchronized(CopperCanContent.STREAM_CODEC)
			.build()
	);

	public static final Supplier<DataComponentType<FluidManifestContent>> FLUID_MANIFEST = REGISTER.register(
		"fluid_manifest",
		() -> DataComponentType.<FluidManifestContent>builder()
			.persistent(FluidManifestContent.CODEC)
			.networkSynchronized(FluidManifestContent.STREAM_CODEC)
			.build()
	);

	public static void register() {}
}
