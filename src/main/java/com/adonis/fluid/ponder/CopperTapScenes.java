package com.adonis.fluid.ponder;

import com.adonis.fluid.block.CopperTap.CopperTapBlock;
import com.simibubi.create.content.fluids.FluidFX;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.catnip.math.VecHelper;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class CopperTapScenes {

    public static void tap(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("copper_tap", "Using Copper Taps");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        // 定义位置 - 完全复制1.20.1的布局
        BlockPos tap1Pos = util.grid().at(4, 2, 2);
        BlockPos tank1BottomPos = util.grid().at(4, 1, 3);
        BlockPos tank1TopPos = util.grid().at(4, 2, 3);
        BlockPos cauldronPos = util.grid().at(4, 1, 2);

        BlockPos tap2Pos = util.grid().at(2, 2, 2);
        BlockPos tank2BottomPos = util.grid().at(2, 1, 3);
        BlockPos tank2TopPos = util.grid().at(2, 2, 3);
        BlockPos depotPos = util.grid().at(2, 1, 2);
        BlockPos copperCasing1Pos = util.grid().at(2, 3, 3);
        BlockPos leverPos = util.grid().at(2, 3, 2);

        BlockPos tap3Pos = util.grid().at(0, 2, 2);
        BlockPos basinPos = util.grid().at(0, 1, 2);
        BlockPos leavesPos = util.grid().at(0, 2, 3);
        BlockPos copperCasing2Pos = util.grid().at(0, 1, 3);

        // 定义选择区域
        Selection tap1Sel = util.select().position(tap1Pos);
        Selection tank1Sel = util.select().fromTo(4, 1, 3, 4, 2, 3);
        Selection cauldronSel = util.select().position(cauldronPos);

        Selection tap2Sel = util.select().position(tap2Pos);
        Selection tank2Sel = util.select().fromTo(2, 1, 3, 2, 2, 3);
        Selection depotSel = util.select().position(depotPos);
        Selection copperCasing1Sel = util.select().position(copperCasing1Pos);
        Selection leverSel = util.select().position(leverPos);

        Selection tap3Sel = util.select().position(tap3Pos);
        Selection basinSel = util.select().position(basinPos);
        Selection leavesSel = util.select().position(leavesPos);
        Selection copperCasing2Sel = util.select().position(copperCasing2Pos);

        // 初始设置
        scene.idle(20);

        // 在储罐1中添加岩浆
        scene.world().modifyBlockEntity(tank1BottomPos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().fill(new FluidStack(Fluids.LAVA, 4000), IFluidHandler.FluidAction.EXECUTE);
        });

        // 在储罐2中添加水（替代细雪流体）
        scene.world().modifyBlockEntity(tank2BottomPos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().fill(new FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE);
        });

        // === 第一组：铜龙头填充炼药锅（岩浆）===
        scene.world().showSection(tank1Sel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(tap1Sel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(cauldronSel, Direction.DOWN);
        scene.idle(10);

        // 第一句话
        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("Copper Taps draw fluid from containers behind them and can fill Cauldrons or Basins below")
                .pointAt(util.vector().blockSurface(tap1Pos, Direction.WEST))
                .placeNearTarget();
        scene.idle(110);

        // 开启铜龙头1
        scene.world().modifyBlock(tap1Pos, s -> {
            if (s.getBlock() instanceof CopperTapBlock) {
                return s.setValue(BlockStateProperties.OPEN, true);
            }
            return s;
        }, false);
        scene.idle(10);

        // 模拟填充炼药锅的流体效果（岩浆）
        simulateLavaCauldronFilling(scene, tap1Pos, cauldronPos, tank1BottomPos, util);

        // 关闭铜龙头1
        scene.world().modifyBlock(tap1Pos, s -> {
            if (s.getBlock() instanceof CopperTapBlock) {
                return s.setValue(BlockStateProperties.OPEN, false);
            }
            return s;
        }, false);
        scene.idle(20);

        // === 第二组：含水树叶与铜龙头 ===
        scene.world().showSection(leavesSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(copperCasing2Sel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(tap3Sel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(basinSel, Direction.DOWN);
        scene.idle(10);

        // 第二句话
        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("Copper Taps can be attached to leaves and infinitely output water from waterlogged leaves")
                .pointAt(util.vector().blockSurface(tap3Pos, Direction.WEST))
                .placeNearTarget();
        scene.idle(110);

        // 开启铜龙头3
        scene.world().modifyBlock(tap3Pos, s -> {
            if (s.getBlock() instanceof CopperTapBlock) {
                return s.setValue(BlockStateProperties.OPEN, true);
            }
            return s;
        }, false);
        scene.idle(10);

        // 模拟填充工作盆的流体效果
        simulateBasinFilling(scene, tap3Pos, basinPos, util);

        // 关闭铜龙头3
        scene.world().modifyBlock(tap3Pos, s -> {
            if (s.getBlock() instanceof CopperTapBlock) {
                return s.setValue(BlockStateProperties.OPEN, false);
            }
            return s;
        }, false);
        scene.idle(20);

        // === 第三组：注液加工 ===
        scene.world().showSection(tank2Sel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(tap2Sel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(depotSel, Direction.DOWN);
        scene.idle(10);

        // 第三句话
        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("Copper Taps can perform spout processing on Depots below")
                .pointAt(util.vector().blockSurface(tap2Pos, Direction.WEST))
                .placeNearTarget();
        scene.idle(110);

        // 在置物台上放置空桶
        ItemStack emptyBucket = new ItemStack(Items.BUCKET);
        scene.overlay().showControls(util.vector().topOf(depotPos), Pointing.RIGHT, 30)
                .rightClick()
                .withItem(emptyBucket);
        scene.world().createItemOnBeltLike(depotPos, Direction.DOWN, emptyBucket);
        scene.idle(20);

        // 开启铜龙头2，进行注液加工
        scene.world().modifyBlock(tap2Pos, s -> {
            if (s.getBlock() instanceof CopperTapBlock) {
                return s.setValue(BlockStateProperties.OPEN, true);
            }
            return s;
        }, false);
        scene.idle(10);

        // 模拟注液加工效果
        simulateItemFilling(scene, tap2Pos, depotPos, tank2BottomPos, util);

        // 关闭铜龙头2
        scene.world().modifyBlock(tap2Pos, s -> {
            if (s.getBlock() instanceof CopperTapBlock) {
                return s.setValue(BlockStateProperties.OPEN, false);
            }
            return s;
        }, false);
        scene.idle(10);

        // === 第四组：红石控制 ===
        scene.world().showSection(copperCasing1Sel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(leverSel, Direction.DOWN);
        scene.idle(10);

        // 第四句话
        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text("Redstone signals can be used to control the tap's on/off state")
                .pointAt(util.vector().blockSurface(leverPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        // 开启拉杆，这会通过红石信号自动开启铜龙头
        scene.world().toggleRedstonePower(util.select().fromTo(2, 3, 2, 2, 2, 2));
        scene.effects().indicateRedstone(leverPos);
        scene.world().modifyBlock(leverPos, s -> s.setValue(LeverBlock.POWERED, true), false);

        // 铜龙头被红石信号控制，设置POWERED和OPEN状态
        scene.world().modifyBlock(tap2Pos, s -> {
            if (s.getBlock() instanceof CopperTapBlock) {
                return s.setValue(BlockStateProperties.POWERED, true)
                        .setValue(BlockStateProperties.OPEN, true);
            }
            return s;
        }, false);

        // 显示流体流动效果（细流）
        for (int i = 0; i < 30; i++) {
            Vec3 tapBottom = util.vector().centerOf(tap2Pos).add(0, -0.25, 0);
            Vec3 depotTop = util.vector().topOf(depotPos);
            createFluidStream(scene, tapBottom, depotTop, new FluidStack(Fluids.WATER, 100));
            scene.idle(1);
        }

        // 关闭拉杆
        scene.world().toggleRedstonePower(util.select().fromTo(2, 3, 2, 2, 2, 2));
        scene.effects().indicateRedstone(leverPos);
        scene.world().modifyBlock(leverPos, s -> s.setValue(LeverBlock.POWERED, false), false);

        // 铜龙头失去红石信号，自动关闭
        scene.world().modifyBlock(tap2Pos, s -> {
            if (s.getBlock() instanceof CopperTapBlock) {
                return s.setValue(BlockStateProperties.POWERED, false)
                        .setValue(BlockStateProperties.OPEN, false);
            }
            return s;
        }, false);
        scene.idle(20);

        scene.markAsFinished();
    }

    // 模拟岩浆炼药锅填充效果
    private static void simulateLavaCauldronFilling(CreateSceneBuilder scene, BlockPos tapPos, BlockPos cauldronPos,
                                                    BlockPos tankPos, SceneBuildingUtil util) {
        FluidStack lava = new FluidStack(Fluids.LAVA, 1000);

        // 生成流体细流效果
        Vec3 tapBottom = util.vector().centerOf(tapPos).add(0, -0.25, 0);
        Vec3 cauldronTop = util.vector().topOf(cauldronPos);

        for (int i = 0; i < 60; i++) {
            createFluidStream(scene, tapBottom, cauldronTop, lava);
            scene.idle(1);
        }

        // 一次性填满岩浆炼药锅
        scene.world().setBlock(cauldronPos, Blocks.LAVA_CAULDRON.defaultBlockState(), false);

        // 减少储罐中的岩浆
        scene.world().modifyBlockEntity(tankPos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().drain(1000, IFluidHandler.FluidAction.EXECUTE);
        });

        scene.idle(10);
    }

    // 模拟工作盆填充效果
// 模拟工作盆填充效果
    private static void simulateBasinFilling(CreateSceneBuilder scene, BlockPos tapPos, BlockPos basinPos,
                                             SceneBuildingUtil util) {
        FluidStack water = new FluidStack(Fluids.WATER, 1000);

        Vec3 tapBottom = util.vector().centerOf(tapPos).add(0, -0.25, 0);
        Vec3 basinTop = util.vector().topOf(basinPos);

        // 生成持续的流体细流效果
        for (int i = 0; i < 60; i++) {
            createFluidStream(scene, tapBottom, basinTop, water);
            scene.idle(1);
        }

        // 填充工作盆 - 直接使用方块实体的能力
        scene.world().modifyBlockEntity(basinPos, BasinBlockEntity.class, be -> {
            // 直接通过方块实体的 Level 获取能力
            IFluidHandler handler = be.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, basinPos, null);
            if (handler != null) {
                handler.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
            }
        });
    }

    // 模拟物品注液加工效果
    private static void simulateItemFilling(CreateSceneBuilder scene, BlockPos tapPos, BlockPos depotPos,
                                            BlockPos tankPos, SceneBuildingUtil util) {
        FluidStack water = new FluidStack(Fluids.WATER, 1000);

        Vec3 tapBottom = util.vector().centerOf(tapPos).add(0, -0.25, 0);
        Vec3 depotTop = util.vector().topOf(depotPos);

        // 生成注液粒子效果（20 tick的处理时间）
        for (int i = 0; i < 20; i++) {
            createFluidStream(scene, tapBottom, depotTop, water);
            scene.idle(1);
        }

        // 替换物品
        scene.world().removeItemsFromBelt(depotPos);
        ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET);
        scene.world().createItemOnBeltLike(depotPos, Direction.DOWN, waterBucket);

        // 从储罐中减少水
        scene.world().modifyBlockEntity(tankPos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().drain(1000, IFluidHandler.FluidAction.EXECUTE);
        });

        scene.idle(10);
    }

    // 创建细流流体效果
    private static void createFluidStream(CreateSceneBuilder scene, Vec3 start, Vec3 end, FluidStack fluid) {
        ParticleOptions particle = FluidFX.getFluidParticle(fluid);

        // 创建一条细流，从龙头底部到目标
        Vec3 flow = end.subtract(start);

        // 在流体流路径上密集生成粒子，形成连续的细流
        for (float t = 0; t <= 1; t += 0.1f) {
            Vec3 pos = start.add(flow.scale(t));
            Vec3 motion = flow.normalize().scale(0.01);

            // 细流效果，减少随机偏移
            RandomSource random = RandomSource.create();
            pos = pos.add(
                    VecHelper.offsetRandomly(Vec3.ZERO, random, 0.005f)
            );

            scene.effects().emitParticles(
                    pos,
                    scene.effects().simpleParticleEmitter(particle, motion),
                    0.5f,
                    1
            );
        }

        // 在底部添加一些飞溅粒子
        if (RandomSource.create().nextFloat() < 0.3f) {
            Vec3 splashMotion = Vec3.ZERO.add(
                    VecHelper.offsetRandomly(Vec3.ZERO, RandomSource.create(), 0.02f)
            );
            scene.effects().emitParticles(
                    end,
                    scene.effects().simpleParticleEmitter(particle, splashMotion),
                    0.2f,
                    1
            );
        }
    }
}