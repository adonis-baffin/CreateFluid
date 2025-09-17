package com.adonis.fluid.fluid.powdersnow;

import com.adonis.fluid.registry.CFFluid;
import com.simibubi.create.api.behaviour.spouting.CauldronSpoutingBehavior;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod.EventBusSubscriber(modid = "fluid", bus = Mod.EventBusSubscriber.Bus.MOD)
public class PowderSnowCauldronSpoutingRegistry {
    
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 注册细雪炼药锅的注液行为
            CauldronSpoutingBehavior.CAULDRON_INFO.register(
                    CFFluid.POWDER_SNOW.get(),
                    new CauldronSpoutingBehavior.CauldronInfo(1000,
                            Blocks.POWDER_SNOW_CAULDRON.defaultBlockState()
                                    .setValue(LayeredCauldronBlock.LEVEL, 3)) // 设置为满
            );
        });
    }
}