package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlock;
import com.adonis.fluid.block.CopperSink.CopperSinkBlock;
import com.adonis.fluid.block.CopperSink.CopperSinkMovementBehaviour;
import com.adonis.fluid.block.CopperTap.CopperTapBlock;
import com.adonis.fluid.block.RedstoneValve.RedstoneValveBlock;
import com.adonis.fluid.block.RedstoneTripleValve.RedstoneTripleValveBlock;
import com.adonis.fluid.block.FluidInterface.FluidInterfaceBlock;
import com.adonis.fluid.block.GutterOutlet.GutterOutletBlock;
import com.adonis.fluid.block.GutterOutlet.GutterOutletMovementBehaviour;
import com.adonis.fluid.block.GutterOutlet.SmartGutterOutletBlock;
import com.adonis.fluid.block.Pipette.PipetteBlock;
import com.adonis.fluid.block.SmartFluidInterface.SmartFluidInterfaceBlock;
import com.adonis.fluid.block.CanFiller.CanFillerBlock;
import com.adonis.fluid.block.CommunicatingVessel.CommunicatingVesselBlock;
import com.adonis.fluid.block.FluidAtomizer.FluidAtomizerBlock;
import com.adonis.fluid.block.QuicksandBlock;
import com.simibubi.create.foundation.data.ModelGen;
import com.simibubi.create.foundation.data.SharedProperties;
import com.simibubi.create.foundation.data.TagGen;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.core.Direction;
import com.adonis.fluid.item.PipetteItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;

import static com.adonis.fluid.CreateFluid.REGISTRATE;
import static com.simibubi.create.api.behaviour.movement.MovementBehaviour.movementBehaviour;
import static com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType.mountedFluidStorage;

public class CFBlocks {

    // 流体接口注册
    public static final BlockEntry<FluidInterfaceBlock> FLUID_INTERFACE = REGISTRATE
            .block("fluid_interface", FluidInterfaceBlock::new)
            .initialProperties(SharedProperties::wooden)
            .properties(prop -> prop
                    .mapColor(DyeColor.BROWN)
                    .sound(SoundType.SCAFFOLDING)
                    .noOcclusion())
            .transform(TagGen.axeOrPickaxe())
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
            .item()
            .model((ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.modLoc("block/fluid_interface")))
            .build()
            .register();

    // 智能流体接口注册
    public static final BlockEntry<SmartFluidInterfaceBlock> SMART_FLUID_INTERFACE = REGISTRATE
            .block("smart_fluid_interface", SmartFluidInterfaceBlock::new)
            .initialProperties(SharedProperties::wooden)
            .properties(prop -> prop
                    .mapColor(DyeColor.GRAY)
                    .sound(SoundType.METAL)
                    .noOcclusion())
            .transform(TagGen.axeOrPickaxe())
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
            .item()
            .model((ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.modLoc("block/smart_fluid_interface")))
            .build()
            .register();

