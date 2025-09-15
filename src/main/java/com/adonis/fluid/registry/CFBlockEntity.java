package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.block.Pipette.PipetteBlockEntity;
import com.adonis.fluid.block.FluidInterface.FluidInterfaceBlockEntity;
import com.adonis.fluid.block.SmartFluidInterface.SmartFluidInterfaceBlockEntity;
import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlockEntity;
import com.adonis.fluid.block.Aqueduct.AqueductBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class CFBlockEntity {
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

    // 新增离心泵的注册
    public static final RegistryObject<BlockEntityType<CentrifugalPumpBlockEntity>> CENTRIFUGAL_PUMP =
            BLOCK_ENTITIES.register("centrifugal_pump", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CentrifugalPumpBlockEntity(CFBlockEntity.CENTRIFUGAL_PUMP.get(), pos, state),
                    CFBlock.CENTRIFUGAL_PUMP.get()).build(null));

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}