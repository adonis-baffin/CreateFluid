package com.adonis.fluid.ponder;

import java.util.List;

import com.adonis.fluid.item.CopperCanItem;
import com.adonis.fluid.registry.CFBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class SmartRepackagerScenes {

    public static void processing(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("smart_repackager", tr("fluid.ponder.smart_repackager.header"));
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.925f);
        scene.setSceneOffsetY(-0.5f);

        BlockPos networkPackager = util.grid().at(2, 1, 7);
        BlockPos stockLink = util.grid().at(2, 2, 7);
        BlockPos vault = util.grid().at(4, 2, 7);
        BlockPos smartRepackager = util.grid().at(2, 1, 1);
        BlockPos sourceBarrel = util.grid().at(1, 1, 1);
        BlockPos canFiller = util.grid().at(2, 1, 3);
        BlockPos fluidTank = util.grid().at(2, 1, 4);
        BlockPos junction = util.grid().at(6, 1, 1);
        BlockPos junctionInputFunnel = util.grid().at(5, 1, 1);
        BlockPos upperDepot = util.grid().at(7, 1, 1);
        BlockPos spout = util.grid().at(7, 3, 1);

        Selection floor = util.select().fromTo(0, 0, 0, 8, 0, 8);
        Selection vaultAndNetwork = util.select().fromTo(3, 1, 6, 5, 2, 7)
                .add(util.select().fromTo(2, 1, 7, 2, 2, 7));
        Selection board = util.select().fromTo(3, 2, 3, 6, 4, 4)
                .add(util.select().fromTo(3, 1, 4, 5, 1, 4));
        Selection smartCell = util.select().position(smartRepackager)
                .add(util.select().position(sourceBarrel))
                .add(util.select().position(1, 1, 2))
                .add(util.select().position(1, 1, 3));
        Selection canCell = util.select().position(canFiller)
                .add(util.select().fromTo(2, 1, 4, 2, 2, 4))
                .add(util.select().position(2, 1, 0))
                .add(util.select().position(3, 1, 1));
        Selection belts = util.select().fromTo(1, 0, 7, 1, 0, 2)
                .add(util.select().fromTo(3, 0, 1, 5, 0, 1))
                .add(util.select().fromTo(6, 0, 1, 7, 0, 1));
        Selection junctionCell = util.select().position(junction)
                .add(util.select().position(junctionInputFunnel))
                .add(util.select().position(6, 1, 1))
                .add(util.select().position(7, 1, 1))
                .add(util.select().position(7, 3, 1))
                .add(util.select().position(7, 2, 1));
        Selection kinetics = belts
                .add(util.select().fromTo(1, 0, 7, 7, 1, 7))
                .add(util.select().fromTo(7, 0, 5, 7, 1, 7));

        scene.world().showSection(floor, Direction.UP);
        scene.idle(10);

        scene.world().showSection(vaultAndNetwork, Direction.NORTH);
        scene.idle(10);
        scene.world().showSection(board, Direction.SOUTH);
        scene.idle(20);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .text(tr("fluid.ponder.smart_repackager.text_1"))
                .pointAt(util.vector().centerOf(vault))
                .placeNearTarget();
        scene.idle(100);

        scene.overlay().showControls(util.vector().blockSurface(stockLink, Direction.WEST), Pointing.RIGHT, 45)
                .rightClick()
                .withItem(CFBlocks.SMART_REPACKAGER.asStack());
        scene.overlay().showText(80)
                .colored(PonderPalette.WHITE)
                .text(tr("fluid.ponder.smart_repackager.text_2"))
                .pointAt(util.vector().centerOf(stockLink))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(smartCell, Direction.DOWN);
        scene.idle(15);

        scene.overlay().showOutline(PonderPalette.GREEN, "smart_repackager", util.select().position(smartRepackager), 90);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text(tr("fluid.ponder.smart_repackager.text_3"))
                .pointAt(util.vector().blockSurface(smartRepackager, Direction.UP))
                .placeNearTarget();
        scene.idle(100);

        scene.world().showSection(canCell, Direction.DOWN);
        scene.idle(15);
        scene.world().modifyBlockEntity(fluidTank, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE);
        });
        scene.overlay().showText(90)
                .attachKeyFrame()
                .colored(PonderPalette.INPUT)
                .text(tr("fluid.ponder.smart_repackager.text_4"))
                .pointAt(util.vector().blockSurface(canFiller, Direction.WEST))
                .placeNearTarget();
        scene.idle(100);

        scene.world().showSection(belts, Direction.DOWN);
        scene.world().setKineticSpeed(kinetics, -16);
        scene.idle(10);

        ItemStack itemFragment = PackageItem.containing(List.of(AllItems.ANDESITE_ALLOY.asStack()));
        PackageItem.addAddress(itemFragment, "Sorting");
        ItemStack waterCan = CopperCanItem.create(new FluidStack(Fluids.WATER, 1000), 1000);
        PackageItem.addAddress(waterCan, "Sorting");

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .text(tr("fluid.ponder.smart_repackager.text_5"))
                .pointAt(util.vector().blockSurface(networkPackager, Direction.WEST))
                .placeNearTarget();
        scene.idle(30);
        packagerCreate(scene, networkPackager, itemFragment);
        scene.idle(20);
        packagerClear(scene, networkPackager);
        scene.world().createItemOnBelt(util.grid().at(1, 0, 7), Direction.EAST, itemFragment);
        scene.idle(55);

        scene.world().flapFunnel(util.grid().at(1, 1, 2), false);
        scene.world().removeItemsFromBelt(util.grid().at(1, 0, 2));
        scene.idle(10);
        packagerUnpack(scene, smartRepackager, itemFragment);
        scene.idle(25);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text(tr("fluid.ponder.smart_repackager.text_6"))
                .pointAt(util.vector().blockSurface(smartRepackager, Direction.EAST))
                .placeNearTarget();
        scene.idle(100);

        packagerCreate(scene, smartRepackager, itemFragment);
        scene.idle(20);
        packagerClear(scene, smartRepackager);
        scene.world().createItemOnBelt(util.grid().at(3, 0, 1), Direction.WEST, itemFragment);
        scene.idle(30);

        scene.world().showSection(junctionCell, Direction.WEST);
        scene.idle(15);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "junction", util.select().position(junction), 90);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .colored(PonderPalette.OUTPUT)
                .text(tr("fluid.ponder.smart_repackager.text_7"))
                .pointAt(util.vector().blockSurface(junction, Direction.WEST))
                .placeNearTarget();
        scene.idle(95);

        scene.world().removeItemsFromBelt(util.grid().at(5, 0, 1));
        scene.world().flapFunnel(junctionInputFunnel, false);
        scene.overlay().showControls(util.vector().topOf(upperDepot), Pointing.DOWN, 60)
                .withItem(AllItems.ANDESITE_ALLOY.asStack());
        scene.idle(35);

        scene.overlay().showText(85)
                .attachKeyFrame()
                .colored(PonderPalette.INPUT)
                .text(tr("fluid.ponder.smart_repackager.text_8"))
                .pointAt(util.vector().blockSurface(canFiller, Direction.WEST))
                .placeNearTarget();
        scene.idle(35);
        packagerCreate(scene, canFiller, waterCan);
        scene.world().modifyBlockEntity(fluidTank, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().drain(1000, IFluidHandler.FluidAction.EXECUTE);
        });
        scene.idle(20);
        packagerClear(scene, canFiller);
        scene.world().flapFunnel(util.grid().at(3, 1, 1), false);
        scene.world().createItemOnBelt(util.grid().at(3, 0, 1), Direction.WEST, waterCan);
        scene.idle(35);

        scene.world().removeItemsFromBelt(util.grid().at(5, 0, 1));
        scene.world().flapFunnel(junctionInputFunnel, false);
        scene.overlay().showControls(util.vector().blockSurface(spout, Direction.WEST), Pointing.RIGHT, 60)
                .withItem(Items.WATER_BUCKET.getDefaultInstance());
        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .text(tr("fluid.ponder.smart_repackager.text_9"))
                .pointAt(util.vector().centerOf(junction))
                .placeNearTarget();
        scene.idle(110);

        scene.markAsFinished();
    }

    private static void packagerCreate(CreateSceneBuilder scene, BlockPos pos, ItemStack box) {
        scene.world().modifyBlockEntity(pos, PackagerBlockEntity.class, be -> {
            be.animationTicks = PackagerBlockEntity.CYCLE;
            be.animationInward = false;
            be.heldBox = box;
        });
    }

    private static void packagerUnpack(CreateSceneBuilder scene, BlockPos pos, ItemStack box) {
        scene.world().modifyBlockEntity(pos, PackagerBlockEntity.class, be -> {
            be.animationTicks = PackagerBlockEntity.CYCLE;
            be.animationInward = true;
            be.previouslyUnwrapped = box;
        });
    }

    private static void packagerClear(CreateSceneBuilder scene, BlockPos pos) {
        scene.world().modifyBlockEntity(pos, PackagerBlockEntity.class, be -> {
            be.heldBox = ItemStack.EMPTY;
        });
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }
}
