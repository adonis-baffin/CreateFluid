package com.adonis.fluid.block.FluidAtomizer;

import com.adonis.fluid.CreateFluid;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = CreateFluid.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class AtomizerCompatReloadListener extends SimpleJsonResourceReloadListener {

	private static final Gson GSON = new GsonBuilder().create();
	private static final String DIRECTORY = "createfluid/atomizer_compat";
	private static final AtomizerCompatReloadListener INSTANCE = new AtomizerCompatReloadListener();

	private AtomizerCompatReloadListener() {
		super(GSON, DIRECTORY);
	}

	@SubscribeEvent
	public static void onAddReloadListeners(AddReloadListenerEvent event) {
		event.addListener(INSTANCE);
	}

	@Override
	protected void apply(java.util.Map<ResourceLocation, JsonElement> jsonMap, net.minecraft.server.packs.resources.ResourceManager resourceManager,
						 ProfilerFiller profiler) {
		List<AtomizerProcessingRegistry.Entry> entries = new ArrayList<>();

		for (java.util.Map.Entry<ResourceLocation, JsonElement> resourceEntry : jsonMap.entrySet()) {
			ResourceLocation fileId = resourceEntry.getKey();
			JsonElement jsonElement = resourceEntry.getValue();
			if (!jsonElement.isJsonObject()) {
				CreateFluid.LOGGER.warn("Skipping atomizer compat file {} because it is not a JSON object.", fileId);
				continue;
			}

			JsonObject root = jsonElement.getAsJsonObject();
			for (JsonElement entryElement : GsonHelper.getAsJsonArray(root, "entries", new com.google.gson.JsonArray())) {
				if (!entryElement.isJsonObject()) {
					CreateFluid.LOGGER.warn("Skipping atomizer compat entry in {} because it is not a JSON object.", fileId);
					continue;
				}

				AtomizerProcessingRegistry.Entry parsed = parseEntry(fileId, entryElement.getAsJsonObject());
				if (parsed != null)
					entries.add(parsed);
			}
		}

		AtomizerProcessingRegistry.replaceDatapackEntries(entries);
		CreateFluid.LOGGER.info("Loaded {} atomizer compat datapack entries.", entries.size());
	}

	private static AtomizerProcessingRegistry.Entry parseEntry(ResourceLocation fileId, JsonObject json) {
		if (json.has("required_mod")) {
			String requiredMod = GsonHelper.getAsString(json, "required_mod");
			if (!ModList.get().isLoaded(requiredMod))
				return null;
		}

		boolean hasFluid = json.has("fluid");
		boolean hasTag = json.has("tag");
		if (hasFluid == hasTag) {
			CreateFluid.LOGGER.warn("Skipping atomizer compat entry in {} because exactly one of 'fluid' or 'tag' is required.", fileId);
			return null;
		}

		ResourceLocation typeId = ResourceLocation.tryParse(GsonHelper.getAsString(json, "processing_type"));
		if (typeId == null) {
			CreateFluid.LOGGER.warn("Skipping atomizer compat entry in {} because processing_type is not a valid resource location.", fileId);
			return null;
		}

		FanProcessingType processingType = FanProcessingType.parse(typeId.toString());
		if (processingType == null) {
			CreateFluid.LOGGER.warn("Skipping atomizer compat entry in {} because processing type {} is not registered.", fileId, typeId);
			return null;
		}

		if (hasFluid) {
			ResourceLocation fluidId = ResourceLocation.tryParse(GsonHelper.getAsString(json, "fluid"));
			if (fluidId == null) {
				CreateFluid.LOGGER.warn("Skipping atomizer compat entry in {} because fluid is not a valid resource location.", fileId);
				return null;
			}

			Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
			if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) {
				CreateFluid.LOGGER.warn("Skipping atomizer compat entry in {} because fluid {} is not registered.", fileId, fluidId);
				return null;
			}

			return new AtomizerProcessingRegistry.Entry(candidate -> candidate.isSame(fluid), processingType);
		}

		ResourceLocation tagId = ResourceLocation.tryParse(GsonHelper.getAsString(json, "tag"));
		if (tagId == null) {
			CreateFluid.LOGGER.warn("Skipping atomizer compat entry in {} because tag is not a valid resource location.", fileId);
			return null;
		}

		TagKey<Fluid> tagKey = TagKey.create(BuiltInRegistries.FLUID.key(), tagId);
		return new AtomizerProcessingRegistry.Entry(fluid -> fluid.builtInRegistryHolder().is(tagKey), processingType);
	}
}