    // 移液器注册
    public static final BlockEntry<PipetteBlock> PIPETTE = REGISTRATE
            .block("pipette", PipetteBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(prop -> prop
                    .mapColor(MapColor.TERRACOTTA_YELLOW)
                    .noOcclusion())
            .transform(CreateFluid.STRESS_CONFIG.setImpact(2.0))
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
            .item(PipetteItem::new)
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
            .transform(TagGen.axeOrPickaxe())
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

    // 离心泵注册
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
                            String modelName;

                            if (face == AttachFace.WALL) {
                                // 垂直模式
                                modelName = encased ? "encased_pump_vertical" : "block_vertical";
                                builder.modelFile(prov.models().getExistingFile(
                                        prov.modLoc("block/centrifugal_pump/" + modelName)));
                                switch (facing) {
                                    case NORTH: builder.rotationY(0); break;
                                    case SOUTH: builder.rotationY(180); break;
                                    case WEST: builder.rotationY(270); break;
                                    case EAST: builder.rotationY(90); break;
                                }
                            } else if (face == AttachFace.CEILING) {
                                // 天花板模式
                                if (encased) {
                                    boolean isNorthSouth = (facing == Direction.NORTH || facing == Direction.SOUTH);
                                    modelName = isNorthSouth ? "encased_pump_ns" : "encased_pump_ew";
                                } else {
                                    modelName = "block";
                                }
                                builder.modelFile(prov.models().getExistingFile(
                                        prov.modLoc("block/centrifugal_pump/" + modelName)));
                                builder.rotationX(180);
                                switch (facing) {
                                    case NORTH: builder.rotationY(180); break;
                                    case SOUTH: builder.rotationY(0); break;
                                    case WEST: builder.rotationY(90); break;
                                    case EAST: builder.rotationY(270); break;
                                }
                            } else { // FLOOR
                                // 地板模式
                                if (encased) {
                                    boolean isNorthSouth = (facing == Direction.NORTH || facing == Direction.SOUTH);
                                    modelName = isNorthSouth ? "encased_pump_ns" : "encased_pump_ew";
                                } else {
                                    modelName = "block";
                                }
                                builder.modelFile(prov.models().getExistingFile(
                                        prov.modLoc("block/centrifugal_pump/" + modelName)));
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
            .item()
            .transform(ModelGen.customItemModel())
            .register();

    // 集水器注册
    public static final BlockEntry<GutterOutletBlock> GUTTER_OUTLET = REGISTRATE
            .block("gutter_outlet", GutterOutletBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(prop -> prop
                    .mapColor(MapColor.COLOR_ORANGE)
                    .sound(SoundType.COPPER)
                    .noOcclusion())
            .transform(TagGen.axeOrPickaxe())
            .transform(mountedFluidStorage(CFMountedStorageTypes.GUTTER_OUTLET))
            .onRegister(movementBehaviour(new GutterOutletMovementBehaviour()))
            .blockstate((ctx, prov) -> {
                prov.getVariantBuilder(ctx.get())
                        .forAllStates(state -> {
                            Direction facing = state.getValue(GutterOutletBlock.FACING);
                            int yRot = (int) facing.toYRot();
                            return ConfiguredModel.builder()
                                    .modelFile(prov.models().getExistingFile(prov.modLoc("block/gutter_outlet")))
                                    .rotationY(yRot)
                                    .build();
                        });
            })
            .item()
            .model((ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.modLoc("block/gutter_outlet")))
            .build()
            .register();

    // 智能集水器注册
    public static final BlockEntry<SmartGutterOutletBlock> SMART_GUTTER_OUTLET = REGISTRATE
            .block("smart_gutter_outlet", SmartGutterOutletBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(prop -> prop
                    .mapColor(MapColor.COLOR_ORANGE)
                    .sound(SoundType.COPPER)
                    .noOcclusion())
            .transform(TagGen.axeOrPickaxe())
            .transform(mountedFluidStorage(CFMountedStorageTypes.GUTTER_OUTLET))
            .onRegister(movementBehaviour(new GutterOutletMovementBehaviour()))
            .blockstate((ctx, prov) -> {
                prov.getVariantBuilder(ctx.get())
                        .forAllStates(state -> {
                            Direction facing = state.getValue(SmartGutterOutletBlock.FACING);
                            int yRot = (int) facing.toYRot();
                            return ConfiguredModel.builder()
                                    .modelFile(prov.models().getExistingFile(prov.modLoc("block/smart_gutter_outlet")))
                                    .rotationY(yRot)
                                    .build();
                        });
            })
            .item()
            .model((ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.modLoc("block/smart_gutter_outlet")))
            .build()
            .register();

    // 铜水槽注册
    public static final BlockEntry<CopperSinkBlock> COPPER_SINK = REGISTRATE
            .block("copper_sink", CopperSinkBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(prop -> prop
                    .mapColor(MapColor.COLOR_ORANGE)
                    .sound(SoundType.COPPER)
                    .noOcclusion())
            .transform(TagGen.pickaxeOnly())
            .transform(mountedFluidStorage(CFMountedStorageTypes.COPPER_SINK))
            .onRegister(movementBehaviour(new CopperSinkMovementBehaviour()))
            .blockstate((ctx, prov) -> {
                prov.getVariantBuilder(ctx.get())
                        .forAllStates(state -> {
                            Direction facing = state.getValue(CopperSinkBlock.FACING);
                            int yRot = (int) facing.toYRot();
                            return ConfiguredModel.builder()
                                    .modelFile(prov.models().getExistingFile(prov.modLoc("block/copper_sink")))
                                    .rotationY(yRot)
                                    .build();
                        });
            })
            .item()
            .model((ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.modLoc("block/copper_sink")))
            .build()
            .register();

    // 红石阀门注册
    public static final BlockEntry<RedstoneValveBlock> REDSTONE_VALVE = REGISTRATE
            .block("redstone_valve", RedstoneValveBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(prop -> prop
                    .mapColor(MapColor.COLOR_ORANGE)
                    .sound(SoundType.COPPER)
                    .noOcclusion())
            .transform(TagGen.pickaxeOnly())
            .blockstate((ctx, prov) -> {
                // 使用已有的 blockstate 文件
            })
            .item()
            .model((ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.modLoc("item/redstone_valve")))
            .build()
            .register();

    // 红石三通阀门注册
    public static final BlockEntry<RedstoneTripleValveBlock> REDSTONE_TRIPLE_VALVE = REGISTRATE
            .block("redstone_triple_valve", RedstoneTripleValveBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(prop -> prop
                    .mapColor(MapColor.COLOR_ORANGE)
                    .sound(SoundType.COPPER)
                    .noOcclusion())
            .transform(TagGen.pickaxeOnly())
            .blockstate((ctx, prov) -> {
                // 使用已有的 blockstate 文件
            })
            .item()
            .model((ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.modLoc("item/redstone_triple_valve")))
            .build()
            .register();

    // 装罐机
    public static final BlockEntry<CanFillerBlock> CAN_FILLER = REGISTRATE
            .block("can_filler", CanFillerBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(prop -> prop
                    .mapColor(MapColor.TERRACOTTA_BLUE)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
                    .isRedstoneConductor(($1, $2, $3) -> false))
            .transform(TagGen.pickaxeOnly())
            .addLayer(() -> net.minecraft.client.renderer.RenderType::cutoutMipped)
            .item()
            .transform(ModelGen.customItemModel())
            .register();

    // 流体连通器
    public static final BlockEntry<CommunicatingVesselBlock> COMMUNICATING_VESSEL = REGISTRATE
            .block("communicating_vessel", CommunicatingVesselBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(prop -> prop
                    .mapColor(MapColor.STONE)
                    .sound(SoundType.METAL)
                    .noOcclusion())
            .transform(TagGen.pickaxeOnly())
            .blockstate((ctx, prov) -> {
                prov.getVariantBuilder(ctx.get())
                        .forAllStates(state -> {
                            Direction.Axis axis = state.getValue(CommunicatingVesselBlock.AXIS);
                            int rotX = 0;
                            int rotY = 0;
                            if (axis == Direction.Axis.Y) {
                                rotX = 90;
                            } else if (axis == Direction.Axis.X) {
                                rotY = 90;
                            }
                            return ConfiguredModel.builder()
                                    .modelFile(prov.models().getExistingFile(
                                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "block/smart_fluid_pipe/block")))
                                    .rotationX(rotX)
                                    .rotationY(rotY)
                                    .build();
                        });
            })
            .item()
            .model((ctx, prov) -> prov.withExistingParent(ctx.getName(),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "block/smart_fluid_pipe/item")))
            .build()
            .register();

    // 流体雾化器（测试方块）
    public static final BlockEntry<FluidAtomizerBlock> FLUID_ATOMIZER = REGISTRATE
            .block("fluid_atomizer", FluidAtomizerBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(prop -> prop
                    .mapColor(MapColor.STONE)
                    .sound(SoundType.METAL)
                    .noOcclusion())
            .transform(TagGen.pickaxeOnly())
            .transform(CreateFluid.STRESS_CONFIG.setImpact(4.0))
            .blockstate((ctx, prov) -> {
                prov.getVariantBuilder(ctx.get())
                        .forAllStates(state -> {
                            Direction dir = state.getValue(FluidAtomizerBlock.FACING);
                            ConfiguredModel.Builder<?> builder = ConfiguredModel.builder()
                                    .modelFile(prov.models().getExistingFile(prov.modLoc("block/fluid_atomizer")));
                            switch (dir) {
                                case DOWN -> builder.rotationX(90);
                                case EAST -> builder.rotationY(90);
                                case NORTH -> {
                                }
                                case SOUTH -> builder.rotationY(180);
                                case UP -> builder.rotationX(270);
                                case WEST -> builder.rotationY(270);
                            }
                            return builder.build();
                        });
            })
            .item()
            .model((ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.modLoc("block/fluid_atomizer")))
            .build()
            .register();

    // 流沙方块
    public static final BlockEntry<QuicksandBlock> QUICKSAND = REGISTRATE
            .block("quicksand", QuicksandBlock::new)
            .initialProperties(() -> net.minecraft.world.level.block.Blocks.SAND)
            .properties(prop -> prop.strength(0.25f))
            .simpleItem()
            .register();

    public static void register() {
        // 静态初始化触发
    }
}
