package com.adonis.fluid.registry;

import static com.adonis.fluid.CreateFluid.REGISTRATE;
import static com.simibubi.create.foundation.data.TagGen.axeOrPickaxe;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.block.CopperTap.CopperTapBlock;
import com.adonis.fluid.block.CopperTap.CopperTapProxyBlock;
import com.adonis.fluid.block.SmartFluidInterface.SmartFluidInterfaceBlock;
import com.adonis.fluid.block.Pipette.PipetteBlock;
import com.adonis.fluid.block.FluidInterface.FluidInterfaceBlock;
import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlock;
import com.adonis.fluid.block.Aqueduct.AqueductBlock;
import com.adonis.fluid.item.PipetteItem;
import com.simibubi.create.foundation.data.ModelGen;
import com.simibubi.create.foundation.data.SharedProperties;
import com.simibubi.create.foundation.data.TagGen;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CFBlock {
    private static final Logger LOGGER = LoggerFactory.getLogger(CFBlock.class);

    // 流体接口注册
    public static final BlockEntry<FluidInterfaceBlock> FLUID_INTERFACE = REGISTRATE
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
    public static final BlockEntry<SmartFluidInterfaceBlock> SMART_FLUID_INTERFACE = REGISTRATE
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

    public static final BlockEntry<AqueductBlock> AQUEDUCT = REGISTRATE
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

    public static final BlockEntry<PipetteBlock> PIPETTE = REGISTRATE
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

    public static final BlockEntry<CentrifugalPumpBlock> CENTRIFUGAL_PUMP = REGISTRATE
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
                            AttachFace face = state.getValue(CentrifugalPumpBlock.FACE);
                            boolean encased = state.getValue(CentrifugalPumpBlock.ENCASED);

                            ConfiguredModel.Builder<?> builder = ConfiguredModel.builder();
                            String modelPrefix = encased ? "encased_pump" : "block";

                            if (face == AttachFace.WALL) {
                                builder.modelFile(prov.models().getExistingFile(
                                        prov.modLoc("block/centrifugal_pump/" + modelPrefix + "_vertical")));
                                switch (facing) {
                                    case NORTH: builder.rotationY(0); break;
                                    case SOUTH: builder.rotationY(180); break;
                                    case WEST: builder.rotationY(270); break;
                                    case EAST: builder.rotationY(90); break;
                                }
                            } else if (face == AttachFace.CEILING) {
                                builder.modelFile(prov.models().getExistingFile(
                                        prov.modLoc("block/centrifugal_pump/" + modelPrefix)));
                                builder.rotationX(180);
                                switch (facing) {
                                    case NORTH: builder.rotationY(180); break;
                                    case SOUTH: builder.rotationY(0); break;
                                    case WEST: builder.rotationY(90); break;
                                    case EAST: builder.rotationY(270); break;
                                }
                            } else { // AttachFace.FLOOR
                                builder.modelFile(prov.models().getExistingFile(
                                        prov.modLoc("block/centrifugal_pump/" + modelPrefix)));
                                switch (facing) {
                                    case NORTH: builder.rotationY(0); break;
                                    case SOUTH: builder.rotationY(180); break;
                                    case WEST: builder.rotationY(270); break;
                                    case EAST: builder.rotationY(90); break;
                                }
                            }
                            return builder.build();
                        });
            })
            .transform(CreateFluid.STRESS_CONFIG.setImpact(8.0))
            // 连接纹理功能已禁用 - 如需重新启用，取消下面的注释
            // .onRegister(CreateRegistrate.connectedTextures(() -> new CentrifugalPumpCTBehaviour()))
            // .onRegister(CreateRegistrate.casingConnectivity((block, cc) ->
            //         cc.make(block, AllSpriteShifts.COPPER_CASING, (state, face) -> {
            //             if (!state.getValue(CentrifugalPumpBlock.ENCASED)) {
            //                 return false;
            //             }
            //
            //             Direction primary = CentrifugalPumpBlock.getPrimaryFluidDirection(state);
            //             Direction secondary = CentrifugalPumpBlock.getSecondaryFluidDirection(state);
            //             Direction shaft = CentrifugalPumpBlock.getShaftDirection(state);
            //
            //             return face != primary && face != secondary && face != shaft;
            //         })
            // ))
            .item()
            .transform(ModelGen.customItemModel())
            .register();

    // 铜龙头注册
    public static final BlockEntry<CopperTapBlock> COPPER_TAP = REGISTRATE
            .block("copper_tap", CopperTapBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(prop -> prop
                    .mapColor(MapColor.COLOR_ORANGE)
                    .sound(SoundType.COPPER)
                    .noOcclusion())
            .transform(axeOrPickaxe())
            .blockstate((ctx, prov) -> {
                prov.getVariantBuilder(ctx.get())
                        .forAllStates(state -> {
                            Direction dir = state.getValue(CopperTapBlock.FACING);
                            int yRot = (int) dir.toYRot();
                            return ConfiguredModel.builder()
                                    .modelFile(prov.models().getExistingFile(prov.modLoc("block/copper_tap")))
                                    .rotationY(yRot)
                                    .build();
                        });
            })
            .item()
            .model((ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.modLoc("block/copper_tap")))
            .build()
            .register();

    // 在其他方块注册后添加
    public static final BlockEntry<CopperTapProxyBlock> COPPER_TAP_PROXY = REGISTRATE
            .block("copper_tap_proxy", CopperTapProxyBlock::new)
            .initialProperties(() -> Blocks.AIR)
            .properties(prop -> prop
                    .noCollission()
                    .noOcclusion()
                    .noLootTable()
                    .replaceable())
            .blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(),
                    prov.models().getBuilder(ctx.getName()).texture("particle", "minecraft:block/air")))
            .register();

    public static void register() {
        // 这个方法是空的，仅用于触发静态字段的初始化
    }
}