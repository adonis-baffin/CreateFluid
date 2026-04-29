package com.adonis.fluid.block.FluidAtomizer;

import com.adonis.fluid.registry.CFFanProcessingTypes;
import com.adonis.fluid.registry.CFFluids;
import com.simibubi.create.AllTags.AllFluidTags;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class AtomizerProcessingRegistry {

	public record Entry(Predicate<Fluid> matcher, FanProcessingType processingType) {
	}

	private static final List<Entry> BUILTIN_ENTRIES = new ArrayList<>();
	private static final List<Entry> API_ENTRIES = new ArrayList<>();
	private static final List<Entry> DATAPACK_ENTRIES = new ArrayList<>();

	public static void register(Predicate<Fluid> matcher, FanProcessingType type) {
		API_ENTRIES.add(new Entry(matcher, type));
	}

	public static FanProcessingType getProcessingType(Fluid fluid) {
		FanProcessingType builtin = findMatch(BUILTIN_ENTRIES, fluid);
		if (builtin != null)
			return builtin;

		FanProcessingType api = findMatch(API_ENTRIES, fluid);
		if (api != null)
			return api;

		return findMatch(DATAPACK_ENTRIES, fluid);
	}

	public static void init() {
		BUILTIN_ENTRIES.clear();
		API_ENTRIES.clear();
		DATAPACK_ENTRIES.clear();
		registerBuiltin(AllFluidTags.FAN_PROCESSING_CATALYSTS_SPLASHING::matches, AllFanProcessingTypes.SPLASHING);
		registerBuiltin(AllFluidTags.FAN_PROCESSING_CATALYSTS_BLASTING::matches, AllFanProcessingTypes.BLASTING);
		registerBuiltin(AllFluidTags.FAN_PROCESSING_CATALYSTS_HAUNTING::matches, AllFanProcessingTypes.HAUNTING);
		registerBuiltin(AllFluidTags.FAN_PROCESSING_CATALYSTS_SMOKING::matches, AllFanProcessingTypes.SMOKING);
		if (!ModList.get().isLoaded("create_dragons_plus")) {
			registerBuiltin(candidate -> candidate.isSame(CFFluids.POWDER_SNOW.get()), CFFanProcessingTypes.FREEZING.get());
			registerBuiltin(candidate -> candidate.isSame(CFFluids.QUICKSAND_SOURCE.get()), CFFanProcessingTypes.SANDBLASTING.get());
		}
		registerBuiltin(candidate -> candidate.isSame(CFFluids.SLIME_FLUID.getSource()), CFFanProcessingTypes.GLUEING.get());
	}

	public static void replaceDatapackEntries(List<Entry> entries) {
		DATAPACK_ENTRIES.clear();
		DATAPACK_ENTRIES.addAll(entries);
	}

	public static List<Entry> getDatapackEntriesView() {
		return Collections.unmodifiableList(DATAPACK_ENTRIES);
	}

	private static void registerBuiltin(Predicate<Fluid> matcher, FanProcessingType type) {
		BUILTIN_ENTRIES.add(new Entry(matcher, type));
	}

	private static FanProcessingType findMatch(List<Entry> entries, Fluid fluid) {
		for (Entry entry : entries) {
			if (entry.matcher.test(fluid))
				return entry.processingType;
		}
		return null;
	}
}
