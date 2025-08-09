package com.adonis.fluid.config;

import com.adonis.fluid.CreateFluid;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.apache.commons.lang3.tuple.Pair;

@Mod.EventBusSubscriber(modid = CreateFluid.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CFConfig {
    private static final CFServer SERVER_CONFIG;
    private static final ForgeConfigSpec SERVER_SPEC;

    static {
        Pair<CFServer, ForgeConfigSpec> serverSpecPair = new ForgeConfigSpec.Builder().configure(builder -> {
            CFServer config = new CFServer();
            config.registerAll(builder);
            return config;
        });
        SERVER_CONFIG = serverSpecPair.getLeft();
        SERVER_SPEC = serverSpecPair.getRight();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);
    }

    public static CFServer server() {
        return SERVER_CONFIG;
    }

    public static CFStress stress() {
        return SERVER_CONFIG.kinetics.stressValues;
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SERVER_SPEC) {
            SERVER_CONFIG.onLoad();
        }
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SERVER_SPEC) {
            SERVER_CONFIG.onReload();
        }
    }
}