package com.adonis.fluid;

import com.adonis.fluid.config.CFCommonConfig;
import com.adonis.fluid.config.CFStressConfig;
import com.adonis.fluid.content.pipette.FluidInteractionPointCompat;
import com.adonis.fluid.content.pipette.FluidInteractionPointTypes;
import com.adonis.fluid.fluid.powdersnow.PowderSnowBucketHandler;
import com.adonis.fluid.fluid.quicksand.QuicksandBucketHandler;
import com.adonis.fluid.fluid.quicksand.QuicksandFluid;
import com.adonis.fluid.registry.*;
import com.simibubi.create.api.behaviour.spouting.CauldronSpoutingBehavior;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Mod(CreateFluid.MOD_ID)
public class CreateFluid {
	public static final String MOD_ID = "fluid";
	public static final String NAME = "Create Fluid";
	public static final Random RANDOM = new Random();
	public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(NAME);

	public static final CFRegistrate REGISTRATE = CFRegistrate.create(MOD_ID)
		.setTooltipModifierFactory(item ->
			new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
				.andThen(TooltipModifier.mapNull(KineticStats.create(item)))
		);

	public static final CFStressConfig STRESS_CONFIG = new CFStressConfig(MOD_ID);
	private static final Map<String, String> INCOMPATIBLE_LOGISTICS_MODS = Map.of(
		"create_factory_logistics", "Create Factory Logistics",
		"fluidlogistics", "Create Fluid Logistic",
		"repackaged", "Repackaged"
	);

	private static ModConfigSpec stressConfigSpec;

		public CreateFluid(IEventBus modEventBus, ModContainer modContainer) {
			checkIncompatibleLogisticsMods();

		// 注册 Registrate
		REGISTRATE.registerEventListeners(modEventBus);

		// 注册 DataComponents
		CFDataComponents.REGISTER.register(modEventBus);

		// 注册所有内容
		CFFluids.FLUID_TYPES.register(modEventBus);
		CFFluids.FLUIDS.register(modEventBus);
		CFFluids.register();
		CFRecipeTypes.register(modEventBus);
		CFFanProcessingTypes.register(modEventBus);
		CFBlocks.register();
		CFBlockEntities.register();
		CFMountedStorageTypes.register();
		CFBlockEntities.registerToEventBus(modEventBus);
		CFItems.register();
		CFCreativeTab.register(modEventBus);

		// 注册细雪桶能力
		modEventBus.addListener(PowderSnowBucketHandler::register);

		// 注册流沙桶能力
		modEventBus.addListener(QuicksandBucketHandler::register);

		// 注册应力配置
		ModConfigSpec.Builder stressBuilder = new ModConfigSpec.Builder();
		STRESS_CONFIG.registerAll(stressBuilder);
		stressConfigSpec = stressBuilder.build();
		modContainer.registerConfig(ModConfig.Type.SERVER, stressConfigSpec, STRESS_CONFIG.getName() + ".toml");

		// 注册 Common 配置（GutterOutlet 等）
		modContainer.registerConfig(ModConfig.Type.COMMON, CFCommonConfig.CONFIG_SPEC, MOD_ID + "-common.toml");

		// 注册事件监听器
		modEventBus.addListener(this::onCommonSetup);
		modEventBus.addListener(this::onModConfigEvent);
		modEventBus.register(CFBlockEntities.class);
	}

	private void onCommonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			CFPartialModels.init();
			// 细雪流体相关初始化
			CFFluids.register();

			// 注册雾化器加工类型映射
			com.adonis.fluid.block.FluidAtomizer.AtomizerProcessingRegistry.init();

			// 注册粉末雪流体到炼药锅注液行为（使注液器可以向炼药锅注入粉末雪，直接注满）
			CauldronSpoutingBehavior.CAULDRON_INFO.register(
				CFFluids.POWDER_SNOW.get(),
				new CauldronSpoutingBehavior.CauldronInfo(1000,
					Blocks.POWDER_SNOW_CAULDRON.defaultBlockState()
						.setValue(BlockStateProperties.LEVEL_CAULDRON, 3))
			);

			// 设置流沙流体对应的桶物品
			QuicksandFluid.setBucketSupplier(() -> CFItems.QUICKSAND_BUCKET.asItem());

			// 注册原版细雪桶的 tooltip
			TooltipModifier.REGISTRY.register(Items.POWDER_SNOW_BUCKET,
				new ItemDescription.Modifier(Items.POWDER_SNOW_BUCKET, FontHelper.Palette.STANDARD_CREATE));
			FluidInteractionPointCompat.init();
			FluidInteractionPointTypes.resolveDeferredRegistrations();
		});
	}

	private void onModConfigEvent(ModConfigEvent event) {
		ModConfig config = event.getConfig();

		if (stressConfigSpec != null && config.getSpec() == stressConfigSpec) {
			// 配置已加载或重新加载，静默处理
		}

		if (config.getSpec() == CFCommonConfig.CONFIG_SPEC) {
			if (event instanceof ModConfigEvent.Loading) {
				CFCommonConfig.onLoad();
			} else if (event instanceof ModConfigEvent.Reloading) {
				CFCommonConfig.onReload();
			}
		}
	}

	public static ResourceLocation asResource(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	private static void checkIncompatibleLogisticsMods() {
		List<String> detected = new ArrayList<>();
		for (Map.Entry<String, String> entry : INCOMPATIBLE_LOGISTICS_MODS.entrySet()) {
			if (ModList.get().isLoaded(entry.getKey()))
				detected.add(entry.getValue() + " (" + entry.getKey() + ")");
		}

		if (detected.isEmpty())
			return;

		String message = """
			Create Fluid 2.0+ is incompatible with the following installed mod(s):
			%s

			Please remove one of the conflicting mods, or downgrade Create Fluid to a 1.x version.
			""".formatted(String.join("\n", detected.stream()
			.map(mod -> "- " + mod)
			.toList()));

		LOGGER.error(message);
		throw new IllegalStateException(message);
	}
}
