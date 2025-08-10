package com.adonis.fluid.registry;

import static com.simibubi.create.foundation.data.TagGen.axeOrPickaxe;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.block.FrameTrap.FrameTrapBlock;
import com.adonis.fluid.block.FrameTrap.FrameTrapMovementBehaviour;
import com.adonis.fluid.block.MeshTrap.MeshTrapBlock;
import com.adonis.fluid.block.SmartFluidInterface.SmartFluidInterfaceBlock;
import com.adonis.fluid.block.SmartMesh.SmartMeshBlock;
import com.adonis.fluid.block.TrapNozzle.TrapNozzleBlock;
import com.adonis.fluid.block.SmartNozzle.SmartNozzleBlock;
import com.adonis.fluid.block.Pipette.PipetteBlock;
import com.adonis.fluid.block.FluidInterface.FluidInterfaceBlock; // 添加这个导入
import com.adonis.fluid.config.CFConfig;
import com.adonis.fluid.item.PipetteItem;
import com.simibubi.create.AllTags;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.SoundType;
import net.minecraftforge.client.model.generators.ConfiguredModel;

public class CFBlock {

    public static final BlockEntry<FrameTrapBlock> FRAME_TRAP = CreateFluid.REGISTRATE
            .block("frame_trap", FrameTrapBlock::new)
            .initialProperties(SharedProperties::wooden)
            .properties(prop -> prop
                    .mapColor(DyeColor.BROWN)
                    .sound(SoundType.SCAFFOLDING)
                    .noOcclusion())
            .transform(axeOrPickaxe())
            .tag(AllTags.AllBlockTags.WINDMILL_SAILS.tag)
            .onRegister(block -> FrameTrapMovementBehaviour.REGISTRY.register(block, new FrameTrapMovementBehaviour()))
            .blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(), prov.models().cubeAll(ctx.getName(), prov.modLoc("block/frame_trap"))))
            .simpleItem()
            .register();

    public static final BlockEntry<FluidInterfaceBlock> FLUID_INTERFACE = CreateFluid.REGISTRATE
            .block("fluid_interface", FluidInterfaceBlock::new)
            .initialProperties(SharedProperties::wooden)
            .properties(prop -> prop
                    .mapColor(DyeColor.BROWN)
                    .sound(SoundType.SCAFFOLDING)
                    .noOcclusion())
            .transform(axeOrPickaxe())
            .blockstate((ctx, prov) -> {
                prov.getVariantBuilder(ctx.get())
                        .forAllStates(state -> {
                            Direction dir = state.getValue(FluidInterfaceBlock.FACING);
                            int yRot = (int) dir.toYRot();
                            return ConfiguredModel.builder()
                                    .modelFile(prov.models().getExistingFile(prov.modLoc("block/fluid_interface")))
                                    .rotationY(yRot)
                                    .build();
                        });
            })
            .simpleItem()
            .register();

    public static final BlockEntry<SmartFluidInterfaceBlock> SMART_FLUID_INTERFACE = CreateFluid.REGISTRATE
            .block("smart_fluid_interface", SmartFluidInterfaceBlock::new)
            .initialProperties(SharedProperties::wooden)
            .properties(prop -> prop
                    .mapColor(DyeColor.GRAY)
                    .sound(SoundType.METAL)
                    .noOcclusion())
            .transform(axeOrPickaxe())
            .blockstate((ctx, prov) -> {
                prov.getVariantBuilder(ctx.get())
                        .forAllStates(state -> {
                            Direction dir = state.getValue(SmartFluidInterfaceBlock.FACING);
                            int yRot = (int) dir.toYRot();
                            return ConfiguredModel.builder()
                                    .modelFile(prov.models().getExistingFile(prov.modLoc("block/smart_fluid_interface")))
                                    .rotationY(yRot)
                                    .build();
                        });
            })
            .simpleItem()
            .register();

    public static final BlockEntry<MeshTrapBlock> MESH_TRAP = CreateFluid.REGISTRATE
            .block("mesh_trap", MeshTrapBlock::new)
            .initialProperties(SharedProperties::wooden)
            .properties(prop -> prop
                    .mapColor(DyeColor.WHITE)
                    .sound(SoundType.BAMBOO)
                    .noOcclusion())
            .transform(axeOrPickaxe())
            .blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(), prov.models().cubeAll(ctx.getName(), prov.modLoc("block/mesh_trap"))))
            .simpleItem()
            .register();

    public static final BlockEntry<TrapNozzleBlock> TRAP_NOZZLE = CreateFluid.REGISTRATE
            .block("trap_nozzle", TrapNozzleBlock::new)
            .initialProperties(SharedProperties::wooden)
            .properties(prop -> prop
                    .mapColor(DyeColor.WHITE)
                    .sound(SoundType.BAMBOO)
                    .noOcclusion())
            .transform(axeOrPickaxe())
            .blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(), prov.models().cubeAll(ctx.getName(), prov.modLoc("block/trap_nozzle"))))
            .simpleItem()
            .register();

    public static final BlockEntry<SmartNozzleBlock> SMART_NOZZLE = CreateFluid.REGISTRATE
            .block("smart_nozzle", SmartNozzleBlock::new)
            .initialProperties(SharedProperties::wooden)
            .properties(prop -> prop
                    .mapColor(DyeColor.GRAY)
                    .sound(SoundType.NETHER_WOOD)
                    .noOcclusion())
            .transform(axeOrPickaxe())
            .blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(), prov.models().cubeAll(ctx.getName(), prov.modLoc("block/smart_nozzle"))))
            .simpleItem()
            .register();

    public static final BlockEntry<SmartMeshBlock> SMART_MESH = CreateFluid.REGISTRATE
            .block("smart_mesh", SmartMeshBlock::new)
            .initialProperties(SharedProperties::wooden)
            .properties(prop -> prop
                    .mapColor(DyeColor.GRAY)
                    .sound(SoundType.NETHER_WOOD)
                    .noOcclusion())
            .transform(axeOrPickaxe())
            .blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(), prov.models().cubeAll(ctx.getName(), prov.modLoc("block/smart_mesh"))))
            .simpleItem()
            .register();

    public static final BlockEntry<PipetteBlock> PIPETTE = CreateFluid.REGISTRATE
            .block("pipette", PipetteBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(prop -> prop.mapColor(DyeColor.YELLOW))
            .transform(axeOrPickaxe())
            .blockstate((ctx, prov) -> {
                prov.simpleBlock(ctx.get());
            })
            .transform(CreateFluid.STRESS_CONFIG.setImpact(2.0))
            .item(PipetteItem::new)
            .build()
            .register();

    public static void register() {}

    public static void setupRenderLayers() {
        RenderType cutout = RenderType.cutout();
        ItemBlockRenderTypes.setRenderLayer(FRAME_TRAP.get(), cutout);
        ItemBlockRenderTypes.setRenderLayer(FLUID_INTERFACE.get(), cutout); // 添加这行
        ItemBlockRenderTypes.setRenderLayer(SMART_FLUID_INTERFACE.get(), cutout);
        ItemBlockRenderTypes.setRenderLayer(MESH_TRAP.get(), cutout);
        ItemBlockRenderTypes.setRenderLayer(TRAP_NOZZLE.get(), cutout);
        ItemBlockRenderTypes.setRenderLayer(SMART_NOZZLE.get(), cutout);
        ItemBlockRenderTypes.setRenderLayer(SMART_MESH.get(), cutout);
        ItemBlockRenderTypes.setRenderLayer(PIPETTE.get(), cutout);
    }
}