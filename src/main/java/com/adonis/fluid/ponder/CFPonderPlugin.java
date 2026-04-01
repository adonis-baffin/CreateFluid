package com.adonis.fluid.ponder;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.registry.CFBlocks;
import com.adonis.fluid.registry.CFItems;
import com.simibubi.create.Create;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

public class CFPonderPlugin implements PonderPlugin {

    private static final ResourceLocation FLUIDS = Create.asResource("fluids");
    private static final ResourceLocation ARM_TARGETS = Create.asResource("arm_targets");
    private static final ResourceLocation KINETIC_APPLIANCES = Create.asResource("kinetic_appliances");

    @Override
    public String getModId() {
        return CreateFluid.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderPlugin.super.registerScenes(helper);

        // 注册动力移液器的三个场景 - 使用与 1.20.1 相同的场景 ID
        helper.forComponents(CFBlocks.PIPETTE.getId())
                .addStoryBoard("pipette", PipetteScenes::setup)
                .addStoryBoard("pipette_filter", PipetteScenes::filtering)
                .addStoryBoard("pipette_fill", PipetteScenes::filling);

        // 注册指挥棒的场景
        helper.forComponents(CFItems.BATON.getId())
                .addStoryBoard("baton", ConductorBatonScenes::usage);

        // 注册铜龙头的场景
        helper.forComponents(CFBlocks.COPPER_TAP.getId())
                .addStoryBoard("tap", CopperTapScenes::tap);

        // 注册离心泵的场景
        helper.forComponents(CFBlocks.CENTRIFUGAL_PUMP.getId())
                .addStoryBoard("centrifugal_pump", CentrifugalPumpScenes::centrifugalpump);

        // 注册集水器的场景
        helper.forComponents(CFBlocks.GUTTER_OUTLET.getId())
                .addStoryBoard("gutter_outlet", GutterOutletScenes::gutteroutlet)
                .addStoryBoard("gutter_outlet_interact", GutterOutletScenes::gutteroutletinteract);

        // 注册智能集水器的场景
        helper.forComponents(CFBlocks.SMART_GUTTER_OUTLET.getId())
                .addStoryBoard("gutter_outlet_interact", GutterOutletScenes::gutteroutletinteract);

        // 注册细雪流体的场景（使用原版细雪桶）
        helper.forComponents(BuiltInRegistries.ITEM.getKey(Items.POWDER_SNOW_BUCKET))
                .addStoryBoard("powder_snow", PowderSnowScenes::snow);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        PonderPlugin.super.registerTags(helper);

        // KINETIC_APPLIANCES: 移液器、离心泵
        helper.addToTag(KINETIC_APPLIANCES)
                .add(CFBlocks.PIPETTE.getId())
                .add(CFBlocks.CENTRIFUGAL_PUMP.getId());

        // FLUIDS: 移液器、离心泵、铜龙头、智能集水器、集水器
        helper.addToTag(FLUIDS)
                .add(CFBlocks.PIPETTE.getId())
                .add(CFBlocks.CENTRIFUGAL_PUMP.getId())
                .add(CFBlocks.COPPER_TAP.getId())
                .add(CFBlocks.SMART_GUTTER_OUTLET.getId())
                .add(CFBlocks.GUTTER_OUTLET.getId());

        // ARM_TARGETS: 指挥棒
        helper.addToTag(ARM_TARGETS)
                .add(CFItems.BATON.getId());

        // FLUIDS: 细雪桶
        helper.addToTag(FLUIDS)
                .add(BuiltInRegistries.ITEM.getKey(Items.POWDER_SNOW_BUCKET));
    }
}
