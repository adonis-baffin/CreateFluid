package com.adonis.fluid;

import com.adonis.fluid.config.CFCommonConfig;
import com.adonis.fluid.config.CFConfig;
import com.adonis.fluid.content.pipette.FluidInteractionPointCompat;
import com.adonis.fluid.networking.CFNetworking;
import com.adonis.fluid.registry.*;
import com.mojang.logging.LogUtils;
import com.simibubi.create.api.stress.BlockStressValues;
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
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

import java.util.Random;

@Mod(CreateFluid.MODID)
public class CreateFluid {
    public static final String MODID = "fluid";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final Random RANDOM = new Random();

    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID)
            .setTooltipModifierFactory(item ->
                    new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                            .andThen(TooltipModifier.mapNull(KineticStats.create(item))));

    public static ResourceLocation asResource(String path) {
        return new ResourceLocation(MODID, path);
    }

    public CreateFluid() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        REGISTRATE.registerEventListeners(modEventBus);

        CFMountedStorageTypes.register();
        CFBlock.register();
        CFBlockEntity.register(modEventBus);
        CFItem.register(modEventBus);
        CFTab.register(modEventBus);
        CFFluid.register();

        // 注册Common配置（GutterOutlet等）
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CFCommonConfig.CONFIG_SPEC);

        // 注册Server配置（包含kinetics、centrifugalPumpRange、stressValues）
        // 注意：这是唯一的SERVER配置，包含所有服务端可配置项
        CFConfig.register();

        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::enqueueIMC);
        modEventBus.addListener(this::processIMC);
        modEventBus.addListener(this::onModConfigEvent);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CFCommonConfig.onLoad();
            CFPartialModels.init();

            // 注册应力值提供者 - 使用嵌套在CFKinetics中的stressValues
            BlockStressValues.IMPACTS.registerProvider(CFConfig.server().kinetics.stressValues::getImpact);
            BlockStressValues.CAPACITIES.registerProvider(CFConfig.server().kinetics.stressValues::getCapacity);

            CFNetworking.register();

            FluidInteractionPointCompat.init();
        });
    }

    private void enqueueIMC(final InterModEnqueueEvent event) {}

    private void processIMC(final InterModProcessEvent event) {}

    private void onModConfigEvent(ModConfigEvent event) {
        ModConfig config = event.getConfig();

        if (config.getSpec() == CFCommonConfig.CONFIG_SPEC) {
            if (event instanceof ModConfigEvent.Loading) {
                CFCommonConfig.onLoad();
            } else if (event instanceof ModConfigEvent.Reloading) {
                CFCommonConfig.onReload();
            }
        }
        // CFConfig的事件在CFConfig类中处理
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        CFCommonConfig.onLoad();
    }
}
