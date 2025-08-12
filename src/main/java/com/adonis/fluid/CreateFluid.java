package com.adonis.fluid;

import com.adonis.fluid.config.CFCommonConfig;
import com.adonis.fluid.config.CFStress;
import com.adonis.fluid.handler.PipetteFluidInteractionPointHandler;
import com.adonis.fluid.packet.PipetteFluidPlacementPacket;
import com.adonis.fluid.registry.*;
import com.mojang.logging.LogUtils;
import com.simibubi.create.AllPackets;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.fml.loading.FMLEnvironment;
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

    public static final CFStress STRESS_CONFIG = new CFStress(MODID);

    private static ForgeConfigSpec stressConfigSpec;

    public static ResourceLocation asResource(String path) {
        return new ResourceLocation(MODID, path);
    }

    public CreateFluid() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        REGISTRATE.registerEventListeners(modEventBus);

        // 注册模组内容
        CFBlock.register();
        CFBlockEntity.register(modEventBus);
        CFItem.register(modEventBus);
        CFTab.register(modEventBus);

        // 注册普通配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CFCommonConfig.CONFIG_SPEC);
        ForgeConfigSpec.Builder stressBuilder = new ForgeConfigSpec.Builder();
        STRESS_CONFIG.registerAll(stressBuilder);
        stressConfigSpec = stressBuilder.build();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, stressConfigSpec, STRESS_CONFIG.getName() + ".toml");

        // 注册事件监听器
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::enqueueIMC);
        modEventBus.addListener(this::processIMC);
        modEventBus.addListener(this::clientInit);
        modEventBus.addListener(this::onModConfigEvent);

        // 注册Forge事件
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);

        // 客户端事件注册
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.addListener(CreateFluid::onClientTick);
        }
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CFCommonConfig.onLoad();
            CFPartialModels.init();

            BlockStressValues.IMPACTS.registerProvider(STRESS_CONFIG::getImpact);
            BlockStressValues.CAPACITIES.registerProvider(STRESS_CONFIG::getCapacity);

            // 注册流体网络包
            SimpleChannel channel = AllPackets.getChannel();
            int id = 200;

            channel.registerMessage(id++, PipetteFluidPlacementPacket.class,
                    (msg, buf) -> msg.write(buf),
                    PipetteFluidPlacementPacket::new,
                    (msg, ctxSupplier) -> {
                        NetworkEvent.Context ctx = ctxSupplier.get();
                        boolean handled = msg.handle(ctx);
                        ctx.setPacketHandled(handled);
                    });

            channel.registerMessage(id++, PipetteFluidPlacementPacket.ClientBoundRequest.class,
                    (msg, buf) -> msg.write(buf),
                    PipetteFluidPlacementPacket.ClientBoundRequest::new,
                    (msg, ctxSupplier) -> {
                        NetworkEvent.Context ctx = ctxSupplier.get();
                        boolean handled = msg.handle(ctx);
                        ctx.setPacketHandled(handled);
                    });
        });
    }

    private void enqueueIMC(final InterModEnqueueEvent event) {}
    private void processIMC(final InterModProcessEvent event) {}

    private void clientInit(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            CFBlock.setupRenderLayers();
        });
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            PipetteFluidInteractionPointHandler.tick();
        }
    }

    private void onModConfigEvent(ModConfigEvent event) {
        ModConfig config = event.getConfig();

        if (config.getSpec() == CFCommonConfig.CONFIG_SPEC) {
            if (event instanceof ModConfigEvent.Loading) {
                CFCommonConfig.onLoad();
            } else if (event instanceof ModConfigEvent.Reloading) {
                CFCommonConfig.onReload();
            }
        } else if (stressConfigSpec != null && config.getSpec() == stressConfigSpec) {
            if (event instanceof ModConfigEvent.Loading) {
            } else if (event instanceof ModConfigEvent.Reloading) {
            }
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        CFCommonConfig.onLoad();
    }
}