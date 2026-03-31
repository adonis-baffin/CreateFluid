package com.adonis.fluid.ponder;

import com.adonis.fluid.block.CopperTap.CopperTapBlock;
import com.adonis.fluid.block.GutterOutlet.GutterOutletBlockEntity;
import com.adonis.fluid.block.GutterOutlet.SmartGutterOutletBlockEntity;
import com.simibubi.create.content.fluids.FluidFX;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class GutterOutletScenes {

    public static void gutteroutlet(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("gutter_outlet", "Collecting Fluid with Gutter Outlets");
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.9f);
        scene.showBasePlate();
        scene.idle(20);

        // 定义位置
        BlockPos gutterPos = util.grid().at(2, 1, 2);
        BlockPos pipe1Pos = util.grid().at(3, 1, 2);
        BlockPos tankBottomPos = util.grid().at(2, 1, 4);
        BlockPos pumpPos = util.grid().at(4, 1, 2);
        Selection kineticsSel = util.select().position(5, 1, 2)
                .add(util.select().position(5, 0, 1));
        Selection casingSel = util.select().position(2, 4, 2);
        Selection stalactiteSel = util.select().position(2, 3, 2);

        // 定义选择区域
        Selection gutterSel = util.select().position(gutterPos);
        Selection pipe1Sel = util.select().position(pipe1Pos);
        Selection pipes2Sel = util.select().fromTo(4, 2, 2, 4, 2, 4).add(util.select().position(3, 2, 4));
        Selection tankSel = util.select().fromTo(2, 1, 4, 2, 3, 4);
        Selection pumpSel = util.select().position(pumpPos);

        // 第一步：单独展示集水器
        ElementLink<WorldSectionElement> gutterLink = scene.world().showIndependentSection(gutterSel, Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("Gutter Outlets can be used to drain large bodies of fluid above them, just like Item Drains")
                .pointAt(util.vector().blockSurface(gutterPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(80);

        // 第二步：演示收集岩浆/雨雪
        scene.idle(20);
        scene.world().showSection(casingSel, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(stalactiteSel, Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(110)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("Gutter Outlets can also collect lava like a cauldron, or to collect fluid in rainy or snowy weather")
                .pointAt(util.vector().topOf(gutterPos))
                .placeNearTarget();
        scene.idle(130);

        // 隐藏临时演示方块
        scene.world().hideSection(casingSel, Direction.UP);
        scene.idle(10);
        scene.world().hideSection(stalactiteSel, Direction.UP);
        scene.idle(20);

        // 第三步：右键倒入岩浆桶
        ItemStack lavaBucket = new ItemStack(Items.LAVA_BUCKET);
        scene.overlay().showControls(new Vec3(gutterPos.getX() + 0.5, gutterPos.getY() + 1, gutterPos.getZ() + 0.5),
                        Pointing.DOWN, 40)
                .rightClick()
                .withItem(lavaBucket);
        scene.idle(20);

        // 【修复点】通过 getCapability() 获取 handler 进行操作
        scene.world().modifyBlockEntity(gutterPos, GutterOutletBlockEntity.class, be -> {
            SmartFluidTankBehaviour tankBehaviour = be.tankBehaviour;
            if (tankBehaviour != null) {
                tankBehaviour.allowInsertion();
                // 必须调用 getCapability() 才能访问 fill 方法
                IFluidHandler handler = tankBehaviour.getCapability();
                handler.fill(new FluidStack(Fluids.LAVA, 2000), IFluidHandler.FluidAction.EXECUTE);

                // 手动触发渲染更新
                SmartFluidTankBehaviour.TankSegment primary = tankBehaviour.getPrimaryTank();
                primary.getFluidLevel().chase(1.0f, 0.5f, LerpedFloat.Chaser.EXP);
                primary.onFluidStackChanged();

                be.setChanged();
                be.sendData();
            }
        });

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text("Right-click it to pour fluid from your held item into it, or to extract fluid")
                .pointAt(util.vector().blockSurface(gutterPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(120);

        // 第四步：显示管道网络 + 动力系统
        scene.world().showSection(pipe1Sel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(pipes2Sel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(tankSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(pumpSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(kineticsSel, Direction.DOWN);
        scene.idle(20);

        // 接入动力
        scene.world().setKineticSpeed(kineticsSel, 32);
        scene.world().setKineticSpeed(pumpSel, 32);
        scene.world().propagatePipeChange(pumpPos);
        scene.idle(20);

        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.OUTPUT)
                .text("Pipe Networks can now pull the fluid from their internal buffer")
                .pointAt(util.vector().blockSurface(gutterPos, Direction.EAST))
                .placeNearTarget();
        scene.idle(20);

        scene.world().propagatePipeChange(pumpPos);

        // 【修复点】通过 getCapability() 获取 handler 进行操作
        scene.world().modifyBlockEntity(gutterPos, GutterOutletBlockEntity.class, be -> {
            SmartFluidTankBehaviour tankBehaviour = be.tankBehaviour;
            if (tankBehaviour != null) {
                tankBehaviour.allowExtraction();
                // 必须调用 getCapability() 才能访问 drain 方法
                IFluidHandler handler = tankBehaviour.getCapability();
                handler.drain(2000, IFluidHandler.FluidAction.EXECUTE);

                SmartFluidTankBehaviour.TankSegment primary = tankBehaviour.getPrimaryTank();
                primary.getFluidLevel().chase(0.0f, 0.5f, LerpedFloat.Chaser.EXP);
                primary.onFluidStackChanged();

                be.setChanged();
                be.sendData();
            }
        });

        // 在储罐底部方块实体中添加等量岩浆
        scene.world().modifyBlockEntity(tankBottomPos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().fill(new FluidStack(Fluids.LAVA, 2000), IFluidHandler.FluidAction.EXECUTE);
        });

        scene.idle(80);

        scene.markAsFinished();
    }

    public static void gutteroutletinteract(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("gutter_outlet_interact", "Gutter Outlet Interactions");
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.9f);
        scene.showBasePlate();
        scene.idle(20);

        // 主位置
        BlockPos gutterTempPos = util.grid().at(2, 2, 2);
        BlockPos tankTempPos = util.grid().at(2, 1, 2);

        // 真实方块初始偏移位置
        BlockPos smartGutterOffsetPos = util.grid().at(3, 2, 2);
        BlockPos tank1OffsetPos = util.grid().at(3, 1, 2);

        // 其他位置
        BlockPos tank2Pos = util.grid().at(2, 3, 3);
        BlockPos tapPos = util.grid().at(2, 3, 2);
        BlockPos basinPos = util.grid().at(3, 2, 0);
        BlockPos casing2Pos = util.grid().at(3, 1, 0);
        BlockPos gutterSidePos = util.grid().at(2, 1, 0);
        BlockPos pipettePos = util.grid().at(0, 1, 3);
        BlockPos leverPos = util.grid().at(1, 2, 3);

        // 定义选择区域
        Selection gutterTempSel = util.select().position(gutterTempPos);
        Selection tankTempSel = util.select().position(tankTempPos);
        Selection casing1Sel = util.select().fromTo(2, 1, 3, 2, 2, 3);
        Selection tank2Sel = util.select().position(tank2Pos);
        Selection tapSel = util.select().position(tapPos);

        Selection smartGutterSel = util.select().position(smartGutterOffsetPos);
        Selection tank1Sel = util.select().position(tank1OffsetPos);
        Selection leverSel = util.select().position(leverPos);

        Selection casing2Sel = util.select().position(casing2Pos);
        Selection basinSel = util.select().position(basinPos);
        Selection gutterSideSel = util.select().position(gutterSidePos);

        Selection pipetteSel = util.select().position(pipettePos);
        Selection gearsSel = util.select().fromTo(0, 1, 4, 0, 1, 5);

        // 初始设置储罐2填充1000mb岩浆
        scene.world().modifyBlockEntity(tank2Pos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);
        });

        scene.idle(10);

        // 显示组件
        scene.world().showSection(tankTempSel, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(gutterTempSel, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(casing1Sel, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(tank2Sel, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(tapSel, Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("Copper Taps can fill Gutter Outlets")
                .pointAt(util.vector().blockSurface(gutterTempPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(100);

        // 开启铜龙头，填充临时普通集水器
        scene.world().modifyBlock(tapPos, s -> {
            if (s.getBlock() instanceof CopperTapBlock) {
                return s.setValue(BlockStateProperties.OPEN, true);
            }
            return s;
        }, false);
        simulateTapPouring(scene, util, tapPos, gutterTempPos, new FluidStack(Fluids.LAVA, 1000));

        // 【修复点】
        scene.world().modifyBlockEntity(gutterTempPos, GutterOutletBlockEntity.class, be -> {
            be.tankBehaviour.allowInsertion();
            be.tankBehaviour.getCapability().fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);
            var primary = be.tankBehaviour.getPrimaryTank();
            primary.getFluidLevel().chase(1.0f, 0.5f, LerpedFloat.Chaser.EXP);
            primary.onFluidStackChanged();
        });
        scene.idle(20);

        // 关闭铜龙头
        scene.world().modifyBlock(tapPos, s -> {
            if (s.getBlock() instanceof CopperTapBlock) {
                return s.setValue(BlockStateProperties.OPEN, false);
            }
            return s;
        }, false);
        scene.idle(20);

        // 移除临时普通集水器
        scene.world().hideSection(gutterTempSel, Direction.WEST);
        scene.idle(15);

        // 显示智能集水器
        ElementLink<WorldSectionElement> smartGutterLink = scene.world().showIndependentSection(smartGutterSel, Direction.WEST);
        scene.world().moveSection(smartGutterLink, util.vector().of(-1, 0, 0), 10);
        scene.idle(15);

        scene.world().showSection(leverSel, Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(110)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("Smart Gutter Outlets have the same functionality, but can be disabled with a redstone signal")
                .pointAt(util.vector().blockSurface(gutterTempPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(120);

        // 拉杆控制
        scene.world().toggleRedstonePower(leverSel.add(smartGutterSel));
        scene.effects().indicateRedstone(leverPos);
        scene.idle(40);

        scene.world().toggleRedstonePower(leverSel.add(smartGutterSel));
        scene.effects().indicateRedstone(leverPos);
        scene.idle(20);

        // 移除临时铜机壳
        scene.world().hideSection(tankTempSel, Direction.WEST);
        scene.idle(15);

        // 显示真实流体储罐1
        ElementLink<WorldSectionElement> tank1Link = scene.world().showIndependentSection(tank1Sel, Direction.WEST);
        scene.world().moveSection(tank1Link, util.vector().of(-1, 0, 0), 10);
        scene.idle(15);

        // 【修复点】
        scene.world().modifyBlockEntity(gutterTempPos, SmartGutterOutletBlockEntity.class, be -> {
            be.tankBehaviour.allowExtraction();
            be.tankBehaviour.getCapability().drain(1000, IFluidHandler.FluidAction.EXECUTE);
            var primary = be.tankBehaviour.getPrimaryTank();
            primary.getFluidLevel().chase(0.0f, 0.5f, LerpedFloat.Chaser.EXP);
            primary.onFluidStackChanged();
        });

        scene.world().modifyBlockEntity(tankTempPos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);
        });

        scene.overlay().showText(90)
                .attachKeyFrame()
                .colored(PonderPalette.OUTPUT)
                .text("Gutter Outlets will automatically fill fluid containers placed directly below them")
                .pointAt(util.vector().blockSurface(tankTempPos, Direction.SOUTH))
                .placeNearTarget();
        scene.idle(100);

        // 显示工作盆等
        scene.world().showSection(casing2Sel, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(basinSel, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(gutterSideSel, Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("Gutter Outlets can accept a Basin's output")
                .pointAt(util.vector().blockSurface(gutterSidePos, Direction.WEST))
                .placeNearTarget();
        scene.idle(100);

        // 显示移液器系统
        scene.world().showSection(gearsSel, Direction.DOWN);
        scene.world().showSection(pipetteSel, Direction.DOWN);
        scene.idle(10);

        scene.world().setKineticSpeed(gearsSel, -32);
        scene.world().setKineticSpeed(pipetteSel, -32);
        scene.idle(20);

        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("Mechanical Pipettes can deposit fluid into or extract fluid from Gutter Outlets")
                .pointAt(util.vector().blockSurface(pipettePos, Direction.EAST))
                .placeNearTarget();
        scene.idle(110);

        scene.markAsFinished();
    }

    private static void simulateTapPouring(CreateSceneBuilder scene, SceneBuildingUtil util,
                                           BlockPos tapPos, BlockPos targetPos, FluidStack fluid) {
        ParticleOptions particle = FluidFX.getFluidParticle(fluid);
        Vec3 start = util.vector().centerOf(tapPos).add(0, -0.25, 0);
        Vec3 end = util.vector().topOf(targetPos);
        Vec3 flow = end.subtract(start);

        for (int i = 0; i < 50; i++) {
            for (float t = 0; t <= 1; t += 0.1f) {
                Vec3 pos = start.add(flow.scale(t))
                        .add(VecHelper.offsetRandomly(Vec3.ZERO, RandomSource.create(), 0.005f));
                Vec3 motion = flow.normalize().scale(0.01);

                scene.effects().emitParticles(pos,
                        scene.effects().simpleParticleEmitter(particle, motion),
                        0.5f, 1);
            }
            if (RandomSource.create().nextFloat() < 0.3f) {
                scene.effects().emitParticles(end,
                        scene.effects().simpleParticleEmitter(particle,
                                VecHelper.offsetRandomly(Vec3.ZERO, RandomSource.create(), 0.02f)),
                        0.2f, 1);
            }
            scene.idle(1);
        }
    }
}