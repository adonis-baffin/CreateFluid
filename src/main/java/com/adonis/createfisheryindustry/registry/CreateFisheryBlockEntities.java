package com.adonis.createfisheryindustry.registry;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.block.MeshTrap.MeshTrapBlockEntity;
import com.adonis.createfisheryindustry.block.TrapNozzle.TrapNozzleBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class CreateFisheryBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CreateFisheryMod.MODID);

    public static final RegistryObject<BlockEntityType<MeshTrapBlockEntity>> MESH_TRAP = BLOCK_ENTITIES.register("mesh_trap",
            () -> BlockEntityType.Builder.of(MeshTrapBlockEntity::new, CreateFisheryBlocks.MESH_TRAP.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<TrapNozzleBlockEntity>> TRAP_NOZZLE = BLOCK_ENTITIES.register("trap_nozzle",
            () -> BlockEntityType.Builder.of(
                            (pos, state) -> new TrapNozzleBlockEntity(CreateFisheryBlockEntities.TRAP_NOZZLE.get(), pos, state),
                            CreateFisheryBlocks.TRAP_NOZZLE.get())
                    .build(null));

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}