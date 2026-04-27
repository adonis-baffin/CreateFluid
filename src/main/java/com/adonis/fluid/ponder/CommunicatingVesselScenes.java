package com.adonis.fluid.ponder;

import com.simibubi.create.content.fluids.FluidFX;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity.CreativeSmartFluidTank;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.VecHelper;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class CommunicatingVesselScenes {

    public static void balancing(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("communicating_vessel", "Balancing Fluids with Communicating Vessels");
        scene.configureBasePlate(0, 0, 6);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos frontTankA = util.grid().at(4, 1, 1);
        BlockPos frontTankB = util.grid().at(0, 1, 1);
        BlockPos creativeTank = util.grid().at(0, 1, 5);
        BlockPos outputTank = util.grid().at(4, 1, 5);

        Selection frontTanks = util.select().fromTo(0, 1, 1, 0, 3, 1)
                .add(util.select().fromTo(4, 1, 1, 4, 3, 1));
        Selection frontVessels = util.select().fromTo(1, 1, 1, 3, 1, 1);
        Selection backSetup = util.select().fromTo(0, 1, 5, 4, 3, 5);

        scene.world().showSection(frontTanks, Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(frontVessels, Direction.DOWN);
        scene.idle(15);

        fillTank(scene, frontTankA, new FluidStack(Fluids.WATER, 24000));
        fillTank(scene, frontTankB, FluidStack.EMPTY);
        scene.idle(10);

        scene.overlay().showText(90)
                .colored(PonderPalette.GREEN)
                .text(tr("fluid.ponder.communicating_vessel.text_1"))
                .pointAt(util.vector().centerOf(2, 1, 1))
                .placeNearTarget();
        scene.idle(100);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text(tr("fluid.ponder.communicating_vessel.text_2"))
                .pointAt(util.vector().centerOf(2, 1, 1))
                .placeNearTarget();
        scene.idle(70);

        int[] transfers = new int[] {4000, 4000, 4000};
        for (int amount : transfers) {
            transfer(scene, frontTankA, frontTankB, Fluids.WATER, amount);
            spawnFlow(scene, util.vector().centerOf(3, 1, 1), util.vector().centerOf(1, 1, 1),
                    new FluidStack(Fluids.WATER, amount), 12, 0.05f);
            scene.idle(18);
        }
        scene.idle(20);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .text(tr("fluid.ponder.communicating_vessel.text_3"))
                .pointAt(util.vector().blockSurface(frontTankB, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(backSetup, Direction.DOWN);
        scene.idle(15);

        fillCreativeTank(scene, creativeTank, new FluidStack(Fluids.WATER, 8000));
        fillTank(scene, outputTank, FluidStack.EMPTY);
        scene.idle(10);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text(tr("fluid.ponder.communicating_vessel.text_4"))
                .pointAt(util.vector().centerOf(2, 1, 5))
                .placeNearTarget();
        scene.idle(90);

        for (int i = 0; i < 3; i++) {
            addToTank(scene, outputTank, new FluidStack(Fluids.WATER, 8000));
            spawnFlow(scene, util.vector().centerOf(1, 1, 5), util.vector().centerOf(3, 1, 5),
                    new FluidStack(Fluids.WATER, 1000), 12, 0.05f);
            scene.idle(18);
        }
        scene.idle(25);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text(tr("fluid.ponder.communicating_vessel.text_5"))
                .pointAt(util.vector().blockSurface(outputTank, Direction.WEST))
                .placeNearTarget();
        scene.idle(100);

        scene.markAsFinished();
    }

    private static void fillTank(CreateSceneBuilder scene, BlockPos pos, FluidStack fluidStack) {
        scene.world().modifyBlockEntity(pos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
            be.getTankInventory().fill(fluidStack.copy(), IFluidHandler.FluidAction.EXECUTE);
        });
    }

    private static void fillCreativeTank(CreateSceneBuilder scene, BlockPos pos, FluidStack fluidStack) {
        scene.world().modifyBlockEntity(pos, CreativeFluidTankBlockEntity.class,
                be -> ((CreativeSmartFluidTank) be.getTankInventory()).setContainedFluid(fluidStack.copy()));
    }

    private static void transfer(CreateSceneBuilder scene, BlockPos from, BlockPos to, net.minecraft.world.level.material.Fluid fluid, int amount) {
        scene.world().modifyBlockEntity(from, FluidTankBlockEntity.class,
                be -> be.getTankInventory().drain(amount, IFluidHandler.FluidAction.EXECUTE));
        scene.world().modifyBlockEntity(to, FluidTankBlockEntity.class,
                be -> be.getTankInventory().fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE));
    }

    private static void addToTank(CreateSceneBuilder scene, BlockPos pos, FluidStack fluidStack) {
        scene.world().modifyBlockEntity(pos, FluidTankBlockEntity.class,
                be -> be.getTankInventory().fill(fluidStack.copy(), IFluidHandler.FluidAction.EXECUTE));
    }

    private static void spawnFlow(CreateSceneBuilder scene, Vec3 start, Vec3 end, FluidStack fluidStack, int particles, float spread) {
        ParticleOptions particle = FluidFX.getFluidParticle(fluidStack);
        RandomSource random = scene.getScene().getWorld().random;
        Vec3 line = end.subtract(start);
        Vec3 motion = line.normalize().scale(0.045f);

        for (int i = 0; i < particles; i++) {
            float t = (i + 1f) / particles;
            Vec3 pos = start.add(line.scale(t));
            pos = pos.add(VecHelper.offsetRandomly(Vec3.ZERO, random, spread));
            scene.effects().emitParticles(pos,
                    scene.effects().simpleParticleEmitter(particle,
                            VecHelper.offsetRandomly(motion, random, spread * 0.4f)),
                    0.15f, 1);
        }
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }
}
