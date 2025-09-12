package com.adonis.fluid.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public class PipetteScenes {

    public static void setup(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        
        scene.title("pipette_setup", "配置动力移液器");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        
        // 定义位置
        BlockPos pipettePos = util.grid().at(2, 1, 2);
        BlockPos basinPos = util.grid().at(4, 2, 1);  // 工作盆位置（对应动力臂场景的inputDepot）
        BlockPos splashingPos = util.grid().at(0, 2, 1); // 分液池位置（对应动力臂场景的outputDepot）
        BlockPos tankPos = util.grid().at(4, 1, 3); // 储罐位置
        BlockPos fluidInterfacePos = util.grid().at(4, 2, 3); // 流体接口位置
        BlockPos blazeBurnerPos = util.grid().at(0, 1, 3); // 烈焰人燃烧室位置
        BlockPos beehivePos = util.grid().at(1, 1, 4); // 蜂巢位置
        BlockPos beenestPos = util.grid().at(3, 1, 4); // 蜂箱位置
        
        // 定义选择区域
        Selection pipetteSel = util.select().position(pipettePos);
        Selection basinSel = util.select().position(basinPos);
        Selection splashingSel = util.select().position(splashingPos);
        Selection tankSel = util.select().position(tankPos);
        Selection fluidInterfaceSel = util.select().position(fluidInterfacePos);
        Selection blazeBurnerSel = util.select().position(blazeBurnerPos);
        Selection beehiveSel = util.select().position(beehivePos);
        Selection beenestSel = util.select().position(beenestPos);
        
        scene.idle(20);
        
        // 显示动力移液器
        scene.world().showSection(pipetteSel, Direction.DOWN);
        scene.idle(10);
        
        // 显示工作盆和分液池
        scene.world().showSection(basinSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(splashingSel, Direction.DOWN);
        scene.idle(15);
        
        // 高亮显示动力移液器、工作盆和分液池
        scene.overlay().showOutlineWithText(pipetteSel.add(basinSel).add(splashingSel), 80)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("动力移液器与动力臂的工作方式别无二致，但是正如其名，它专注于与流体容器交互")
                .pointAt(util.vector().blockSurface(pipettePos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);
        
        // 显示储罐和流体接口
        scene.world().showSection(tankSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(fluidInterfaceSel, Direction.DOWN);
        scene.idle(10);
        
        // 高亮流体接口
        scene.overlay().showOutlineWithText(fluidInterfaceSel, 80)
                .attachKeyFrame()
                .colored(PonderPalette.OUTPUT)
                .text("对于某些无法直接交互的流体容器，流体接口可以解决此问题")
                .pointAt(util.vector().blockSurface(fluidInterfacePos, Direction.NORTH))
                .placeNearTarget();
        scene.idle(90);
        
        // 显示烈焰人燃烧室、蜂巢和蜂箱
        scene.world().showSection(blazeBurnerSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(beehiveSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(beenestSel, Direction.DOWN);
        scene.idle(10);
        
        // 高亮特殊方块
        scene.overlay().showOutlineWithText(blazeBurnerSel.add(beehiveSel).add(beenestSel), 100)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("除了常规的流体容器，动力移液器还支持与更多有趣的方块交互，也许它能更好地帮助你施展创意")
                .pointAt(util.vector().blockSurface(beehivePos, Direction.WEST))
                .placeNearTarget();
        scene.idle(110);
        
        // 高亮动力移液器并说明存量指示器
        Vec3 pipetteTop = util.vector().blockSurface(pipettePos, Direction.UP).add(0, 0.5, 0);
        scene.overlay().showOutlineWithText(pipetteSel, 100)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text("这是动力移液器的存量指示器，通过它来了解当前动力移液器所持有的流体量。动力移液器的最大流体容量为1000mb")
                .pointAt(pipetteTop)
                .placeNearTarget();
        scene.idle(110);
        
        scene.markAsFinished();
    }
}