package com.adonis.fluid.ponder;

import com.adonis.fluid.block.GutterOutlet.GutterOutletBlockEntity;
import com.adonis.fluid.registry.CFBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class LogisticsJunctionScenes {

    public static void item(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("logistics_junction_item", tr("fluid.ponder.logistics_junction_item.header"));
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(0.9f);
        scene.showBasePlate();

        BlockPos junctionPos = util.grid().at(3, 2, 3);
        BlockPos beltStartPos = util.grid().at(5, 1, 2);
        BlockPos funnelInputPos = util.grid().at(3, 2, 2);
        BlockPos sideChutePos = util.grid().at(2, 2, 3);
        BlockPos sideVaultPos = util.grid().at(2, 1, 3);
        BlockPos upperChutePos = util.grid().at(1, 4, 5);
        BlockPos upperVaultPos = util.grid().at(1, 3, 5);
        BlockPos stressInputPos = util.grid().at(3, 1, 3);

        Selection fullScene = util.select().fromTo(0, 1, 0, 7, 5, 7);
        Selection initialScene = util.select().position(junctionPos)
                .add(util.select().position(sideChutePos))
                .add(util.select().position(sideVaultPos))
                .add(util.select().position(upperChutePos))
                .add(util.select().position(upperVaultPos))
                .add(util.select().position(1, 1, 5))
                .add(util.select().position(1, 2, 5))
                .add(util.select().position(stressInputPos));
        Selection inputAssembly = fullScene.substract(initialScene);
        Selection junction = util.select().position(junctionPos);
        Selection beltInput = util.select().fromTo(3, 1, 2, 5, 2, 2);
        Selection inputKinetics = beltInput
                .add(util.select().position(7, 0, 3))
                .add(util.select().position(3, 1, 3))
                .add(util.select().position(3, 1, 4))
                .add(util.select().position(4, 1, 4))
                .add(util.select().position(5, 1, 3))
                .add(util.select().position(5, 1, 4))
                .add(util.select().position(6, 1, 4))
                .add(util.select().position(7, 1, 4));
        Selection sideOutput = util.select().position(sideChutePos);
        Selection upperOutput = util.select().position(upperChutePos);
        Selection stressInput = util.select().position(stressInputPos);
        Selection inputFaces = util.select().position(funnelInputPos)
                .add(util.select().position(4, 2, 3))
                .add(util.select().position(3, 2, 4));

        scene.idle(15);
        scene.world().showSection(initialScene, Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text(tr("fluid.ponder.logistics_junction_item.text_1"))
                .pointAt(util.vector().centerOf(junctionPos))
                .placeNearTarget();
        scene.idle(100);

        scene.overlay().showOutline(PonderPalette.INPUT, "item_input_faces", inputFaces, 100);
        scene.overlay().showText(90)
                .colored(PonderPalette.INPUT)
                .text(tr("fluid.ponder.logistics_junction_item.text_2"))
                .pointAt(util.vector().blockSurface(funnelInputPos, Direction.NORTH))
                .placeNearTarget();
        scene.idle(100);

        scene.overlay().showOutline(PonderPalette.OUTPUT, "side_chute", sideOutput, 100);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .colored(PonderPalette.OUTPUT)
                .text(tr("fluid.ponder.logistics_junction_item.text_3"))
                .pointAt(util.vector().blockSurface(sideChutePos, Direction.WEST))
                .placeNearTarget();
        scene.idle(100);

        scene.overlay().showOutline(PonderPalette.OUTPUT, "upper_chute", upperOutput, 110);
        scene.overlay().showControls(util.vector().blockSurface(upperChutePos, Direction.WEST), Pointing.RIGHT, 45)
                .rightClick()
                .withItem(CFBlocks.LOGISTICS_JUNCTION.asStack());
        scene.overlay().showText(110)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text(tr("fluid.ponder.logistics_junction_item.text_4"))
                .pointAt(util.vector().centerOf(upperChutePos))
                .placeNearTarget();
        scene.idle(120);

        scene.overlay().showOutline(PonderPalette.RED, "stress_input", stressInput, 80);
        scene.overlay().showText(80)
                .colored(PonderPalette.RED)
                .text(tr("fluid.ponder.logistics_junction_item.text_5"))
                .pointAt(util.vector().blockSurface(stressInputPos, Direction.DOWN))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(inputAssembly, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.INPUT, "belt_input", beltInput, 100);
        scene.world().setKineticSpeed(inputKinetics, 16);
        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text(tr("fluid.ponder.logistics_junction_item.text_6"))
                .pointAt(util.vector().blockSurface(funnelInputPos, Direction.NORTH))
                .placeNearTarget();
        scene.world().createItemOnBelt(beltStartPos, Direction.UP, AllItems.ANDESITE_ALLOY.asStack());
        scene.idle(60);
        scene.world().flapFunnel(funnelInputPos, false);
        scene.world().removeItemsFromBelt(funnelInputPos.below().east());
        scene.world().removeItemsFromBelt(funnelInputPos.below());
        scene.idle(8);
        scene.world().createItemOnBelt(beltStartPos, Direction.UP, AllItems.BRASS_INGOT.asStack());
        scene.idle(60);
        scene.world().removeItemsFromBelt(funnelInputPos.below().east());
        scene.world().removeItemsFromBelt(funnelInputPos.below());
        scene.world().flapFunnel(funnelInputPos, false);
        scene.idle(35);

        scene.overlay().showControls(util.vector().topOf(sideChutePos), Pointing.DOWN, 80)
                .withItem(AllItems.ANDESITE_ALLOY.asStack());
        scene.overlay().showControls(util.vector().topOf(upperChutePos), Pointing.DOWN, 80)
                .withItem(AllItems.BRASS_INGOT.asStack());
        scene.idle(40);
        scene.overlay().showText(110)
                .attachKeyFrame()
                .colored(PonderPalette.OUTPUT)
                .text(tr("fluid.ponder.logistics_junction_item.text_7"))
                .pointAt(util.vector().centerOf(junctionPos))
                .placeNearTarget();
        scene.idle(120);

        scene.markAsFinished();
    }

    public static void fluid(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("logistics_junction_fluid", tr("fluid.ponder.logistics_junction_fluid.header"));
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(0.9f);
        scene.showBasePlate();

        BlockPos junctionPos = util.grid().at(3, 2, 3);
        BlockPos pipeInputPos = util.grid().at(3, 2, 2);
        BlockPos sideOutletPos = util.grid().at(2, 2, 3);
        BlockPos upperOutletPos = util.grid().at(1, 4, 5);
        BlockPos stressInputPos = util.grid().at(3, 1, 3);
        BlockPos sourceTankPos = util.grid().at(6, 1, 0);
        BlockPos upperTankPos = util.grid().at(1, 2, 5);

        Selection fullScene = util.select().fromTo(0, 1, 0, 7, 5, 7);
        Selection initialScene = util.select().position(junctionPos)
                .add(util.select().position(sideOutletPos))
                .add(util.select().position(2, 1, 3))
                .add(util.select().fromTo(1, 2, 5, 1, 4, 5))
                .add(util.select().position(1, 1, 5))
                .add(util.select().position(stressInputPos));
        Selection inputAssembly = fullScene.substract(initialScene);
        Selection pipeInput = util.select().fromTo(3, 1, 0, 3, 2, 2)
                .add(util.select().fromTo(4, 1, 0, 6, 3, 0));
        Selection sideOutput = util.select().position(sideOutletPos);
        Selection upperOutput = util.select().position(upperOutletPos);
        Selection stressInput = util.select().position(stressInputPos);
        Selection inputFaces = util.select().position(pipeInputPos)
                .add(util.select().position(4, 2, 3))
                .add(util.select().position(3, 2, 4));

        scene.idle(15);
        scene.world().showSection(initialScene, Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text(tr("fluid.ponder.logistics_junction_fluid.text_1"))
                .pointAt(util.vector().centerOf(junctionPos))
                .placeNearTarget();
        scene.idle(100);

        scene.overlay().showOutline(PonderPalette.INPUT, "fluid_input_faces", inputFaces, 100);
        scene.overlay().showText(90)
                .colored(PonderPalette.INPUT)
                .text(tr("fluid.ponder.logistics_junction_fluid.text_2"))
                .pointAt(util.vector().blockSurface(pipeInputPos, Direction.NORTH))
                .placeNearTarget();
        scene.idle(100);

        scene.overlay().showOutline(PonderPalette.BLUE, "side_gutter", sideOutput, 100);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .text(tr("fluid.ponder.logistics_junction_fluid.text_3"))
                .pointAt(util.vector().blockSurface(sideOutletPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(100);

        scene.overlay().showOutline(PonderPalette.BLUE, "upper_gutter", upperOutput, 110);
        scene.overlay().showControls(util.vector().blockSurface(upperOutletPos, Direction.WEST), Pointing.RIGHT, 45)
                .rightClick()
                .withItem(CFBlocks.LOGISTICS_JUNCTION.asStack());
        scene.overlay().showText(110)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text(tr("fluid.ponder.logistics_junction_fluid.text_4"))
                .pointAt(util.vector().centerOf(upperOutletPos))
                .placeNearTarget();
        scene.idle(120);

        scene.overlay().showOutline(PonderPalette.RED, "fluid_stress_input", stressInput, 80);
        scene.overlay().showText(80)
                .colored(PonderPalette.RED)
                .text(tr("fluid.ponder.logistics_junction_fluid.text_5"))
                .pointAt(util.vector().blockSurface(stressInputPos, Direction.DOWN))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(inputAssembly, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.INPUT, "pipe_input", pipeInput, 100);
        scene.world().modifyBlockEntity(sourceTankPos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE);
        });
        scene.world().propagatePipeChange(sourceTankPos);
        scene.overlay().showControls(util.vector().topOf(sourceTankPos), Pointing.DOWN, 40)
                .withItem(Items.WATER_BUCKET.getDefaultInstance());
        scene.idle(35);
        scene.world().modifyBlockEntity(upperTankPos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        });
        scene.world().modifyBlockEntity(sourceTankPos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().drain(2000, IFluidHandler.FluidAction.EXECUTE);
            be.getTankInventory().fill(new FluidStack(Fluids.LAVA, 2000), IFluidHandler.FluidAction.EXECUTE);
        });
        scene.world().propagatePipeChange(sourceTankPos);
        scene.overlay().showControls(util.vector().topOf(sourceTankPos), Pointing.DOWN, 40)
                .withItem(Items.LAVA_BUCKET.getDefaultInstance());
        scene.idle(35);
        scene.world().modifyBlockEntity(sourceTankPos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().drain(2000, IFluidHandler.FluidAction.EXECUTE);
        });
        scene.world().modifyBlockEntity(sideOutletPos, GutterOutletBlockEntity.class, be -> {
            be.getTankInventory().fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);
        });
        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text(tr("fluid.ponder.logistics_junction_fluid.text_6"))
                .pointAt(util.vector().blockSurface(pipeInputPos, Direction.NORTH))
                .placeNearTarget();
        scene.idle(110);

        scene.overlay().showText(110)
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .text(tr("fluid.ponder.logistics_junction_fluid.text_7"))
                .pointAt(util.vector().centerOf(junctionPos))
                .placeNearTarget();
        scene.idle(120);

        scene.markAsFinished();
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }
}
