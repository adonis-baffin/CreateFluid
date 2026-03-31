package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlockEntity;
import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpRenderer;
import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpVisual;
import com.adonis.fluid.block.CopperTap.CopperTapBlockEntity;
import com.adonis.fluid.block.CopperTap.CopperTapRenderer;
import com.adonis.fluid.block.FluidInterface.FluidInterfaceBlockEntity;
import com.adonis.fluid.block.FluidInterface.FluidInterfaceRenderer;
import com.adonis.fluid.block.GutterOutlet.GutterOutletBlockEntity;
import com.adonis.fluid.block.GutterOutlet.GutterOutletRenderer;
import com.adonis.fluid.block.GutterOutlet.SmartGutterOutletBlockEntity;
import com.adonis.fluid.block.Pipette.PipetteBlockEntity;
import com.adonis.fluid.block.Pipette.PipetteRenderer;
import com.adonis.fluid.block.SmartFluidInterface.SmartFluidInterfaceBlockEntity;
import com.adonis.fluid.block.SmartFluidInterface.SmartFluidInterfaceRenderer;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.adonis.fluid.CreateFluid.REGISTRATE;

public class CFBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateFluid.MOD_ID);

    // 流体接口方块实体
    public static final BlockEntityEntry<FluidInterfaceBlockEntity> FLUID_INTERFACE = REGISTRATE
            .blockEntity("fluid_interface", FluidInterfaceBlockEntity::new)
            .validBlocks(CFBlocks.FLUID_INTERFACE)
            .renderer(() -> FluidInterfaceRenderer::new)
            .register();

    // 智能流体接口方块实体
    public static final BlockEntityEntry<SmartFluidInterfaceBlockEntity> SMART_FLUID_INTERFACE = REGISTRATE
            .blockEntity("smart_fluid_interface", SmartFluidInterfaceBlockEntity::new)
            .validBlocks(CFBlocks.SMART_FLUID_INTERFACE)
            .renderer(() -> SmartFluidInterfaceRenderer::new)
            .register();

    // 铜龙头方块实体
    public static final BlockEntityEntry<CopperTapBlockEntity> COPPER_TAP = REGISTRATE
            .blockEntity("copper_tap", CopperTapBlockEntity::new)
            .validBlocks(CFBlocks.COPPER_TAP)
            .renderer(() -> CopperTapRenderer::new)
            .register();

    // 移液器方块实体
    public static final BlockEntityEntry<PipetteBlockEntity> PIPETTE = REGISTRATE
            .blockEntity("pipette", PipetteBlockEntity::new)
            .validBlocks(CFBlocks.PIPETTE)
            .renderer(() -> PipetteRenderer::new)
            .register();

    // 离心泵方块实体
    public static final BlockEntityEntry<CentrifugalPumpBlockEntity> CENTRIFUGAL_PUMP = REGISTRATE
            .blockEntity("centrifugal_pump", CentrifugalPumpBlockEntity::new)
            .visual(() -> CentrifugalPumpVisual::new, false)
            .validBlocks(CFBlocks.CENTRIFUGAL_PUMP)
            .renderer(() -> CentrifugalPumpRenderer::new)
            .register();

    // 集水器方块实体
    public static final BlockEntityEntry<GutterOutletBlockEntity> GUTTER_OUTLET = REGISTRATE
            .blockEntity("gutter_outlet", GutterOutletBlockEntity::new)
            .validBlocks(CFBlocks.GUTTER_OUTLET)
            .renderer(() -> GutterOutletRenderer::new)
            .register();

    // 智能集水器方块实体
    public static final BlockEntityEntry<SmartGutterOutletBlockEntity> SMART_GUTTER_OUTLET = REGISTRATE
            .blockEntity("smart_gutter_outlet", SmartGutterOutletBlockEntity::new)
            .validBlocks(CFBlocks.SMART_GUTTER_OUTLET)
            .renderer(() -> GutterOutletRenderer::new)
            .register();

    public static void register() {
    }

    public static void registerToEventBus(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // 注册移液器的流体能力
        PipetteBlockEntity.registerCapabilities(event);

        // 注册流体接口的流体能力
        FluidInterfaceBlockEntity.registerCapabilities(event);

        // 注册智能流体接口的流体能力
        SmartFluidInterfaceBlockEntity.registerCapabilities(event);

        // 注册集水器的流体能力
        GutterOutletBlockEntity.registerCapabilities(event);

        // 注册智能集水器的流体能力
        SmartGutterOutletBlockEntity.registerCapabilities(event);
    }
}
