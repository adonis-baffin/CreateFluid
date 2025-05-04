package com.adonis.createfisheryindustry;

import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import com.adonis.createfisheryindustry.registry.CreateFisheryTabs;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CreateFisheryMod.MODID)
public class CreateFisheryMod {
    public static final String MODID = "createfisheryindustry";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID);

    public static ResourceLocation asResource(String path) {
        return new ResourceLocation(MODID, path);
    }

    static {
        REGISTRATE.setTooltipModifierFactory(item -> new ItemDescription.Modifier(item, null)
                .andThen(TooltipModifier.mapNull(KineticStats.create(item))));
    }

    public CreateFisheryMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        REGISTRATE.registerEventListeners(modEventBus);

        CreateFisheryBlocks.register();
        CreateFisheryBlockEntities.register(modEventBus);
        CreateFisheryItems.register(modEventBus);
        CreateFisheryTabs.register(modEventBus);

        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::enqueueIMC);
        modEventBus.addListener(this::processIMC);
        modEventBus.addListener(this::clientInit);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("Create: Fishery Industry is setting up!");
    }

    private void enqueueIMC(final InterModEnqueueEvent event) {}

    private void processIMC(final InterModProcessEvent event) {}

    private void clientInit(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            CreateFisheryBlocks.setupRenderLayers();
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        CreateFisheryCommonConfig.onLoad();
        CreateFisheryMod.LOGGER.info("Server starting, ensuring config is loaded.");
    }
}