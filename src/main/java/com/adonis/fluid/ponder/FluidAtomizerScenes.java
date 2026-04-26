package com.adonis.fluid.ponder;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

public class FluidAtomizerScenes {

    public static void processing(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("fluid_atomizer", "Fan Processing with Fluid Atomizer");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos atomizerPos = util.grid().at(2, 1, 2);
        BlockPos beltStartPos = util.grid().at(0, 1, 2);
        BlockPos beltEndPos = util.grid().at(4, 1, 2);
        BlockPos depotPos = util.grid().at(4, 1, 2);

        Selection atomizerSel = util.select().position(atomizerPos);
        Selection beltSel = util.select().fromTo(0, 1, 2, 4, 1, 2);
        Selection depotSel = util.select().position(depotPos);
        Selection gearsSel = util.select().fromTo(2, 1, 3, 2, 1, 4);

        scene.world().setKineticSpeed(atomizerSel, 0);
        scene.world().setKineticSpeed(beltSel, 0);
        scene.idle(20);

        scene.world().showSection(atomizerSel, Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(80)
                .colored(PonderPalette.GREEN)
                .text("流体雾化器是一种特殊的风扇，它内部的流体会决定气流的加工类型")
                .pointAt(util.vector().blockSurface(atomizerPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(beltSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(gearsSel, Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text("向雾化器中装入水后，气流会对传送带上的物品进行洗涤加工")
                .pointAt(util.vector().blockSurface(atomizerPos, Direction.UP))
                .placeNearTarget();
        scene.idle(90);

        // 模拟物品在传送带上被加工
        ItemStack gravel = new ItemStack(Items.GRAVEL);
        scene.world().createItemOnBeltLike(beltStartPos, Direction.EAST, gravel);
        scene.idle(20);

        scene.world().setKineticSpeed(atomizerSel, 32);
        scene.world().setKineticSpeed(beltSel, 16);
        scene.idle(30);

        scene.world().removeItemsFromBelt(beltEndPos);
        ItemStack flint = new ItemStack(Items.FLINT);
        scene.world().createItemOnBeltLike(beltEndPos, Direction.EAST, flint);
        scene.idle(20);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("装入岩浆后，气流会提供爆炸加工效果，效果与鼓风机经过岩浆时完全一致")
                .pointAt(util.vector().blockSurface(atomizerPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        scene.markAsFinished();
    }

    public static void potionCloud(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("fluid_atomizer_potion", "Potion Cloud from Atomizer");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos atomizerPos = util.grid().at(2, 1, 2);
        BlockPos zombiePos = util.grid().at(4, 1, 2);

        Selection atomizerSel = util.select().position(atomizerPos);
        Selection gearsSel = util.select().fromTo(2, 1, 3, 2, 1, 4);

        scene.world().setKineticSpeed(atomizerSel, 0);
        scene.idle(20);

        scene.world().showSection(atomizerSel, Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(80)
                .colored(PonderPalette.GREEN)
                .text("当雾化器内部装有药水流体时，会吹出带有药水效果的气流云雾")
                .pointAt(util.vector().blockSurface(atomizerPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(gearsSel, Direction.DOWN);
        scene.idle(5);
        scene.world().setKineticSpeed(atomizerSel, 32);
        scene.idle(10);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text("范围内的生物会受到药水效果影响，持续效果会被缩短以维持平衡")
                .pointAt(util.vector().centerOf(atomizerPos))
                .placeNearTarget();
        scene.idle(90);

        scene.markAsFinished();
    }
}
