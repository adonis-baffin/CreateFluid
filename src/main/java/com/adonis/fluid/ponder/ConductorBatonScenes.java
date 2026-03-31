package com.adonis.fluid.ponder;

import com.adonis.fluid.registry.CFItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

public class ConductorBatonScenes {

    public static void usage(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("baton", "Using the Conductor Baton");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        // 定义位置
        BlockPos armPos = util.grid().at(2, 1, 2);
        BlockPos depotPos = util.grid().at(0, 1, 1);
        BlockPos ejectorDepotPos = util.grid().at(0, 1, 2);
        BlockPos pipettePos = util.grid().at(0, 1, 4);
        BlockPos pipetteGearPos = util.grid().at(0, 1, 5);
        
        // 第一套加工设备（冲压机系统）
        BlockPos pressPos = util.grid().at(4, 4, 1);
        BlockPos pressShaftPos = util.grid().at(5, 4, 1);
        BlockPos pressFunnelPos = util.grid().at(3, 2, 1);
        BlockPos shaftPos = util.grid().at(5, 4, 1);
        
        // 第二套加工设备（搅拌器系统）
        BlockPos mixerPos = util.grid().at(4, 4, 3);
        BlockPos mixerGearPos = util.grid().at(5, 4, 3);
        BlockPos mixerFunnelPos = util.grid().at(3, 2, 3);
        BlockPos basinPos = util.grid().at(3, 1, 3);
        
        // 齿轮
        BlockPos gear1Pos = util.grid().at(2, 1, 3);
        BlockPos gear2Pos = util.grid().at(2, 1, 4);
        BlockPos gear3Pos = util.grid().at(2, 1, 5);

        // 定义选择区域
        Selection armSel = util.select().position(armPos);
        Selection depotSel = util.select().position(depotPos);
        Selection ejectorDepotSel = util.select().position(ejectorDepotPos);
        Selection pipetteSel = util.select().position(pipettePos);
        Selection pipetteGearSel = util.select().position(pipetteGearPos);
        Selection shaftSel = util.select().position(shaftPos);
        
        // 第一套加工设备选择
        Selection pressSel = util.select().position(pressPos);
        Selection pressShaftSel = util.select().fromTo(5, 1, 1, 5, 3, 1);
        Selection pressFunnelSel = util.select().position(pressFunnelPos);
        Selection pressSystemSel = util.select().fromTo(3, 1, 1, 5, 4, 1);
        
        // 第二套加工设备选择
        Selection mixerSel = util.select().position(mixerPos);
        Selection mixerShaftSel = util.select().fromTo(3, 1, 3, 3, 3, 3)
                .add(util.select().fromTo(4, 1, 3, 4, 3, 3))
                .add(util.select().fromTo(5, 1, 3, 5, 3, 3));
        Selection mixerGearSel = util.select().position(mixerGearPos);
        Selection mixerFunnelSel = util.select().position(mixerFunnelPos);
        Selection mixerSystemSel = util.select().fromTo(3, 1, 3, 5, 4, 3);
        
        Selection gearsSel = util.select().fromTo(2, 1, 3, 2, 1, 5);
        Selection allGearsSel = gearsSel.add(pressShaftSel).add(mixerShaftSel)
                .add(pressSel).add(mixerSel).add(mixerGearSel)
                .add(pipetteGearSel).add(pipetteSel).add(shaftSel);

        // 初始设置 - 所有动力设备静止
        scene.world().setKineticSpeed(allGearsSel, 0);
        
        scene.idle(20);

        // 显示动力臂
        scene.world().showSection(armSel, Direction.DOWN);
        scene.idle(10);
        
        // 显示第一套加工设备（冲压机系统）
        scene.world().showSection(pressSystemSel, Direction.DOWN);
        scene.idle(10);
        
        // 显示置物台
        scene.world().showSection(depotSel, Direction.DOWN);
        scene.idle(10);

        // 第一句话：输入
        scene.overlay().showOutlineWithText(depotSel, 40)
                .colored(PonderPalette.INPUT)
                .text("Input")
                .pointAt(util.vector().blockSurface(depotPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(5);

        // 第二句话：输出
        scene.overlay().showOutlineWithText(pressFunnelSel, 40)
                .colored(PonderPalette.OUTPUT)
                .text("Output")
                .pointAt(util.vector().blockSurface(pressFunnelPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(40);

        // 显示第二套加工设备
        scene.world().showSection(mixerSystemSel, Direction.DOWN);
        scene.idle(10);
        
        // 显示动力臂后面的齿轮并启动所有动力
        scene.world().showSection(gearsSel, Direction.DOWN);
        scene.idle(10);
        
        scene.world().setKineticSpeed(allGearsSel, 32);
        scene.idle(20);

        // 第三句话
        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("Sometimes after placing a Mechanical Arm, you may want to add or modify its interaction points")
                .pointAt(util.vector().blockSurface(armPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        // 第四句话 - 显示右键指挥棒
        ItemStack baton = CFItems.BATON.asStack();
        scene.overlay().showControls(util.vector().blockSurface(armPos, Direction.WEST), Pointing.RIGHT, 60)
                .rightClick()
                .withItem(baton);
        scene.idle(10);
        
        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text("Right-click it with the Conductor Baton to enter conductor mode. In this mode, all interaction points will be highlighted")
                .pointAt(util.vector().blockSurface(armPos, Direction.UP))
                .placeNearTarget();
        scene.overlay().showOutline(PonderPalette.WHITE, "highlight", armSel, 80);
        scene.idle(20);

        // 高亮显示交互点

        scene.overlay().showOutline(PonderPalette.INPUT, "input", depotSel, 180);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "output1", pressFunnelSel, 180);
        scene.idle(70);

        // 第五句话 - 添加新的交互点
        scene.overlay().showControls(util.vector().blockSurface(mixerFunnelPos, Direction.WEST), Pointing.RIGHT, 60)
                .rightClick()
                .withItem(baton);
        scene.idle(10);
        
        scene.overlay().showOutlineWithText(mixerFunnelSel, 100)
                .attachKeyFrame()
                .colored(PonderPalette.OUTPUT)
                .text("Now you can freely adjust the Arm's interaction points, just like when holding the Arm itself")
                .pointAt(util.vector().blockSurface(mixerFunnelPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(110);

        // 第六句话 - 完成修改
        scene.overlay().showControls(util.vector().blockSurface(armPos, Direction.WEST), Pointing.RIGHT, 60)
                .rightClick()
                .withItem(baton);
        scene.overlay().showOutline(PonderPalette.WHITE, "highlight", armSel, 70);
        scene.idle(10);
        
        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("Right-click the Arm again to update its interaction points")
                .pointAt(util.vector().blockSurface(armPos, Direction.UP))
                .placeNearTarget();
        scene.idle(90);

        // 显示移液器和弹射置物台
        scene.world().showSection(pipetteSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(ejectorDepotSel, Direction.DOWN);
        scene.idle(10);

        // 第七句话
        scene.overlay().showOutlineWithText(ejectorDepotSel, 100)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text("The interaction points of Mechanical Pipettes and Ejector Depots can also be modified with the Conductor Baton")
                .pointAt(util.vector().blockSurface(ejectorDepotPos, Direction.WEST))
                .placeNearTarget();
        
        scene.overlay().showOutline(PonderPalette.WHITE, "highlight", pipetteSel.add(ejectorDepotSel), 100);
        scene.idle(110);

        scene.markAsFinished();
    }
}