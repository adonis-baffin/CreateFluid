package com.adonis.fluid.ponder;

import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpBlock;
import com.adonis.fluid.registry.CFItems;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class CentrifugalPumpScenes {

    public static void centrifugalpump(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("centrifugal_pump", "Using Centrifugal Pumps");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        // 定义位置
        BlockPos pumpPos = util.grid().at(2, 1, 2);
        BlockPos pipe1Pos = util.grid().at(2, 2, 2);
        BlockPos pipe2Pos = util.grid().at(2, 2, 3);
        BlockPos pipe3Pos = util.grid().at(2, 2, 4);
        BlockPos pipe4Pos = util.grid().at(3, 2, 4);
        BlockPos tank1BottomPos = util.grid().at(4, 1, 4);
        BlockPos tank1MiddlePos = util.grid().at(4, 2, 4);
        BlockPos tank1TopPos = util.grid().at(4, 3, 4);
        BlockPos pipe5Pos = util.grid().at(1, 1, 2);
        BlockPos pipe6Pos = util.grid().at(0, 1, 2);
        BlockPos pipe7Pos = util.grid().at(0, 1, 3);
        BlockPos tank2BottomPos = util.grid().at(0, 1, 4);
        BlockPos tank2MiddlePos = util.grid().at(0, 2, 4);
        BlockPos tank2TopPos = util.grid().at(0, 3, 4);
        BlockPos shaftPos = util.grid().at(3, 1, 2);
        BlockPos gearshiftPos = util.grid().at(4, 1, 2);
        BlockPos leverPos = util.grid().at(4, 2, 2);
        BlockPos gearPos = util.grid().at(5, 1, 2);
        BlockPos largeGearPos = util.grid().at(5, 0, 1);

        // 定义选择区域
        Selection pumpSel = util.select().position(pumpPos);
        Selection pipes1Sel = util.select().fromTo(2, 2, 2, 3, 2, 4);
        Selection tank1Sel = util.select().fromTo(4, 1, 4, 4, 3, 4);
        Selection pipes2Sel = util.select().fromTo(0, 1, 2, 1, 1, 2).add(util.select().position(0, 1, 3));
        Selection tank2Sel = util.select().fromTo(0, 1, 4, 0, 3, 4);
        Selection shaftSel = util.select().position(shaftPos);
        Selection gearshiftSel = util.select().position(gearshiftPos);
        Selection leverSel = util.select().position(leverPos);
        Selection gearSel = util.select().position(gearPos);
        Selection largeGearSel = util.select().position(largeGearPos);
        Selection allKinetics = util.select().fromTo(3, 1, 2, 5, 1, 2)
                .add(largeGearSel);

        // 初始设置
        scene.world().setKineticSpeed(util.select().everywhere(), 0);

        scene.idle(20);

        // 显示离心泵
        scene.world().showSection(pumpSel, Direction.DOWN);
        scene.idle(10);

        // 第一句话
        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("Centrifugal Pumps have more powerful transfer rates and longer transfer distances than Mechanical Pumps, but consume more stress")
                .pointAt(util.vector().blockSurface(pumpPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(110);

        // 显示所有流体管道和储罐
        scene.world().showSection(pipes1Sel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(tank1Sel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(pipes2Sel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(tank2Sel, Direction.DOWN);
        scene.idle(10);

        // 第二句话
        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text("The pump's pressure travels up to 20 blocks along pipes, regardless of input power")
                .pointAt(util.vector().blockSurface(pumpPos, Direction.UP))
                .placeNearTarget();
        scene.idle(90);

        // 显示动力系统
        scene.world().showSection(gearshiftSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(leverSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(shaftSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(gearSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(largeGearSel, Direction.UP);
        scene.idle(10);

        // 启动动力
        scene.world().setKineticSpeed(largeGearSel, 16);
        scene.world().setKineticSpeed(gearSel, -32);
        scene.world().setKineticSpeed(gearshiftSel, -32);
        scene.world().setKineticSpeed(shaftSel, -32);
        scene.world().setKineticSpeed(pumpSel, -32);
        scene.world().propagatePipeChange(pumpPos);
        scene.idle(20);

        // 第三句话
        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text("At the same speed, Centrifugal Pumps transfer fluids twice as fast as Mechanical Pumps")
                .pointAt(util.vector().blockSurface(pumpPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        // 启动拉杆，反转齿轮箱
        scene.world().toggleRedstonePower(util.select().fromTo(4, 2, 2, 4, 1, 2));
        scene.effects().indicateRedstone(leverPos);

        // 反转齿轮箱和其他动力的方向
        scene.world().multiplyKineticSpeed(util.select().fromTo(4, 1, 2, 2, 1, 2), -1);
        scene.effects().rotationDirectionIndicator(pumpPos.east());
        scene.world().propagatePipeChange(pumpPos);
        scene.idle(10);

        // 红色框选反转齿轮箱
        scene.overlay().showOutline(PonderPalette.RED, "gearshift", gearshiftSel, 60);
        scene.idle(20);

        // 第四句话
        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text("The pump's transfer direction is not affected by rotation direction")
                .pointAt(util.vector().blockSurface(pumpPos, Direction.UP))
                .placeNearTarget();
        scene.idle(90);

        // 关闭拉杆，恢复正常
        scene.world().toggleRedstonePower(util.select().fromTo(4, 2, 2, 4, 1, 2));
        scene.effects().indicateRedstone(leverPos);

        // 恢复齿轮箱和动力方向
        scene.world().multiplyKineticSpeed(util.select().fromTo(4, 1, 2, 2, 1, 2), -1);
        scene.effects().rotationDirectionIndicator(pumpPos.east());
        scene.world().propagatePipeChange(pumpPos);
        scene.idle(20);

        // 显示控制面板 - 使用showFilterSlotInput来显示面板位置
        Vec3 panelPos = util.vector().of(2.5, 1.5, 2);
        scene.overlay().showFilterSlotInput(panelPos, Direction.SOUTH, 80);
        scene.idle(10);

        // 第五句话
        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text("Instead, the side panel can be used to control the pump's transfer direction")
                .pointAt(panelPos)
                .placeNearTarget();
        scene.idle(90);

        // 显示指挥棒控制
        ItemStack baton = CFItems.BATON.asStack();
        scene.overlay().showControls(util.vector().blockSurface(shaftPos, Direction.WEST),
                        Pointing.RIGHT, 60)
                .rightClick()
                .withItem(baton);
        scene.idle(10);

        // 第六句话
        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("If using the panel is inconvenient, right-click with the Conductor Baton for quick switching")
                .pointAt(util.vector().blockSurface(pumpPos, Direction.UP))
                .placeNearTarget();
        scene.idle(90);

        // 显示铜机壳封装
        ItemStack copperCasing = AllBlocks.COPPER_CASING.asStack();
        scene.overlay().showControls(util.vector().blockSurface(shaftPos, Direction.WEST),
                        Pointing.RIGHT, 60)
                .rightClick()
                .withItem(copperCasing);
        scene.idle(10);

        // 第七句话
        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text("Copper Casing can be used to encase the Centrifugal Pump")
                .pointAt(util.vector().blockSurface(pumpPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        // 转换为封装状态
        scene.world().modifyBlock(pumpPos, s -> {
            if (s.getBlock() instanceof CentrifugalPumpBlock) {
                return s.setValue(CentrifugalPumpBlock.ENCASED, true);
            }
            return s;
        }, false);
        scene.idle(20);

        // 第八句话
        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text("Encased pumps no longer have a panel; their transfer direction is controlled by rotation direction")
                .pointAt(util.vector().blockSurface(pumpPos, Direction.UP))
                .placeNearTarget();
        scene.idle(110);

        // 再次启动拉杆，展示封装状态下的反转
        scene.world().toggleRedstonePower(util.select().fromTo(4, 2, 2, 4, 1, 2));
        scene.effects().indicateRedstone(leverPos);

        // 反转齿轮箱和动力方向
        scene.world().multiplyKineticSpeed(util.select().fromTo(4, 1, 2, 2, 1, 2), -1);
        scene.effects().rotationDirectionIndicator(pumpPos.east());
        scene.world().propagatePipeChange(pumpPos);
        scene.idle(60);

        // 水从储罐1流向储罐2
        scene.world().modifyBlockEntity(tank1BottomPos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().drain(2000, IFluidHandler.FluidAction.EXECUTE);
        });
        scene.world().modifyBlockEntity(tank2BottomPos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE);
        });
        scene.idle(40);

        scene.markAsFinished();
    }
}
