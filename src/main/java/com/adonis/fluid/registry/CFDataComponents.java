package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.datacomponent.FluidManifestContent;
import com.adonis.fluid.datacomponent.FluidPackageContent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class CFDataComponents {
	public static final DeferredRegister<DataComponentType<?>> REGISTER =
		DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CreateFluid.MOD_ID);

	public static final Supplier<DataComponentType<FluidPackageContent>> FLUID_PACKAGE_CONTENTS = REGISTER.register(
		"fluid_package_contents",
		() -> DataComponentType.<FluidPackageContent>builder()
			.persistent(FluidPackageContent.CODEC)
			.networkSynchronized(FluidPackageContent.STREAM_CODEC)
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
