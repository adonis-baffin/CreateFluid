package com.adonis.fluid.ponder;

import com.adonis.fluid.registry.CFItems;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.redstone.RoseQuartzLampBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ConductorBatonAdvancedScenes {

    public static void advancedFeatures(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("baton_advanced", tr("fluid.ponder.baton_advanced.header"));
        scene.configureBasePlate(0, 0, 8);
        scene.scaleSceneView(0.9f);
        scene.showBasePlate();

        BlockPos lampPos = util.grid().at(2, 1, 2);
        BlockPos canFillerPos = util.grid().at(1, 2, 5);
        BlockPos packagerPos = util.grid().at(2, 2, 5);
        BlockPos repackagerPos = util.grid().at(3, 2, 5);
        BlockPos frogportPos = util.grid().at(5, 2, 2);
        BlockPos chainLinkPos = util.grid().at(6, 5, 2);
        BlockPos postboxPos = util.grid().at(5, 2, 5);

        Selection lampSel = util.select().position(lampPos);
        Selection packagingSel = util.select().fromTo(1, 1, 5, 3, 2, 5);
        Selection frogportSel = util.select().position(frogportPos)
                .add(util.select().position(5, 1, 2))
                .add(util.select().fromTo(7, 1, 7, 7, 5, 7))
                .add(util.select().fromTo(7, 1, 0, 7, 5, 0))
                .add(util.select().fromTo(0, 1, 7, 0, 5, 7));
        Selection mailSel = util.select().position(postboxPos)
                .add(util.select().position(5, 1, 5));

        ItemStack baton = CFItems.BATON.asStack();
        Vec3 chainLinkPoint = util.vector().centerOf(chainLinkPos);

        scene.idle(20);

        scene.world().showSection(lampSel, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(70)
                .colored(PonderPalette.GREEN)
                .text(tr("fluid.ponder.baton_advanced.text_1"))
                .pointAt(util.vector().blockSurface(lampPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(80);

        scene.overlay().showControls(util.vector().blockSurface(lampPos, Direction.WEST), Pointing.RIGHT, 35)
                .rightClick()
                .withItem(baton);
        scene.world().cycleBlockProperty(lampPos, RoseQuartzLampBlock.POWERING);
        scene.idle(45);

        scene.world().showSection(packagingSel, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(85)
                .attachKeyFrame()
                .colored(PonderPalette.INPUT)
                .text(tr("fluid.ponder.baton_advanced.text_2"))
                .pointAt(util.vector().blockSurface(packagerPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(95);

        scene.overlay().showControls(util.vector().blockSurface(packagerPos, Direction.WEST), Pointing.RIGHT, 35)
                .rightClick()
                .withItem(baton);
        scene.world().modifyBlock(canFillerPos, state -> state.setValue(PackagerBlock.POWERED, true), false);
        scene.world().modifyBlock(packagerPos, state -> state.setValue(PackagerBlock.POWERED, true), false);
        scene.world().modifyBlock(repackagerPos, state -> state.setValue(PackagerBlock.POWERED, true), false);
        scene.idle(45);

        scene.world().showSection(frogportSel, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(85)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text(tr("fluid.ponder.baton_advanced.text_3"))
                .pointAt(util.vector().blockSurface(frogportPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(95);

        scene.overlay().showControls(util.vector().blockSurface(frogportPos, Direction.WEST), Pointing.RIGHT, 35)
                .rightClick()
                .withItem(baton);
        scene.idle(20);
        scene.overlay().showControls(chainLinkPoint, Pointing.DOWN, 35)
                .rightClick()
                .withItem(baton);
        AABB chainLinkMarker = new AABB(chainLinkPoint, chainLinkPoint).inflate(0.025, 0.025, 0.025);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, "frogport_chain_point", chainLinkMarker, 70);
        scene.overlay().showLine(PonderPalette.GREEN, util.vector().topOf(frogportPos.below()), chainLinkPoint, 70);
        scene.effects().indicateSuccess(chainLinkPos);
        scene.idle(80);

        scene.world().showSection(mailSel, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(85)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text(tr("fluid.ponder.baton_advanced.text_4"))
                .pointAt(util.vector().blockSurface(postboxPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(95);

        scene.overlay().showControls(util.vector().blockSurface(postboxPos, Direction.WEST), Pointing.RIGHT, 35)
                .rightClick()
                .withItem(baton);
        scene.overlay().showOutline(PonderPalette.BLUE, "mailbox_selected", util.select().position(postboxPos), 70);
        scene.effects().indicateSuccess(postboxPos);
        scene.idle(90);

        scene.markAsFinished();
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }
}
