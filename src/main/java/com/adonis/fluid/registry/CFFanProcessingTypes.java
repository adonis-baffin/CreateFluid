package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.content.kinetics.fan.freezing.FreezingFanProcessingType;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CFFanProcessingTypes {

	private static final DeferredRegister<FanProcessingType> TYPES =
		DeferredRegister.create(CreateRegistries.FAN_PROCESSING_TYPE, CreateFluid.MOD_ID);

	public static final DeferredHolder<FanProcessingType, FreezingFanProcessingType> FREEZING =
		TYPES.register("freezing", FreezingFanProcessingType::new);

	public static void register(IEventBus modBus) {
		TYPES.register(modBus);
	}
}
