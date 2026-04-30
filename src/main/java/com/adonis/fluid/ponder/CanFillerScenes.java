package com.adonis.fluid.ponder;

import com.adonis.fluid.item.CopperCanItem;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class CanFillerScenes {

    public static void filling(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("can_filler", tr("fluid.ponder.can_filler.header"));
        scene.configureBasePlate(0, 0, 8);
        scene.scaleSceneView(0.9f);
        scene.showBasePlate();

        BlockPos tank1Pos = util.grid().at(1, 2, 4);
        BlockPos canFiller1Pos = util.grid().at(1, 2, 3);
        BlockPos leverPos = util.grid().at(1, 3, 3);
        BlockPos beltStartPos = util.grid().at(1, 1, 2);
        BlockPos beltEndPos = util.grid().at(5, 1, 2);
        BlockPos funnel1Pos = util.grid().at(1, 2, 2);
        BlockPos funnel2Pos = util.grid().at(5, 2, 2);
        BlockPos canFiller2Pos = util.grid().at(6, 2, 2);
        BlockPos tank2Pos = util.grid().at(6, 2, 3);

        Selection tank1Sel = util.select().fromTo(1, 1, 4, 1, 3, 4);
        Selection canFiller1Sel = util.select().fromTo(1, 1, 3, 1, 3, 3);
        Selection beltSel = util.select().fromTo(0, 1, 2, 7, 1, 2)
                .add(util.select().position(7, 1, 3))
                .add(util.select().position(8, 0, 3));
        Selection funnel1Sel = util.select().position(funnel1Pos);
        Selection funnel2Sel = util.select().position(funnel2Pos);
        Selection canFiller2Sel = util.select().position(canFiller2Pos);
        Selection tank2Sel = util.select().fromTo(6, 1, 3, 6, 3, 3);

        scene.idle(20);

        scene.world().showSection(tank1Sel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(canFiller1Sel, Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(80)
                .colored(PonderPalette.GREEN)
                .text(tr("fluid.ponder.can_filler.text_1"))
                .pointAt(util.vector().blockSurface(canFiller1Pos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(beltSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(funnel1Sel, Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text(tr("fluid.ponder.can_filler.text_2"))
                .pointAt(util.vector().blockSurface(funnel1Pos, Direction.SOUTH))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(funnel2Sel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(canFiller2Sel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(tank2Sel, Direction.DOWN);
        scene.idle(10);

        scene.world().modifyBlockEntity(tank1Pos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().fill(new FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE);
        });
        scene.idle(10);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.INPUT)
                .text(tr("fluid.ponder.can_filler.text_3"))
                .pointAt(util.vector().blockSurface(tank1Pos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        ItemStack waterCan = CopperCanItem.create(new FluidStack(Fluids.WATER, 1000), 1000);
        scene.world().toggleRedstonePower(util.select().position(leverPos));
        scene.world().modifyBlock(canFiller1Pos, state -> state.setValue(PackagerBlock.POWERED, true), true);
        scene.idle(10);
        canFillerCreate(scene, canFiller1Pos, waterCan);
        scene.idle(5);
        scene.world().modifyBlockEntity(tank1Pos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().drain(1000, IFluidHandler.FluidAction.EXECUTE);
        });
        scene.idle(20);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.OUTPUT)
                .text(tr("fluid.ponder.can_filler.text_4"))
                .pointAt(util.vector().blockSurface(canFiller1Pos, Direction.UP))
                .placeNearTarget();
        scene.idle(90);

        canFillerClear(scene, canFiller1Pos);
        scene.world().setKineticSpeed(beltSel, -16);
        scene.world().flapFunnel(funnel1Pos, false);
        scene.world().createItemOnBelt(beltStartPos, Direction.UP, waterCan);
        scene.idle(20);

        scene.overlay().showText(70)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text(tr("fluid.ponder.can_filler.text_5"))
                .pointAt(util.vector().blockSurface(beltStartPos, Direction.UP))
                .placeNearTarget();
        scene.idle(100);

        scene.world().removeItemsFromBelt(beltEndPos);
        scene.world().flapFunnel(funnel2Pos, false);
        scene.idle(10);

        canFillerUnpack(scene, canFiller2Pos, waterCan);
        scene.idle(5);
        scene.world().modifyBlockEntity(tank2Pos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        });
        scene.idle(20);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text(tr("fluid.ponder.can_filler.text_6"))
                .pointAt(util.vector().blockSurface(canFiller2Pos, Direction.EAST))
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text(tr("fluid.ponder.can_filler.text_7"))
                .pointAt(util.vector().blockSurface(tank2Pos, Direction.EAST))
                .placeNearTarget();
        scene.idle(110);

        scene.markAsFinished();
    }

    private static void canFillerCreate(CreateSceneBuilder scene, BlockPos pos, ItemStack can) {
        scene.world().modifyBlockEntity(pos, PackagerBlockEntity.class, be -> {
            be.animationTicks = PackagerBlockEntity.CYCLE;
            be.animationInward = false;
            be.heldBox = can;
        });
    }

    private static void canFillerUnpack(CreateSceneBuilder scene, BlockPos pos, ItemStack can) {
        scene.world().modifyBlockEntity(pos, PackagerBlockEntity.class, be -> {
            be.animationTicks = PackagerBlockEntity.CYCLE;
            be.animationInward = true;
            be.previouslyUnwrapped = can;
        });
    }

    private static void canFillerClear(CreateSceneBuilder scene, BlockPos pos) {
        scene.world().modifyBlockEntity(pos, PackagerBlockEntity.class, be -> {
            be.heldBox = ItemStack.EMPTY;
        });
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }
}
