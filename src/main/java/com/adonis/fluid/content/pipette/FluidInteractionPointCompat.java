package com.adonis.fluid.content.pipette;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.registry.CFBlocks;
import com.simibubi.create.AllBlocks;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;

public class FluidInteractionPointCompat {

    public static final String CREATE_ENCHANTMENT_INDUSTRY = "create_enchantment_industry";
    public static final String CREATEADDITION = "createaddition";
    public static final String CREATE_DIESEL = "createdieselgenerators";
    public static final String IRONS_SPELLBOOKS = "irons_spellbooks";
    public static final String YOUKAIS_FEASTS = "youkaisfeasts";
    public static final String EMBERS = "embers";
    public static final String YOUKAIS_HOMECOMING = "youkaishomecoming";

    public static void init() {
        registerExistingBlocks();
        registerModCompat();
        CreateFluid.LOGGER.info("Fluid interaction point compatibility initialized");
    }

    private static void registerExistingBlocks() {
        FluidInteractionPointTypes.register(AllBlocks.DEPOT.get(), DepotFluidInteractionPoint::new);
        FluidInteractionPointTypes.register(AllBlocks.WEIGHTED_EJECTOR.get(), DepotFluidInteractionPoint::new);
        FluidInteractionPointTypes.register(AllBlocks.ITEM_DRAIN.get(), ItemDrainFluidInteractionPoint::new);
        FluidInteractionPointTypes.register(AllBlocks.BASIN.get(), FluidInteractionPoint::new);
        FluidInteractionPointTypes.register(AllBlocks.BLAZE_BURNER.get(), FluidInteractionPoint::new);
        FluidInteractionPointTypes.register(AllBlocks.LIT_BLAZE_BURNER.get(), FluidInteractionPoint::new);

        FluidInteractionPointTypes.registerConditional(
                state -> state.getBlock() instanceof net.minecraft.world.level.block.BeehiveBlock,
                FluidInteractionPoint::new,
                100
        );

        FluidInteractionPointTypes.register(Blocks.CAULDRON, FluidInteractionPoint::new);
        FluidInteractionPointTypes.register(Blocks.WATER_CAULDRON, FluidInteractionPoint::new);
        FluidInteractionPointTypes.register(Blocks.LAVA_CAULDRON, FluidInteractionPoint::new);
        FluidInteractionPointTypes.register(Blocks.POWDER_SNOW_CAULDRON, FluidInteractionPoint::new);

        FluidInteractionPointTypes.register(CFBlocks.FLUID_INTERFACE.get(), FluidInteractionPoint::new);
        FluidInteractionPointTypes.register(CFBlocks.SMART_FLUID_INTERFACE.get(), FluidInteractionPoint::new);
        FluidInteractionPointTypes.register(CFBlocks.GUTTER_OUTLET.get(), FluidInteractionPoint::new);
        FluidInteractionPointTypes.register(CFBlocks.SMART_GUTTER_OUTLET.get(), FluidInteractionPoint::new);
        FluidInteractionPointTypes.register(CFBlocks.COPPER_SINK.get(), FluidInteractionPoint::new);
    }

    private static void registerModCompat() {
        if (isModLoaded(CREATE_ENCHANTMENT_INDUSTRY)) {
            FluidInteractionPointTypes.registerDeferred(
                    CREATE_ENCHANTMENT_INDUSTRY, "blaze_enchanter",
                    (level, pos, state) -> new GenericFluidInteractionPoint(level, pos, state, FluidInteractionPoint.Mode.DEPOSIT)
            );
            FluidInteractionPointTypes.registerDeferred(
                    CREATE_ENCHANTMENT_INDUSTRY, "blaze_forger",
                    (level, pos, state) -> new GenericFluidInteractionPoint(level, pos, state, FluidInteractionPoint.Mode.DEPOSIT)
            );
            FluidInteractionPointTypes.registerDeferred(
                    CREATE_ENCHANTMENT_INDUSTRY, "printer",
                    GenericFluidInteractionPoint::new
            );
            FluidInteractionPointTypes.registerDeferred(
                    CREATE_ENCHANTMENT_INDUSTRY, "experience_lantern",
                    GenericFluidInteractionPoint::new
            );
        }

        if (isModLoaded(CREATEADDITION)) {
            FluidInteractionPointTypes.registerDeferred(
                    CREATEADDITION, "liquid_blaze_burner",
                    (level, pos, state) -> new GenericFluidInteractionPoint(level, pos, state, FluidInteractionPoint.Mode.DEPOSIT)
            );
        }

        if (isModLoaded(CREATE_DIESEL)) {
            FluidInteractionPointTypes.registerDeferred(
                    CREATE_DIESEL, "burner",
                    (level, pos, state) -> new GenericFluidInteractionPoint(level, pos, state, FluidInteractionPoint.Mode.DEPOSIT)
            );
            FluidInteractionPointTypes.registerDeferred(
                    CREATE_DIESEL, "chemical_turret",
                    (level, pos, state) -> new GenericFluidInteractionPoint(level, pos, state, FluidInteractionPoint.Mode.DEPOSIT)
            );
            FluidInteractionPointTypes.registerDeferred(
                    CREATE_DIESEL, "diesel_engine",
                    (level, pos, state) -> new GenericFluidInteractionPoint(level, pos, state, FluidInteractionPoint.Mode.DEPOSIT)
            );
            FluidInteractionPointTypes.registerDeferred(
                    CREATE_DIESEL, "large_diesel_engine",
                    (level, pos, state) -> new GenericFluidInteractionPoint(level, pos, state, FluidInteractionPoint.Mode.DEPOSIT)
            );
            FluidInteractionPointTypes.registerDeferred(
                    CREATE_DIESEL, "huge_diesel_engine",
                    (level, pos, state) -> new GenericFluidInteractionPoint(level, pos, state, FluidInteractionPoint.Mode.DEPOSIT)
            );
            FluidInteractionPointTypes.registerDeferred(
                    CREATE_DIESEL, "distillation_tank",
                    GenericFluidInteractionPoint::new
            );
        }

        if (isModLoaded(IRONS_SPELLBOOKS)) {
            FluidInteractionPointTypes.registerDeferred(
                    IRONS_SPELLBOOKS, "alchemist_cauldron",
                    GenericFluidInteractionPoint::new
            );
        }

        if (isModLoaded(YOUKAIS_FEASTS)) {
            FluidInteractionPointTypes.registerDeferred(YOUKAIS_FEASTS, "fermentation_tank", GenericFluidInteractionPoint::new);
            FluidInteractionPointTypes.registerDeferred(YOUKAIS_FEASTS, "wood_basin", GenericFluidInteractionPoint::new);
        }

        if (isModLoaded(EMBERS)) {
            FluidInteractionPointTypes.registerDeferred(
                    EMBERS, "fluid_vessel",
                    (level, pos, state) -> new GenericFluidInteractionPoint(level, pos, state, FluidInteractionPoint.Mode.DEPOSIT)
            );
        }

        if (isModLoaded(YOUKAIS_HOMECOMING)) {
            FluidInteractionPointTypes.registerDeferred(YOUKAIS_HOMECOMING, "fermentation_tank", GenericFluidInteractionPoint::new);
            FluidInteractionPointTypes.registerDeferred(YOUKAIS_HOMECOMING, "wood_basin", GenericFluidInteractionPoint::new);
        }
    }

    private static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
