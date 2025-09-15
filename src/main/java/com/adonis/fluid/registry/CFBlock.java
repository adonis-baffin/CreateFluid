package com.adonis.fluid.registry;

import static com.adonis.fluid.registry.CFBlockEntity.CENTRIFUGAL_PUMP;
import static com.simibubi.create.foundation.data.TagGen.axeOrPickaxe;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.block.SmartFluidInterface.SmartFluidInterfaceBlock;
import com.adonis.fluid.block.Pipette.PipetteBlock;
import com.adonis.fluid.block.FluidInterface.FluidInterfaceBlock;
import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlock;
import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlock.Orientation;
import com.adonis.fluid.block.Aqueduct.AqueductBlock;
import com.adonis.fluid.item.PipetteItem;
import com.simibubi.create.foundation.data.ModelGen;
import com.simibubi.create.foundation.data.SharedProperties;
import com.simibubi.create.foundation.data.TagGen;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.MapColor;
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
            .properties(prop -> prop
                    .mapColor(MapColor.TERRACOTTA_YELLOW)
                    .noOcclusion())
            .transform(TagGen.axeOrPickaxe())
            .blockstate((ctx, prov) -> {
                prov.getVariantBuilder(ctx.get())
                        .forAllStates(state -> {
                            return ConfiguredModel.builder()
                                    .modelFile(prov.models().getExistingFile(prov.modLoc("block/pipette")))
                                    .rotationX(state.getValue(PipetteBlock.CEILING) ? 180 : 0)
                                    .build();
                        });
            })
            .transform(CreateFluid.STRESS_CONFIG.setImpact(2.0))
            .item(PipetteItem::new)
            .transform(ModelGen.customItemModel())
            .register();

    // 离心泵注册
    public static final BlockEntry<CentrifugalPumpBlock> CENTRIFUGAL_PUMP = CreateFluid.REGISTRATE
            .block("centrifugal_pump", CentrifugalPumpBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(prop -> prop
                    .mapColor(MapColor.STONE)
                    .sound(SoundType.COPPER)
                    .noOcclusion())
            .transform(TagGen.pickaxeOnly())
            .blockstate((ctx, prov) -> {
                prov.getVariantBuilder(ctx.get())
                        .forAllStates(state -> {
                            Direction facing = state.getValue(CentrifugalPumpBlock.FACING);
                            Orientation orientation = state.getValue(CentrifugalPumpBlock.ORIENTATION);

                            String modelName = orientation == Orientation.VERTICAL
                                    ? "block/centrifugal_pump/block_vertical"
                                    : "block/centrifugal_pump/block";

                            ConfiguredModel.Builder<?> builder = ConfiguredModel.builder()
                                    .modelFile(prov.models().getExistingFile(prov.modLoc(modelName)));

                            // 处理旋转
                            if (orientation == Orientation.VERTICAL) {
                                // 垂直模式：X轴旋转90度，然后根据facing调整Y轴
                                builder.rotationX(90);
                                switch (facing) {
                                    case NORTH:
                                        builder.rotationY(0);
                                        break;
                                    case SOUTH:
                                        builder.rotationY(180);
                                        break;
                                    case WEST:
                                        builder.rotationY(90);
                                        break;
                                    case EAST:
                                        builder.rotationY(270);
                                        break;
                                    default:
                                        break;
                                }
                            } else {
                                // 水平模式：只需要Y轴旋转
                                switch (facing) {
                                    case NORTH:
                                        builder.rotationY(0);
                                        break;
                                    case SOUTH:
                                        builder.rotationY(180);
                                        break;
                                    case WEST:
                                        builder.rotationY(90);
                                        break;
                                    case EAST:
                                        builder.rotationY(270);
                                        break;
                                    default:
                                        break;
                                }
                            }

                            return builder.build();
                        });
            })
            .transform(CreateFluid.STRESS_CONFIG.setImpact(8.0))  // 设置应力影响为8
            .item()
            .transform(ModelGen.customItemModel())
            .register();

    public static void register() {}

    public static void setupRenderLayers() {
        RenderType cutout = RenderType.cutout();
        ItemBlockRenderTypes.setRenderLayer(FLUID_INTERFACE.get(), cutout);
        ItemBlockRenderTypes.setRenderLayer(SMART_FLUID_INTERFACE.get(), cutout);
        ItemBlockRenderTypes.setRenderLayer(PIPETTE.get(), cutout);
        ItemBlockRenderTypes.setRenderLayer(CENTRIFUGAL_PUMP.get(), cutout);
    }
}