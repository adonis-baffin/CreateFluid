package com.adonis.fluid.ponder;

import com.adonis.fluid.block.Pipette.PipetteBlockEntity;
import com.adonis.fluid.block.SmartFluidInterface.SmartFluidInterfaceBlockEntity;
import com.adonis.fluid.registry.CFFluids;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.catnip.nbt.NBTHelper;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class PowderSnowScenes {

    public static void snow(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("powder_snow", "Powder Snow as Fluid");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        // 定义位置
        BlockPos cauldronPos = util.grid().at(2, 1, 2);
        BlockPos pipettePos = util.grid().at(0, 1, 3);
        BlockPos pipetteGear1Pos = util.grid().at(0, 1, 4);
        BlockPos pipetteGear2Pos = util.grid().at(0, 1, 5);
        BlockPos pipe1Pos = util.grid().at(3, 1, 2);
        BlockPos pipe2Pos = util.grid().at(3, 1, 3);
        BlockPos pumpPos = util.grid().at(3, 1, 4);
        BlockPos shaftPos = util.grid().at(3, 1, 5);
        BlockPos tankBottomPos = util.grid().at(3, 2, 4);
        BlockPos tankTopPos = util.grid().at(3, 3, 4);
        BlockPos smartInterfacePos = util.grid().at(2, 3, 4);
        BlockPos basinPos = util.grid().at(4, 1, 1);
        BlockPos mixerPos = util.grid().at(4, 3, 1);
        BlockPos mixerGearPos = util.grid().at(5, 3, 1);
        BlockPos lowerGearPos = util.grid().at(5, 0, 1);

        // 定义选择区域
        Selection cauldronSel = util.select().position(cauldronPos);
        Selection pipetteSel = util.select().position(pipettePos);
        Selection pipetteGearsSel = util.select().fromTo(0, 1, 4, 0, 1, 5);
        Selection pipesSel = util.select().fromTo(3, 1, 2, 3, 1, 3);
        Selection pumpSel = util.select().position(pumpPos);
        Selection shaftSel = util.select().position(shaftPos);
        Selection tankSel = util.select().fromTo(3, 2, 4, 3, 3, 4);
        Selection smartInterfaceSel = util.select().position(smartInterfacePos);
        Selection basinSel = util.select().position(basinPos);
        Selection mixerSel = util.select().position(mixerPos);
        Selection mixerShaftSel = util.select().fromTo(5, 1, 1, 5, 3, 1);
        Selection mixerGearSel = util.select().position(mixerGearPos);
        Selection lowerGearSel = util.select().position(lowerGearPos);
        Selection tankTopSel = util.select().position(tankTopPos);

        // 初始设置
        scene.world().setKineticSpeed(util.select().everywhere(), 0);
        
        // 设置炼药锅装满细雪
        BlockState powderSnowCauldron = Blocks.POWDER_SNOW_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, 3);
        scene.world().setBlock(cauldronPos, powderSnowCauldron, false);

        scene.idle(20);

        // 显示炼药锅
        scene.world().showSection(cauldronSel, Direction.DOWN);
        scene.idle(10);

        // 第一句话
        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("Powder Snow is now a fluid that can be collected from Cauldrons using pipes or Mechanical Pipettes")
                .pointAt(util.vector().blockSurface(cauldronPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        // 显示流体管道系统
        scene.world().showSection(pipesSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(pumpSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(shaftSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(tankSel, Direction.DOWN);
        scene.idle(10);

        // 启动泵，抽取细雪
        scene.world().setKineticSpeed(pumpSel, 32);
        scene.world().setKineticSpeed(shaftSel, 32);
        scene.world().propagatePipeChange(pumpPos);
        scene.idle(40);

        // 炼药锅变空
        scene.world().setBlock(cauldronPos, Blocks.CAULDRON.defaultBlockState(), false);
        scene.idle(20);

        // 显示智能流体接口
        scene.world().showSection(smartInterfaceSel, Direction.DOWN);
        scene.idle(10);

        // 显示过滤设置
        ItemStack powderSnowBucket = new ItemStack(Items.POWDER_SNOW_BUCKET);
        scene.overlay().showControls(util.vector().blockSurface(tankTopPos, Direction.WEST),
                Pointing.RIGHT, 40)
                .rightClick()
                .withItem(powderSnowBucket);
        scene.idle(7);
        scene.world().setFilterData(smartInterfaceSel, SmartFluidInterfaceBlockEntity.class, powderSnowBucket);
        scene.idle(10);

        // 第二句话
        scene.overlay().showOutlineWithText(smartInterfaceSel, 80)
                .attachKeyFrame()
                .colored(PonderPalette.OUTPUT)
                .text("Powder Snow Buckets can be used to set filters for Powder Snow fluid")
                .pointAt(util.vector().blockSurface(smartInterfacePos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        // 显示移液器系统
        scene.world().showSection(pipetteSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(pipetteGearsSel, Direction.DOWN);
        scene.idle(10);

        scene.world().setKineticSpeed(pipetteSel, -48);
        scene.world().setKineticSpeed(pipetteGearsSel, -48);
        scene.world().multiplyKineticSpeed(util.select().position(0, 1, 5), -1);
        scene.idle(20);

        // 移液器从智能流体接口取液
        instructPipette(scene, pipettePos, PipetteBlockEntity.Phase.MOVE_TO_INPUT, FluidStack.EMPTY, 0);
        scene.idle(24);

        scene.world().modifyBlockEntity(tankBottomPos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().drain(1000, IFluidHandler.FluidAction.EXECUTE);
        });
        scene.idle(10);

        FluidStack powderSnowFluid = getPowderSnowFluidStack(1000);
        instructPipette(scene, pipettePos, PipetteBlockEntity.Phase.SEARCH_OUTPUTS, powderSnowFluid, -1);
        scene.idle(20);

        // 移液器注入炼药锅
        instructPipette(scene, pipettePos, PipetteBlockEntity.Phase.MOVE_TO_OUTPUT, powderSnowFluid, 0);
        scene.idle(24);

        // 炼药锅重新装满
        scene.world().setBlock(cauldronPos, powderSnowCauldron, false);
        scene.idle(10);

        instructPipette(scene, pipettePos, PipetteBlockEntity.Phase.SEARCH_INPUTS, FluidStack.EMPTY, -1);
        scene.idle(20);

        // 显示搅拌器系统
        scene.world().showSection(basinSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(lowerGearSel, Direction.UP);
        scene.idle(5);
        scene.world().showSection(mixerShaftSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(mixerSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(mixerGearSel, Direction.DOWN);
        scene.idle(10);

        // 启动搅拌器
        scene.world().setKineticSpeed(lowerGearSel, 32);
        scene.world().setKineticSpeed(mixerShaftSel, 32);
        scene.world().setKineticSpeed(mixerSel, 32);
        scene.world().setKineticSpeed(mixerGearSel, 32);
        scene.idle(10);

        // 第三句话
        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("Powder Snow fluid can also be created using a Mechanical Mixer")
                .pointAt(util.vector().blockSurface(basinPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        // 添加雪块到工作盆
        ItemStack snowBlock = new ItemStack(Items.SNOW_BLOCK);
        scene.overlay().showControls(util.vector().topOf(basinPos), Pointing.RIGHT, 30)
                .rightClick()
                .withItem(snowBlock);
        scene.world().modifyBlockEntity(basinPos, BasinBlockEntity.class, be -> {
            be.getInputInventory().insertItem(0, snowBlock.copy(), false);
        });
        scene.idle(20);

        // 搅拌器工作
        scene.world().modifyBlockEntity(mixerPos, MechanicalMixerBlockEntity.class, 
                mixer -> mixer.startProcessingBasin());
        scene.idle(40);

        // 生成细雪流体
        scene.world().modifyBlockEntity(basinPos, BasinBlockEntity.class, be -> {
            be.getInputInventory().extractItem(0, 64, false);
            var handler = be.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, be.getBlockPos(), null);
            if (handler != null) {
                handler.fill(getPowderSnowFluidStack(1000), IFluidHandler.FluidAction.EXECUTE);
            }
        });
        scene.idle(20);

        // 显示右键获取细雪桶
        scene.overlay().showControls(util.vector().topOf(basinPos), Pointing.RIGHT, 30)
                .rightClick()
                .withItem(powderSnowBucket);
        scene.idle(10);

        // 第四句话
        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text("Right-click the Basin to obtain Powder Snow Buckets")
                .pointAt(util.vector().blockSurface(basinPos, Direction.UP))
                .placeNearTarget();
        scene.idle(90);

        scene.markAsFinished();
    }

    private static void instructPipette(CreateSceneBuilder scene, BlockPos pipettePos,
                                        PipetteBlockEntity.Phase phase, FluidStack heldFluid, int targetedPoint) {
        scene.world().modifyBlockEntityNBT(scene.getScene().getSceneBuildingUtil().select().position(pipettePos),
                PipetteBlockEntity.class, (compound) -> {
                    NBTHelper.writeEnum(compound, "Phase", phase);
                    compound.put("HeldFluid", heldFluid.saveOptional(scene.getScene().getWorld().registryAccess()));
                    compound.putInt("TargetPointIndex", targetedPoint);
                    compound.putFloat("MovementProgress", 0.0F);
                });
    }

    private static FluidStack getPowderSnowFluidStack(int amount) {
        return new FluidStack(CFFluids.POWDER_SNOW.get(), amount);
    }
}
