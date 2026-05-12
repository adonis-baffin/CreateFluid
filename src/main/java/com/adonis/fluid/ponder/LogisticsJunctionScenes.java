package com.adonis.fluid.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class LogisticsJunctionScenes {

    public static void item(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("logistics_junction_item", "Sorting Packaged Items with Logistics Junctions");
        scene.configureBasePlate(0, 0, 8);
        scene.scaleSceneView(0.9f);
        scene.showBasePlate();

        BlockPos junctionPos = util.grid().at(4, 2, 4);
        Selection fullScene = util.select().fromTo(0, 1, 0, 8, 6, 8);
        Selection junctionSel = util.select().fromTo(3, 1, 3, 5, 4, 5);
        Selection upperLinkSel = util.select().fromTo(3, 4, 3, 5, 6, 5);
        Selection outputSel = util.select().fromTo(0, 1, 0, 8, 3, 8).substract(junctionSel);

        scene.idle(15);
        scene.world().showSection(fullScene, Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("Logistics Junctions unpack routed packages and forward their contents.")
                .pointAt(util.vector().centerOf(junctionPos))
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showOutline(PonderPalette.WHITE, "junction_core", junctionSel, 80);
        scene.overlay().showText(80)
                .colored(PonderPalette.WHITE)
                .text("This center block is the unpacking core.")
                .pointAt(util.vector().centerOf(junctionPos))
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showOutline(PonderPalette.INPUT, "junction_top_link", upperLinkSel, 80);
        scene.overlay().showText(80)
                .colored(PonderPalette.INPUT)
                .text("The upper connection can link to a remote inventory or tank.")
                .pointAt(util.vector().centerOf(4, 5, 4))
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showOutline(PonderPalette.OUTPUT, "junction_outputs", outputSel, 100);
        scene.overlay().showText(100)
                .colored(PonderPalette.OUTPUT)
                .text("Use this scene as a base to show how boxed items leave toward their destinations.")
                .pointAt(util.vector().centerOf(6, 2, 4))
                .placeNearTarget();
        scene.idle(110);
    }

    public static void fluid(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("logistics_junction_fluid", "Routing Boxed Fluids with Logistics Junctions");
        scene.configureBasePlate(0, 0, 8);
        scene.scaleSceneView(0.9f);
        scene.showBasePlate();

        BlockPos junctionPos = util.grid().at(4, 2, 4);
        Selection fullScene = util.select().fromTo(0, 1, 0, 8, 6, 8);
        Selection junctionSel = util.select().fromTo(3, 1, 3, 5, 4, 5);
        Selection upperLinkSel = util.select().fromTo(3, 4, 3, 5, 6, 5);
        Selection fluidSideSel = util.select().fromTo(5, 1, 2, 8, 4, 6);

        scene.idle(15);
        scene.world().showSection(fullScene, Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("Logistics Junctions can also unpack fluids stored inside routed packages.")
                .pointAt(util.vector().centerOf(junctionPos))
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showOutline(PonderPalette.WHITE, "junction_core_fluid", junctionSel, 80);
        scene.overlay().showText(80)
                .colored(PonderPalette.WHITE)
                .text("The same core handles both item cargo and boxed fluids.")
                .pointAt(util.vector().centerOf(junctionPos))
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showOutline(PonderPalette.INPUT, "junction_top_link_fluid", upperLinkSel, 80);
        scene.overlay().showText(80)
                .colored(PonderPalette.INPUT)
                .text("Its top link can feed nearby tanks or other remote fluid targets.")
                .pointAt(util.vector().centerOf(4, 5, 4))
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showOutline(PonderPalette.BLUE, "junction_fluid_targets", fluidSideSel, 100);
        scene.overlay().showText(100)
                .colored(PonderPalette.BLUE)
                .text("This storyboard is meant as a starting point for showing your fluid routing setup.")
                .pointAt(util.vector().centerOf(6, 2, 4))
                .placeNearTarget();
        scene.idle(110);
    }
}
