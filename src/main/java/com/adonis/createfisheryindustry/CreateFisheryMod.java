package com.adonis.createfisheryindustry;

import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.adonis.createfisheryindustry.registry.*;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(CreateFisheryMod.MODID)
public class CreateFisheryMod {
    public static final String MODID = "createfisheryindustry";
    public static final Logger LOGGER = LogUtils.getLogger();

    // 使用你自己的模组ID，而不是Create的ID
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID)
            .setTooltipModifierFactory(item ->
                    new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                            .andThen(TooltipModifier.mapNull(KineticStats.create(item)))
            );

    public static ResourceLocation asResource(String path) {
        return new ResourceLocation(MODID, path);
    }

    public CreateFisheryMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 确保在注册其他内容之前设置好registrate
        REGISTRATE.registerEventListeners(modEventBus);

        // 注册所有内容
        CreateFisheryBlocks.register();
        CreateFisheryBlockEntities.register(modEventBus);
        CreateFisheryEntityTypes.register(modEventBus);
        CreateFisheryItems.register(modEventBus);
        CreateFisheryTabs.register(modEventBus);

        // 注册配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CreateFisheryCommonConfig.CONFIG_SPEC);

        // 注册事件监听器
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