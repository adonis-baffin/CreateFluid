package com.adonis.fluid;

import com.adonis.fluid.config.CFCommonConfig;
import com.adonis.fluid.event.SuperJumpFallProtection;
import com.adonis.fluid.packet.PipettePlacementPacket;
import com.adonis.fluid.registry.CFBlock;
import com.adonis.fluid.registry.CFBlockEntity;
import com.adonis.fluid.registry.CFItem;
import com.adonis.fluid.registry.CFTab;
import com.mojang.logging.LogUtils;
import com.simibubi.create.AllPackets;
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
import net.minecraftforge.network.simple.SimpleChannel;
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

        // 注册模组内容
        CFBlock.register();
        CFBlockEntity.register(modEventBus);
        CFItem.register(modEventBus);
        CFTab.register(modEventBus);

        // 注册配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CFCommonConfig.CONFIG_SPEC);

        // 注册事件监听器
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::enqueueIMC);
        modEventBus.addListener(this::processIMC);
        modEventBus.addListener(this::clientInit);

        // 注册Forge事件
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(SuperJumpFallProtection.class);
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CFCommonConfig.onLoad();

            // 注册网络包
            SimpleChannel channel = AllPackets.getChannel();

            // 获取下一个可用的ID
            int id = 100; // 使用一个安全的起始ID，避免与Create原版包冲突

            channel.registerMessage(id++, PipettePlacementPacket.class,
                    (msg, buf) -> msg.write(buf),
                    PipettePlacementPacket::new,
                    (msg, ctxSupplier) -> msg.handle(ctxSupplier.get()));

            channel.registerMessage(id++, PipettePlacementPacket.ClientBoundRequest.class,
                    (msg, buf) -> msg.write(buf),
                    PipettePlacementPacket.ClientBoundRequest::new,
                    (msg, ctxSupplier) -> msg.handle(ctxSupplier.get()));
        });
    }

    private void enqueueIMC(final InterModEnqueueEvent event) {
        // Inter-mod communication setup
    }

    private void processIMC(final InterModProcessEvent event) {
        // Inter-mod communication processing
    }

    private void clientInit(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            CFBlock.setupRenderLayers();
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        CFCommonConfig.onLoad();
    }
}