package com.adonis.fluid.registry;

import static com.adonis.fluid.CreateFluid.REGISTRATE;
import static com.simibubi.create.api.behaviour.movement.MovementBehaviour.movementBehaviour;
import static com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType.mountedFluidStorage;
import static com.simibubi.create.foundation.data.TagGen.axeOrPickaxe;
import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.config.CFStress;
import com.adonis.fluid.block.CopperSink.CopperSinkBlock;
import com.adonis.fluid.block.CopperSink.CopperSinkMovementBehaviour;
import com.adonis.fluid.block.CopperTap.CopperTapBlock;
import com.adonis.fluid.block.GutterOutlet.GutterOutletBlock;
import com.adonis.fluid.block.GutterOutlet.GutterOutletMovementBehaviour;
import com.adonis.fluid.block.GutterOutlet.SmartGutterOutletBlock;
import com.adonis.fluid.block.RedstoneTripleValve.RedstoneTripleValveBlock;
import com.adonis.fluid.block.RedstoneValve.RedstoneValveBlock;
import com.adonis.fluid.block.SmartFluidInterface.SmartFluidInterfaceBlock;
import com.adonis.fluid.block.Pipette.PipetteBlock;
import com.adonis.fluid.block.FluidInterface.FluidInterfaceBlock;
import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlock;
import com.adonis.fluid.item.PipetteItem;
import com.simibubi.create.content.fluids.PipeAttachmentModel;
import com.simibubi.create.foundation.data.*;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
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

    public static final BlockEntry<GutterOutletBlock> GUTTER_OUTLET = REGISTRATE
            .block("gutter_outlet", GutterOutletBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(prop -> prop
                    .mapColor(MapColor.COLOR_ORANGE)
                    .sound(SoundType.COPPER)
                    .noOcclusion())
            .transform(axeOrPickaxe())
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

    public static final BlockEntry<SmartGutterOutletBlock> SMART_GUTTER_OUTLET = REGISTRATE
            .block("smart_gutter_outlet", SmartGutterOutletBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(prop -> prop
                    .mapColor(MapColor.COLOR_ORANGE)
                    .sound(SoundType.COPPER)
                    .noOcclusion())
            .transform(axeOrPickaxe())
            .transform(mountedFluidStorage(CFMountedStorageTypes.GUTTER_OUTLET))  // Reuse same storage type
            .onRegister(movementBehaviour(new GutterOutletMovementBehaviour()))   // Reuse same movement behaviour
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
            .transform(CFStress.setImpact(2.0))
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
            .transform(pickaxeOnly())
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
            .transform(CFStress.setImpact(8.0))
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

    public static final BlockEntry<CopperSinkBlock> COPPER_SINK = REGISTRATE
            .block("copper_sink", CopperSinkBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(p -> p
                    .mapColor(MapColor.COLOR_ORANGE)
                    .sound(SoundType.COPPER)
                    .strength(3.5f)
                    .noOcclusion())
            .transform(axeOrPickaxe())
            .transform(mountedFluidStorage(CFMountedStorageTypes.COPPER_SINK))
            .onRegister(movementBehaviour(new CopperSinkMovementBehaviour()))  // <-- ADD THIS
            .blockstate((c, p) -> p.horizontalBlock(c.get(),
                    p.models().getExistingFile(p.modLoc("block/copper_sink"))))
            .item()
            .transform(ModelGen.customItemModel("copper_sink"))
            .register();

    public static final BlockEntry<RedstoneValveBlock> REDSTONE_VALVE = REGISTRATE
            .block("redstone_valve", RedstoneValveBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(prop -> prop
                    .mapColor(MapColor.COLOR_ORANGE)
                    .sound(SoundType.COPPER)
                    .noOcclusion())
            .transform(pickaxeOnly())
            .blockstate((c, p) -> {
                p.getVariantBuilder(c.get()).forAllStates(state -> {
                    Direction facing = state.getValue(RedstoneValveBlock.FACING);
                    boolean axisAlongFirst = state.getValue(RedstoneValveBlock.AXIS_ALONG_FIRST_COORDINATE);
                    boolean enabled = state.getValue(RedstoneValveBlock.ENABLED);

                    // 判断管道轴是否为竖直方向（Y轴）
                    Direction.Axis pipeAxis = RedstoneValveBlock.getPipeAxis(state);
                    boolean vertical = pipeAxis == Direction.Axis.Y;

                    String modelPath = "block/redstone_valve/block_"
                            + (vertical ? "vertical" : "horizontal") + "_"
                            + (enabled ? "open" : "closed");

                    // 旋转逻辑与原版流体阀门blockstate JSON一致
                    int xRot = 0;
                    int yRot = 0;

                    if (!axisAlongFirst) {
                        switch (facing) {
                            case DOWN  -> { xRot = 270; yRot = 90; }
                            case UP    -> { xRot = 90;  yRot = 90; }
                            case NORTH -> { yRot = 180; }
                            case SOUTH -> {}
                            case WEST  -> { yRot = 90; }
                            case EAST  -> { yRot = 270; }
                        }
                    } else {
                        switch (facing) {
                            case DOWN  -> { xRot = 270; }
                            case UP    -> { xRot = 90; }
                            case NORTH -> { yRot = 180; }
                            case SOUTH -> {}
                            case WEST  -> { yRot = 90; }
                            case EAST  -> { yRot = 270; }
                        }
                    }

                    return ConfiguredModel.builder()
                            .modelFile(p.models().getExistingFile(p.modLoc(modelPath)))
                            .rotationX(xRot)
                            .rotationY(yRot)
                            .build();
                });
            })
            .onRegister(CreateRegistrate.blockModel(() -> PipeAttachmentModel::withAO))
            .item()
            .transform(ModelGen.customItemModel())
            .register();

    public static final BlockEntry<RedstoneTripleValveBlock> REDSTONE_TRIPLE_VALVE = REGISTRATE
            .block("redstone_triple_valve", RedstoneTripleValveBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(prop -> prop
                    .mapColor(MapColor.COLOR_ORANGE)
                    .sound(SoundType.COPPER)
                    .noOcclusion())
            .transform(TagGen.pickaxeOnly())
            .blockstate((c, p) -> {
                p.getVariantBuilder(c.get()).forAllStates(state -> {
                    Direction facing = state.getValue(RedstoneTripleValveBlock.FACING);
                    boolean axisAlongFirst = state.getValue(RedstoneTripleValveBlock.AXIS_ALONG_FIRST_COORDINATE);
                    boolean powered = state.getValue(RedstoneTripleValveBlock.POWERED);

                    Direction.Axis crossAxis = RedstoneTripleValveBlock.getCrossAxis(state);
                    boolean vertical = crossAxis == Direction.Axis.Y;

                    // open = 无信号(powered=false), closed = 有信号(powered=true)
                    String modelPath = "block/redstone_triple_valve/block_"
                            + (vertical ? "vertical" : "horizontal") + "_"
                            + (powered ? "closed" : "open");

                    // 模型默认朝向:
                    //   horizontal: 固定口朝 DOWN(Y-), 横杆沿 X
                    //   vertical:   固定口朝 WEST(X-), 横杆沿 Y
                    int xRot = 0;
                    int yRot = 0;

                    switch (facing) {
                        case DOWN -> {
                            // horizontal 默认就是固定口DOWN
                            // AAF=false → crossX → horizontal, 无旋转
                            // AAF=true  → crossZ → horizontal, yRot=90 (X→Z)
                            if (crossAxis == Direction.Axis.Z) yRot = 90;
                        }
                        case UP -> {
                            // 翻转 DOWN→UP
                            xRot = 180;
                            if (crossAxis == Direction.Axis.Z) yRot = 90;
                        }
                        case WEST -> {
                            // AAF=false → crossY → vertical 默认就是固定口WEST
                            // AAF=true  → crossZ → horizontal, xRot=90 yRot=90
                            if (!vertical) { xRot = 90; yRot = 90; }
                        }
                        case EAST -> {
                            // AAF=false → crossY → vertical, yRot=180 (WEST→EAST)
                            // AAF=true  → crossZ → horizontal, xRot=270 yRot=270
                            if (vertical) { yRot = 180; }
                            else { xRot = 270; yRot = 270; }
                        }
                        case SOUTH -> {
                            // AAF=false → crossX → horizontal, xRot=90 (DOWN→SOUTH)
                            // AAF=true  → crossY → vertical, yRot=270 (WEST→SOUTH)
                            if (!vertical) { xRot = 90; }
                            else { yRot = 270; }
                        }
                        case NORTH -> {
                            // AAF=false → crossX → horizontal, xRot=270 (DOWN→NORTH)
                            // AAF=true  → crossY → vertical, yRot=90 (WEST→NORTH)
                            if (!vertical) { xRot = 270; }
                            else { yRot = 90; }
                        }
                    }

                    return ConfiguredModel.builder()
                            .modelFile(p.models().getExistingFile(p.modLoc(modelPath)))
                            .rotationX(xRot)
                            .rotationY(yRot)
                            .build();
                });
            })
            .onRegister(CreateRegistrate.blockModel(() -> PipeAttachmentModel::withAO))
            .item()
            .model((ctx, prov) -> prov.withExistingParent(ctx.getName(),
                    prov.modLoc("block/redstone_triple_valve/item")))
            .build()
            .register();

    public static void register() {
        // 这个方法是空的，仅用于触发静态字段的初始化
    }
}