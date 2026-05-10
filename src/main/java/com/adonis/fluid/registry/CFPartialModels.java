package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.Create;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

public class CFPartialModels {
    private static boolean initialized = false;
    private static boolean useFallback = false;

    public static PartialModel PIPETTE_COG;
    public static PartialModel PIPETTE_BASE;
    public static PartialModel PIPETTE_LOWER_ARM;
    public static PartialModel PIPETTE_UPPER_ARM;
    public static PartialModel PIPETTE_HEAD_EMPTY;
    public static PartialModel PIPETTE_HEAD_250;
    public static PartialModel PIPETTE_HEAD_500;
    public static PartialModel PIPETTE_HEAD_750;
    public static PartialModel PIPETTE_HEAD_1000;
    public static PartialModel PIPETTE_HEAD;

    public static PartialModel CAN_FILLER_TRAY;
    public static PartialModel CAN_FILLER_HATCH_OPEN;
    public static PartialModel CAN_FILLER_HATCH_CLOSED;
    public static PartialModel SMART_REPACKAGER_TRAY;
    public static PartialModel SMART_REPACKAGER_HATCH_OPEN;
    public static PartialModel SMART_REPACKAGER_HATCH_CLOSED;

    public static PartialModel FLUID_ATOMIZER_SHAFT;
    public static PartialModel FLUID_ATOMIZER_FAN;

    // Only the protruding nozzle; the interface body already contains the base.
    public static PartialModel FLUID_INTERFACE_DRAIN;
    public static PartialModel LOGISTICS_JUNCTION_ATTACH;
    public static PartialModel LOGISTICS_JUNCTION_COLLAR;

    public static void register() {
        // Trigger static initialization during mod construction.
    }

    public static void init() {
        if (initialized) return;

        try {
            PIPETTE_COG = AllPartialModels.ARM_COG;

            try {
                PIPETTE_BASE = createPartialModel("pipette/base");
                PIPETTE_LOWER_ARM = createPartialModel("pipette/lower_arm");
                PIPETTE_UPPER_ARM = createPartialModel("pipette/upper_arm");
                PIPETTE_HEAD_EMPTY = createPartialModel("pipette/head_empty");
                PIPETTE_HEAD_250 = createPartialModel("pipette/head_250");
                PIPETTE_HEAD_500 = createPartialModel("pipette/head_500");
                PIPETTE_HEAD_750 = createPartialModel("pipette/head_750");
                PIPETTE_HEAD_1000 = createPartialModel("pipette/head_1000");
                PIPETTE_HEAD = PIPETTE_HEAD_EMPTY;
            } catch (Exception e) {
                initFallbackModels();
            }

            registerCustomPackageModels();

            CAN_FILLER_TRAY = createPartialModel("can_filler/tray");
            CAN_FILLER_HATCH_OPEN = createPartialModel("can_filler/hatch_open");
            CAN_FILLER_HATCH_CLOSED = createPartialModel("can_filler/hatch_closed");
            SMART_REPACKAGER_TRAY = createPartialModel("smart_repackager/tray");
            SMART_REPACKAGER_HATCH_OPEN = createPartialModel("smart_repackager/hatch_open");
            SMART_REPACKAGER_HATCH_CLOSED = createPartialModel("smart_repackager/hatch_closed");

            FLUID_ATOMIZER_SHAFT = createPartialModel("fluid_atomizer/shaft");
            FLUID_ATOMIZER_FAN = createPartialModel("fluid_atomizer/fan");

            FLUID_INTERFACE_DRAIN = createPartialModel("fluid_interface_drain");
            LOGISTICS_JUNCTION_ATTACH = createPartialModel("logistics_junction_attach");
            LOGISTICS_JUNCTION_COLLAR = createPartialModel("logistics_junction_collar");
            initialized = true;
        } catch (Exception e) {
            initFallbackModels();
        }
    }

    private static void registerCustomPackageModels() {
        try {
            PartialModel creeperRigging = AllPartialModels.PACKAGE_RIGGING.get(Create.asResource("rare_creeper_package"));

            registerPackageModel("copper_can", "item/copper_can", creeperRigging);
            registerPackageModel("brass_box", "item/brass_box", creeperRigging);
        } catch (Exception e) {
            // Ignore; package visuals can fail independently.
        }
    }

    private static void registerPackageModel(String itemName, String modelPath, PartialModel rigging) {
        ResourceLocation packageId = ResourceLocation.fromNamespaceAndPath(CreateFluid.MOD_ID, itemName);
        PartialModel model = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateFluid.MOD_ID, modelPath));

        if (model != null) {
            AllPartialModels.PACKAGES.put(packageId, model);
        }
        if (rigging != null) {
            AllPartialModels.PACKAGE_RIGGING.put(packageId, rigging);
        }
    }

    private static void initFallbackModels() {
        PIPETTE_COG = AllPartialModels.ARM_COG;
        PIPETTE_BASE = AllPartialModels.ARM_BASE;
        PIPETTE_LOWER_ARM = AllPartialModels.ARM_LOWER_BODY;
        PIPETTE_UPPER_ARM = AllPartialModels.ARM_UPPER_BODY;

        PIPETTE_HEAD_EMPTY = AllPartialModels.ARM_CLAW_BASE;
        PIPETTE_HEAD_250 = AllPartialModels.ARM_CLAW_BASE;
        PIPETTE_HEAD_500 = AllPartialModels.ARM_CLAW_BASE;
        PIPETTE_HEAD_750 = AllPartialModels.ARM_CLAW_BASE;
        PIPETTE_HEAD_1000 = AllPartialModels.ARM_CLAW_BASE;
        PIPETTE_HEAD = AllPartialModels.ARM_CLAW_BASE;

        useFallback = true;
        initialized = true;
    }

    private static PartialModel createPartialModel(String path) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(CreateFluid.MOD_ID, "block/" + path);
        return PartialModel.of(location);
    }

    public static PartialModel getPipetteHeadForFluidAmount(int fluidAmount) {
        if (!initialized) {
            init();
        }

        if (useFallback) {
            return PIPETTE_HEAD_EMPTY != null ? PIPETTE_HEAD_EMPTY : AllPartialModels.ARM_CLAW_BASE;
        }

        try {
            if (fluidAmount >= 1000 && PIPETTE_HEAD_1000 != null) return PIPETTE_HEAD_1000;
            if (fluidAmount >= 750 && PIPETTE_HEAD_750 != null) return PIPETTE_HEAD_750;
            if (fluidAmount >= 500 && PIPETTE_HEAD_500 != null) return PIPETTE_HEAD_500;
            if (fluidAmount >= 250 && PIPETTE_HEAD_250 != null) return PIPETTE_HEAD_250;
            if (PIPETTE_HEAD_EMPTY != null) return PIPETTE_HEAD_EMPTY;
        } catch (Exception e) {
            // Ignore and fall back below.
        }

        return AllPartialModels.ARM_CLAW_BASE;
    }

    public static boolean isUsingFallback() {
        return useFallback;
    }

    public static PartialModel getModelOrFallback(PartialModel model, PartialModel fallback) {
        if (model != null) {
            return model;
        }
        if (fallback != null) {
            return fallback;
        }
        return AllPartialModels.ARM_BASE;
    }
}
