package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.block.CopperTap.CopperTapBlockEntity;
import com.adonis.fluid.block.Pipette.PipetteBlockEntity;
import com.adonis.fluid.block.FluidInterface.FluidInterfaceBlockEntity;
import com.adonis.fluid.block.SmartFluidInterface.SmartFluidInterfaceBlockEntity;
import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlockEntity;
import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpRenderer;
import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpVisual;
import com.adonis.fluid.block.Aqueduct.AqueductBlockEntity;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class CFBlockEntity {
    // 保留原有的注册方式用于其他方块实体
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CreateFluid.MODID);

    public static final RegistryObject<BlockEntityType<PipetteBlockEntity>> PIPETTE =
            BLOCK_ENTITIES.register("pipette", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new PipetteBlockEntity(CFBlockEntity.PIPETTE.get(), pos, state),
                    CFBlock.PIPETTE.get()).build(null));

    public static final RegistryObject<BlockEntityType<FluidInterfaceBlockEntity>> FLUID_INTERFACE =
            BLOCK_ENTITIES.register("fluid_interface", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new FluidInterfaceBlockEntity(CFBlockEntity.FLUID_INTERFACE.get(), pos, state),
                    CFBlock.FLUID_INTERFACE.get()).build(null));

    public static final RegistryObject<BlockEntityType<SmartFluidInterfaceBlockEntity>> SMART_FLUID_INTERFACE =
            BLOCK_ENTITIES.register("smart_fluid_interface", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new SmartFluidInterfaceBlockEntity(CFBlockEntity.SMART_FLUID_INTERFACE.get(), pos, state),
                    CFBlock.SMART_FLUID_INTERFACE.get()).build(null));

    public static final RegistryObject<BlockEntityType<AqueductBlockEntity>> AQUEDUCT =
            BLOCK_ENTITIES.register("aqueduct", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new AqueductBlockEntity(CFBlockEntity.AQUEDUCT.get(), pos, state),
                    CFBlock.AQUEDUCT.get()).build(null));

    // 使用CreateRegistrate方式注册离心泵，以支持Visual
    public static final BlockEntityEntry<CentrifugalPumpBlockEntity> CENTRIFUGAL_PUMP_ENTRY =
            CreateFluid.REGISTRATE
                    .blockEntity("centrifugal_pump", CentrifugalPumpBlockEntity::new)
                    .visual(() -> CentrifugalPumpVisual::new, false)
                    .validBlocks(CFBlock.CENTRIFUGAL_PUMP)
                    .renderer(() -> CentrifugalPumpRenderer::new)
                    .register();

    public static final RegistryObject<BlockEntityType<CopperTapBlockEntity>> COPPER_TAP =
            BLOCK_ENTITIES.register("copper_tap", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CopperTapBlockEntity(CFBlockEntity.COPPER_TAP.get(), pos, state),
                    CFBlock.COPPER_TAP.get()).build(null));

    // 为了保持兼容性，提供一个RegistryObject访问器
    public static final RegistryObject<BlockEntityType<CentrifugalPumpBlockEntity>> CENTRIFUGAL_PUMP =
            RegistryObject.create(CreateFluid.asResource("centrifugal_pump"), ForgeRegistries.BLOCK_ENTITY_TYPES);

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}