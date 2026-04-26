package com.adonis.fluid.ponder;

import com.adonis.fluid.registry.CFBlocks;
import com.adonis.fluid.registry.CFItems;
import com.simibubi.create.AllBlocks;
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

public class ConductorBatonAdvancedScenes {

    public static void advancedFeatures(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("baton_advanced", "Advanced Conductor Baton Features");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos lampPos = util.grid().at(1, 1, 1);
        BlockPos pumpPos = util.grid().at(3, 1, 1);
        BlockPos frogportPos = util.grid().at(1, 1, 3);
        BlockPos mailboxPos = util.grid().at(3, 1, 3);
        BlockPos switchPos = util.grid().at(2, 1, 4);

        Selection lampSel = util.select().position(lampPos);
        Selection pumpSel = util.select().position(pumpPos);
        Selection frogportSel = util.select().position(frogportPos);
        Selection mailboxSel = util.select().position(mailboxPos);
        Selection switchSel = util.select().position(switchPos);
        Selection gearsSel = util.select().fromTo(3, 1, 0, 3, 1, 1);

        scene.idle(20);

        // 显示石英灯
        scene.world().showSection(lampSel, Direction.DOWN);
        scene.idle(5);
        scene.overlay().showText(60)
                .colored(PonderPalette.GREEN)
                .text("手持指挥棒右键石英灯，可以快速切换其开关状态")
                .pointAt(util.vector().blockSurface(lampPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(70);

        ItemStack baton = CFItems.BATON.asStack();
        scene.overlay().showControls(util.vector().blockSurface(lampPos, Direction.WEST), Pointing.RIGHT, 40)
                .rightClick()
                .withItem(baton);
        scene.idle(50);

        // 显示离心泵
        scene.world().showSection(pumpSel, Direction.DOWN);
        scene.world().showSection(gearsSel, Direction.DOWN);
        scene.idle(5);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text("右键离心泵可以循环切换其工作模式：推、拉、或推拉兼备")
                .pointAt(util.vector().blockSurface(pumpPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showControls(util.vector().blockSurface(pumpPos, Direction.WEST), Pointing.RIGHT, 40)
                .rightClick()
                .withItem(baton);
        scene.idle(50);

        // 显示蛙口和信箱
        scene.world().showSection(frogportSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(mailboxSel, Direction.DOWN);
        scene.idle(5);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("指挥棒还可以配置蛙口和信箱的连接目标，方法与动力臂类似")
                .pointAt(util.vector().blockSurface(frogportPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        // 显示阈值开关
        scene.world().showSection(switchSel, Direction.DOWN);
        scene.idle(5);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text("右键阈值开关可以直接打开配置界面，无需护目镜")
                .pointAt(util.vector().blockSurface(switchPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        scene.markAsFinished();
    }
}
