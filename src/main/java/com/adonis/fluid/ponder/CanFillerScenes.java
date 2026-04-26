package com.adonis.fluid.ponder;

import com.adonis.fluid.registry.CFBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

public class CanFillerScenes {

    public static void filling(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("can_filler", "Fluid Logistics with Can Filler");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos canFillerPos = util.grid().at(2, 1, 2);
        BlockPos tankPos = util.grid().at(0, 1, 2);
        BlockPos packagerPos = util.grid().at(4, 1, 2);
        BlockPos panelPos = util.grid().at(2, 2, 4);

        Selection canFillerSel = util.select().position(canFillerPos);
        Selection tankSel = util.select().fromTo(0, 1, 2, 0, 3, 2);
        Selection packagerSel = util.select().position(packagerPos);
        Selection panelSel = util.select().position(panelPos);
        Selection beltSel = util.select().fromTo(3, 1, 2, 5, 1, 2);

        scene.world().setKineticSpeed(util.select().position(2, 1, 2), 0);
        scene.idle(20);

        scene.world().showSection(tankSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(canFillerSel, Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(80)
                .colored(PonderPalette.GREEN)
                .text("装罐机是流体物流系统的核心。它将流体封装进铜罐，以便通过物流网络运输")
                .pointAt(util.vector().blockSurface(canFillerPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(beltSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(packagerSel, Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text("装罐机背对流体容器，面对物流传送带。它会自动将容器中的流体封装并发货")
                .pointAt(util.vector().blockSurface(canFillerPos, Direction.EAST))
                .placeNearTarget();
        scene.idle(90);

        // 模拟封装过程
        ItemStack copperCan = com.adonis.fluid.registry.CFItems.COPPER_CAN.asStack();
        PackageItem.clearAddress(copperCan);
        scene.world().createItemOnBeltLike(packagerPos, Direction.EAST, copperCan);
        scene.idle(20);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.OUTPUT)
                .text("封装好的铜罐会被送往目的地，到达后由对应的装罐机拆包，将流体重新注入储罐")
                .pointAt(util.vector().blockSurface(packagerPos, Direction.EAST))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(panelSel, Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("配合工厂面板，可以自动化地向装罐机下单，请求特定类型的流体")
                .pointAt(util.vector().blockSurface(panelPos, Direction.UP))
                .placeNearTarget();
        scene.idle(90);

        scene.markAsFinished();
    }
}
