package com.adonis.fluid.ponder;

import com.adonis.fluid.registry.CFBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class CommunicatingVesselScenes {

    public static void balancing(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("communicating_vessel", "Fluid Balancing with Communicating Vessel");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos vesselStart = util.grid().at(1, 1, 2);
        BlockPos vesselEnd = util.grid().at(3, 1, 2);
        BlockPos tankAPos = util.grid().at(0, 1, 2);
        BlockPos tankBPos = util.grid().at(4, 1, 2);

        Selection vesselSel = util.select().fromTo(1, 1, 2, 3, 1, 2);
        Selection tankASel = util.select().fromTo(0, 1, 2, 0, 3, 2);
        Selection tankBSel = util.select().fromTo(4, 1, 2, 4, 3, 2);

        scene.idle(20);

        scene.world().showSection(tankASel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(tankBSel, Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(80)
                .colored(PonderPalette.GREEN)
                .text("连通器可以连接两端的流体容器，根据液位高度差自动平衡流体")
                .pointAt(util.vector().blockSurface(vesselStart, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(vesselSel, Direction.DOWN);
        scene.idle(10);

        // 初始化：A罐满，B罐空
        scene.world().modifyBlockEntity(tankAPos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().fill(new FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE);
        });
        scene.idle(20);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text("当一侧液位较高时，连通器会自动将流体转移至低液位一侧，直到两边平衡")
                .pointAt(util.vector().blockSurface(vesselStart, Direction.UP))
                .placeNearTarget();
        scene.idle(90);

        // 模拟平衡效果（减少A，增加B）
        scene.world().modifyBlockEntity(tankAPos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().drain(2000, IFluidHandler.FluidAction.EXECUTE);
        });
        scene.world().modifyBlockEntity(tankBPos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE);
        });
        scene.idle(20);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("连通器支持各种容器，包括创造模式的无限流体源")
                .pointAt(util.vector().blockSurface(tankBPos, Direction.EAST))
                .placeNearTarget();
        scene.idle(90);

        scene.markAsFinished();
    }
}
