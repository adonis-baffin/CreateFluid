package com.adonis.fluid.ponder;

import com.adonis.fluid.block.Pipette.PipetteBlockEntity;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.nbt.NBTHelper;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class PipetteScenes {

    public static void setup(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("mechanical_pipette", "Setting up Mechanical Pipette");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        // 定义位置
        BlockPos pipettePos = util.grid().at(2, 1, 2);
        BlockPos basinPos = util.grid().at(0, 1, 1);
        BlockPos splashingPos = util.grid().at(4, 1, 1);
        BlockPos tankPos = util.grid().at(4, 1, 3);
        BlockPos fluidInterfacePos = util.grid().at(4, 2, 2);
        BlockPos blazeBurnerPos = util.grid().at(2, 1, 0);
        BlockPos beehivePos = util.grid().at(3, 3, 4);
        BlockPos beenestPos = util.grid().at(1, 2, 4);

        // 定义选择区域
        Selection pipetteSel = util.select().position(pipettePos);
        Selection basinSel = util.select().position(basinPos);
        Selection splashingSel = util.select().position(splashingPos);
        Selection tankSel = util.select().fromTo(4, 1, 3, 4, 3, 3);
        Selection fluidInterfaceSel = util.select().position(fluidInterfacePos);
        Selection blazeBurnerSel = util.select().position(blazeBurnerPos);
        Selection beehiveSel = util.select().position(beehivePos);
        Selection beenestSel = util.select().position(beenestPos);

        Selection beehivePillarSel = util.select().fromTo(3, 1, 4, 3, 3, 4);
        Selection beenestPillarSel = util.select().fromTo(1, 1, 4, 1, 2, 4);

        Selection gearsSel = util.select().fromTo(2, 1, 5, 2, 1, 3)
                .add(util.select().position(2, 0, 5));

        scene.world().setKineticSpeed(pipetteSel, 0);
        scene.world().setKineticSpeed(gearsSel, 0);

        scene.idle(20);

        scene.world().showSection(pipetteSel, Direction.DOWN);
        scene.idle(10);

        scene.world().showSection(basinSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(splashingSel, Direction.DOWN);
        scene.idle(15);

        scene.overlay().showOutlineWithText(splashingSel, 40)
                .colored(PonderPalette.INPUT)
                .text("输入")
                .pointAt(util.vector().blockSurface(splashingPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(5);

        scene.overlay().showOutlineWithText(basinSel, 40)
                .colored(PonderPalette.OUTPUT)
                .text("输出")
                .pointAt(util.vector().blockSurface(basinPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(40);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("动力移液器与动力臂的工作方式别无二致，但是正如其名，它专注于与流体容器交互")
                .pointAt(util.vector().blockSurface(pipettePos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        // 第一次动作：从分液池到工作盆
        scene.world().showSection(gearsSel, Direction.DOWN);
        scene.idle(10);

        scene.world().modifyBlockEntity(splashingPos, ItemDrainBlockEntity.class, (be) -> {
            SmartFluidTankBehaviour tankBehaviour = be.getBehaviour(SmartFluidTankBehaviour.TYPE);
            if (tankBehaviour != null) {
                tankBehaviour.allowInsertion();
                be.getCapability(ForgeCapabilities.FLUID_HANDLER).ifPresent((fh) -> {
                    fh.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
                });
                tankBehaviour.forbidInsertion();
            }
        });
        scene.idle(5);

        scene.world().setKineticSpeed(pipetteSel, -48);
        scene.world().setKineticSpeed(gearsSel, -48);
        scene.world().multiplyKineticSpeed(util.select().position(2, 1, 5), -1);
        scene.idle(20);

        instructPipette(scene, pipettePos, PipetteBlockEntity.Phase.MOVE_TO_INPUT, FluidStack.EMPTY, 0);
        scene.idle(24);

        scene.world().modifyBlockEntity(splashingPos, ItemDrainBlockEntity.class, (be) -> {
            be.getCapability(ForgeCapabilities.FLUID_HANDLER).ifPresent((fh) -> {
                fh.drain(1000, IFluidHandler.FluidAction.EXECUTE);
            });
        });
        scene.idle(10);

        instructPipette(scene, pipettePos, PipetteBlockEntity.Phase.SEARCH_OUTPUTS,
                new FluidStack(Fluids.WATER, 1000), -1);
        scene.idle(20);

        instructPipette(scene, pipettePos, PipetteBlockEntity.Phase.MOVE_TO_OUTPUT,
                new FluidStack(Fluids.WATER, 1000), 0);
        scene.idle(24);

        scene.world().modifyBlockEntity(basinPos, BasinBlockEntity.class, (be) -> {
            be.getCapability(ForgeCapabilities.FLUID_HANDLER).ifPresent((fh) -> {
                fh.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
            });
        });
        scene.idle(10);

        instructPipette(scene, pipettePos, PipetteBlockEntity.Phase.SEARCH_INPUTS, FluidStack.EMPTY, -1);
        scene.idle(20);

        // 显示储罐和流体接口
        scene.world().showSection(tankSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(fluidInterfaceSel, Direction.DOWN);
        scene.idle(10);

//        scene.world().modifyBlockEntity(tankPos, FluidTankBlockEntity.class, (be) -> {
//            be.getTankInventory().fill(new FluidStack(Fluids.LAVA, 2000), IFluidHandler.FluidAction.EXECUTE);
//        });
//        scene.idle(10);

        scene.overlay().showOutlineWithText(fluidInterfaceSel, 80)
                .attachKeyFrame()
                .colored(PonderPalette.OUTPUT)
                .text("对于某些无法直接交互的流体容器，流体接口可以解决此问题")
                .pointAt(util.vector().blockSurface(fluidInterfacePos, Direction.NORTH))
                .placeNearTarget();
        scene.idle(90);

        // 显示特殊方块
        scene.world().showSection(blazeBurnerSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(beehivePillarSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(beenestPillarSel, Direction.DOWN);
        scene.idle(10);

        scene.overlay().showOutlineWithText(blazeBurnerSel.add(beehiveSel).add(beenestSel), 100)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("除了常规的流体容器，动力移液器还支持与更多有趣的方块交互，也许它能更好地帮助你施展创意")
                .pointAt(util.vector().blockSurface(beehivePos, Direction.WEST))
                .placeNearTarget();
        scene.idle(110);

        // 特殊方块交互动画 - 只有烈焰人燃烧室

        // 从流体接口（储罐）抽取岩浆
        instructPipette(scene, pipettePos, PipetteBlockEntity.Phase.MOVE_TO_INPUT, FluidStack.EMPTY, 1);
        scene.idle(24);

        scene.world().modifyBlockEntity(tankPos, FluidTankBlockEntity.class, (be) -> {
            be.getTankInventory().drain(1000, IFluidHandler.FluidAction.EXECUTE);
        });
        scene.idle(10);

        instructPipette(scene, pipettePos, PipetteBlockEntity.Phase.SEARCH_OUTPUTS,
                new FluidStack(Fluids.LAVA, 1000), -1);
        scene.idle(20);

        // 移动到烈焰人燃烧室
        instructPipette(scene, pipettePos, PipetteBlockEntity.Phase.MOVE_TO_OUTPUT,
                new FluidStack(Fluids.LAVA, 1000), 1);
        scene.idle(24);

        // 改变烈焰人燃烧室的热量等级（不显示粒子效果）
        scene.world().modifyBlock(blazeBurnerPos, s -> {
            if (AllBlocks.BLAZE_BURNER.has(s)) {
                return s.setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.KINDLED);
            }
            return s;
        }, false);
        scene.idle(10);

        instructPipette(scene, pipettePos, PipetteBlockEntity.Phase.SEARCH_INPUTS, FluidStack.EMPTY, -1);
        scene.idle(30);

        Vec3 pipetteTop = util.vector().blockSurface(pipettePos, Direction.UP).add(0, 0.5, 0);
        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("这是动力移液器的存量指示器，通过它来了解当前动力移液器所持有的流体量。动力移液器的最大流体容量为1000mb")
                .pointAt(pipetteTop)
                .placeNearTarget();
        scene.idle(110);

        scene.markAsFinished();
    }

    private static void instructPipette(CreateSceneBuilder scene, BlockPos pipettePos,
                                        PipetteBlockEntity.Phase phase, FluidStack heldFluid, int targetedPoint) {
        scene.world().modifyBlockEntityNBT(scene.getScene().getSceneBuildingUtil().select().position(pipettePos),
                PipetteBlockEntity.class, (compound) -> {
                    NBTHelper.writeEnum(compound, "Phase", phase);
                    compound.put("HeldFluid", heldFluid.writeToNBT(new CompoundTag()));
                    compound.putInt("TargetPointIndex", targetedPoint);
                    compound.putFloat("MovementProgress", 0.0F);
                });
    }
}