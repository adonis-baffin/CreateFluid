package com.adonis.fluid.registry;

import static com.simibubi.create.foundation.data.TagGen.axeOrPickaxe;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.block.SmartFluidInterface.SmartFluidInterfaceBlock;
import com.adonis.fluid.block.Pipette.PipetteBlock;
import com.adonis.fluid.block.FluidInterface.FluidInterfaceBlock; // 添加这个导入
import com.adonis.fluid.block.aqueduct.AqueductBlock;
import com.adonis.fluid.item.PipetteItem;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.SoundType;
import net.minecraftforge.client.model.generators.ConfiguredModel;

public class CFBlock {

    // 流体接口注册
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

    // 智能流体接口注册
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

    public static final BlockEntry<AqueductBlock> AQUEDUCT = CreateFluid.REGISTRATE
            .block("aqueduct", AqueductBlock::new)
            .initialProperties(SharedProperties::stone)
            .properties(p -> p
                    .mapColor(DyeColor.GRAY)
                    .sound(SoundType.STONE)
                    .noOcclusion())
            .transform(axeOrPickaxe())
            .blockstate((ctx, prov) -> {
                prov.getVariantBuilder(ctx.get())
                        .forAllStates(state -> {
                            Direction dir = state.getValue(AqueductBlock.FACING);
                            int yRot = (int) dir.toYRot();
                            return ConfiguredModel.builder()
                                    .modelFile(prov.models().getExistingFile(prov.modLoc("block/aqueduct")))
                                    .rotationY(yRot)
                                    .build();
                        });
            })
            .item()
            .model((ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.modLoc("block/aqueduct")))
            .build()
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
        ItemBlockRenderTypes.setRenderLayer(FLUID_INTERFACE.get(), cutout);
        ItemBlockRenderTypes.setRenderLayer(SMART_FLUID_INTERFACE.get(), cutout);
        ItemBlockRenderTypes.setRenderLayer(PIPETTE.get(), cutout);
    }
}