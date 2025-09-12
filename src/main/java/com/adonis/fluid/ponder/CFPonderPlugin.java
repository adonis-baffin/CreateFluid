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
        
        // 注册动力移液器的场景
        HELPER.forComponents(CFBlock.PIPETTE)  // 假设你的动力移液器方块注册为PIPETTE
                .addStoryBoard("pipette", PipetteScenes::setup);

//         HELPER.forComponents(CFItem.BATON)
//                 .addStoryBoard("conductor_baton", ConductorBatonScenes::usage);
    }
    
    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        PonderPlugin.super.registerTags(helper);
        
        PonderTagRegistrationHelper<RegistryEntry<?>> HELPER = helper.withKeyFunction(RegistryEntry::getId);
        
        // 将动力移液器添加到机械动力的既有标签中
        HELPER.addToTag(AllCreatePonderTags.KINETIC_APPLIANCES)
                .add(CFBlock.PIPETTE);  // 假设你的动力移液器方块注册为PIPETTE
        
        // 如果需要，也可以添加到其他标签
        // HELPER.addToTag(AllCreatePonderTags.FLUIDS)
        //         .add(CFBlock.PIPETTE);
    }
}