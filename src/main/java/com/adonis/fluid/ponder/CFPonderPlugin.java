package com.adonis.fluid.ponder;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.registry.CFBlock;
import com.adonis.fluid.registry.CFItem;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import com.tterrag.registrate.util.entry.ItemProviderEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class CFPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return CreateFluid.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderPlugin.super.registerScenes(helper);

        PonderSceneRegistrationHelper<ItemProviderEntry<?>> HELPER = helper.withKeyFunction(RegistryEntry::getId);

        // 注册动力移液器的三个场景
        HELPER.forComponents(CFBlock.PIPETTE)
                .addStoryBoard("pipette", PipetteScenes::setup)
                .addStoryBoard("pipette_filter", PipetteScenes::filtering)
                .addStoryBoard("pipette_fill", PipetteScenes::filling);

        // 注册指挥棒的场景
        HELPER.forComponents(CFItem.BATON)
                .addStoryBoard("baton", ConductorBatonScenes::usage);

        // 注册离心泵的场景
        HELPER.forComponents(CFBlock.CENTRIFUGAL_PUMP)
                .addStoryBoard("centrifugal_pump", CentrifugalPumpScenes::centrifugalpump);

        // 注册铜龙头的场景
        HELPER.forComponents(CFBlock.COPPER_TAP)
                .addStoryBoard("tap", CopperTapScenes::tap);

        // 注册细雪桶的场景（绑定到原版物品）
        helper.forComponents(new ResourceLocation("minecraft", "powder_snow_bucket"))
                .addStoryBoard("powder_snow", PowderSnowScenes::snow);

        HELPER.forComponents(CFBlock.GUTTER_OUTLET)
                .addStoryBoard("gutter_outlet", GutterOutletScenes::gutteroutlet)
                .addStoryBoard("gutter_outlet_interact", GutterOutletScenes::gutteroutletinteract);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        PonderPlugin.super.registerTags(helper);

        PonderTagRegistrationHelper<RegistryEntry<?>> HELPER = helper.withKeyFunction(RegistryEntry::getId);

        // 将动力移液器和离心泵添加到机械动力的既有标签中
        HELPER.addToTag(AllCreatePonderTags.KINETIC_APPLIANCES)
                .add(CFBlock.PIPETTE)
                .add(CFBlock.CENTRIFUGAL_PUMP);

        // 添加到流体相关标签
        HELPER.addToTag(AllCreatePonderTags.FLUIDS)
                .add(CFBlock.PIPETTE)
                .add(CFBlock.CENTRIFUGAL_PUMP)
                .add(CFBlock.COPPER_TAP)
                .add(CFBlock.SMART_GUTTER_OUTLET)
                .add(CFBlock.GUTTER_OUTLET);

        // 将指挥棒添加到工具标签
        HELPER.addToTag(AllCreatePonderTags.ARM_TARGETS)
                .add(CFItem.BATON);
    }
}