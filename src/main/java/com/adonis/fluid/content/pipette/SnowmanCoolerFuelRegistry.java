package com.adonis.fluid.content.pipette;

import com.adonis.fluid.CreateFluid;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

@EventBusSubscriber(modid = CreateFluid.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class SnowmanCoolerFuelRegistry extends SimpleJsonResourceReloadListener {
	private static final Gson GSON = new GsonBuilder().create();
	private static final String DIRECTORY = "snowman_cooler_fuel";
	private static final SnowmanCoolerFuelRegistry INSTANCE = new SnowmanCoolerFuelRegistry();
	private static volatile List<Entry> entries = List.of();

	private SnowmanCoolerFuelRegistry() {
		super(GSON, DIRECTORY);
	}

	@SubscribeEvent
	public static void onAddReloadListeners(AddReloadListenerEvent event) {
		event.addListener(INSTANCE);
	}

	public static Optional<Entry> find(FluidStack stack) {
		if (stack.isEmpty())
			return Optional.empty();
		return entries.stream()
			.filter(entry -> entry.matches(stack))
			.findFirst();
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> jsonMap, ResourceManager resourceManager, ProfilerFiller profiler) {
		List<Entry> parsedEntries = new ArrayList<>();

		for (Map.Entry<ResourceLocation, JsonElement> resourceEntry : jsonMap.entrySet()) {
			ResourceLocation fileId = resourceEntry.getKey();
			JsonElement jsonElement = resourceEntry.getValue();
			if (!jsonElement.isJsonObject()) {
				CreateFluid.LOGGER.warn("Skipping snowman cooler fuel file {} because it is not a JSON object.", fileId);
				continue;
			}

			Entry parsed = parseEntry(fileId, jsonElement.getAsJsonObject());
			if (parsed != null)
				parsedEntries.add(parsed);
		}

		entries = List.copyOf(parsedEntries);
		CreateFluid.LOGGER.info("Loaded {} snowman cooler fuel entries.", entries.size());
	}

	private static Entry parseEntry(ResourceLocation fileId, JsonObject json) {
		if (json.has("required_mod")) {
			String requiredMod = GsonHelper.getAsString(json, "required_mod");
			if (!ModList.get().isLoaded(requiredMod))
				return null;
		}

		boolean hasFluid = json.has("fluid");
		boolean hasTag = json.has("tag");
		if (hasFluid == hasTag) {
			CreateFluid.LOGGER.warn("Skipping snowman cooler fuel entry in {} because exactly one of 'fluid' or 'tag' is required.", fileId);
			return null;
		}

		boolean freezing = GsonHelper.getAsBoolean(json, "freezing", false);
		int burnTime = GsonHelper.getAsInt(json, "burnTime", freezing ? 3200 : 1600);
		int amountConsumedPerTick = GsonHelper.getAsInt(json, "amountConsumedPerTick", 1000);
		if (burnTime <= 0) {
			CreateFluid.LOGGER.warn("Skipping snowman cooler fuel entry in {} because burnTime must be greater than 0.", fileId);
			return null;
		}
		if (amountConsumedPerTick <= 0) {
			CreateFluid.LOGGER.warn("Skipping snowman cooler fuel entry in {} because amountConsumedPerTick must be greater than 0.", fileId);
			return null;
		}

		if (hasFluid) {
			ResourceLocation fluidId = ResourceLocation.tryParse(GsonHelper.getAsString(json, "fluid"));
			if (fluidId == null) {
				CreateFluid.LOGGER.warn("Skipping snowman cooler fuel entry in {} because fluid is not a valid resource location.", fileId);
				return null;
			}

			Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
			if (fluid == null || fluid == Fluids.EMPTY) {
				CreateFluid.LOGGER.warn("Skipping snowman cooler fuel entry in {} because fluid {} is not registered.", fileId, fluidId);
				return null;
			}

			return new Entry(candidate -> candidate.getFluid().isSame(fluid), burnTime, amountConsumedPerTick, freezing);
		}

		ResourceLocation tagId = ResourceLocation.tryParse(GsonHelper.getAsString(json, "tag"));
		if (tagId == null) {
			CreateFluid.LOGGER.warn("Skipping snowman cooler fuel entry in {} because tag is not a valid resource location.", fileId);
			return null;
		}

		TagKey<Fluid> tagKey = TagKey.create(BuiltInRegistries.FLUID.key(), tagId);
		return new Entry(candidate -> candidate.getFluid().builtInRegistryHolder().is(tagKey), burnTime, amountConsumedPerTick, freezing);
	}

	public record Entry(Predicate<FluidStack> predicate, int burnTime, int amountConsumedPerTick, boolean freezing) {
		public boolean matches(FluidStack stack) {
			return predicate.test(stack);
		}

		public int getBurnTimeForAmount(int amount) {
			if (amount <= 0)
				return 0;
			return amount * burnTime / amountConsumedPerTick;
		}
	}
}
