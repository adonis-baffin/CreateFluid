package com.adonis.fluid.ponder;

import com.adonis.fluid.block.GutterOutlet.GutterOutletBlockEntity;
import com.adonis.fluid.registry.CFBlock;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class GutterOutletScenes {

    public static void gutteroutlet(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("gutter_outlet", "Collecting Fluid with Gutter Outlets");
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.9f);
        scene.showBasePlate();
        scene.idle(20);

        // 定义位置
        BlockPos gutterPos = util.grid().at(2, 1, 2);
        BlockPos pipe1Pos = util.grid().at(3, 1, 2);
        BlockPos tankBottomPos = util.grid().at(2, 1, 4);
        BlockPos tankTopPos = util.grid().at(2, 3, 4);
        BlockPos pumpPos = util.grid().at(4, 1, 2);
        BlockPos pipe2aPos = util.grid().at(4, 2, 2);
        BlockPos pipe2bPos = util.grid().at(4, 2, 3);
        BlockPos pipe2cPos = util.grid().at(4, 2, 4);
        BlockPos pipe2dPos = util.grid().at(3, 2, 4);
        BlockPos gearPos = util.grid().at(5, 1, 2);
        BlockPos largeGearPos = util.grid().at(5, 0, 1);
        BlockPos casingPos = util.grid().at(2, 4, 2);
        BlockPos stalactitePos = util.grid().at(2, 3, 2);

        // 定义选择区域
        Selection gutterSel = util.select().position(gutterPos);
        Selection pipe1Sel = util.select().position(pipe1Pos);
        Selection pipes2Sel = util.select().fromTo(4, 2, 2, 4, 2, 4).add(util.select().position(3, 2, 4));
        Selection tankSel = util.select().fromTo(2, 1, 4, 2, 3, 4);
        Selection pumpSel = util.select().position(pumpPos);
        Selection kineticsSel = util.select().fromTo(5, 0, 2, 5, 1, 2);

        // 第一步：单独展示集水器（模仿官方 Item Drain 的独立展示 + 平移）
        ElementLink<WorldSectionElement> gutterLink = scene.world().showIndependentSection(gutterSel, Direction.DOWN);
        scene.idle(30);

        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("Gutter Outlets can be used to drain large bodies of fluid above them, just like Item Drains")
                .pointAt(util.vector().blockSurface(gutterPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(120);

        // 第二步：临时放置滴水石锥 + 铜机壳演示收集岩浆/雨雪（不使用 show/hide）
        scene.idle(20);

        scene.world().modifyBlock(casingPos, s -> AllBlocks.COPPER_CASING.getDefaultState(), false);
        scene.world().modifyBlock(stalactitePos, s -> Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN)
                .setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP), false);
        scene.idle(20);

        scene.overlay().showText(110)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("Gutter Outlets can also collect lava like a cauldron, or to collect fluid in rainy or snowy weather")
                .pointAt(util.vector().topOf(gutterPos))
                .placeNearTarget();
        scene.idle(130);

        // 移除临时方块
        scene.world().modifyBlock(casingPos, s -> Blocks.AIR.defaultBlockState(), false);
        scene.world().modifyBlock(stalactitePos, s -> Blocks.AIR.defaultBlockState(), false);
        scene.idle(20);

        // 第三步：右键倒入岩浆桶
        ItemStack lavaBucket = new ItemStack(Items.LAVA_BUCKET);
        scene.overlay().showControls(new Vec3(gutterPos.getX() + 0.5, gutterPos.getY() + 1, gutterPos.getZ() + 0.5),
                        Pointing.DOWN, 60)
                .rightClick()
                .withItem(lavaBucket);
        scene.idle(70);

        // 直接填充内部 tank（你已添加 getTankInventory() 方法）
        scene.world().modifyBlockEntity(gutterPos, GutterOutletBlockEntity.class, be -> {
            be.getTankInventory().fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);
        });

        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text("Right-click it to pour fluid from your held item into it, or to extract fluid")
                .pointAt(util.vector().blockSurface(gutterPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(120);

        // 第四步：展示管道网络抽取流体
        scene.world().showSection(pipe1Sel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(pipes2Sel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(tankSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(pumpSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(kineticsSel, Direction.DOWN);
        scene.idle(20);

        // 启动动力
        scene.world().setKineticSpeed(kineticsSel, 32);
        scene.world().propagatePipeChange(pumpPos);
        scene.idle(20);

        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.OUTPUT)
                .text("Pipe Networks can now pull the fluid from their internal buffer")
                .pointAt(util.vector().blockSurface(gutterPos, Direction.EAST))
                .placeNearTarget();
        scene.idle(40);

        // 模拟流体流动：集水器排空 → 储罐填充
        scene.world().modifyBlockEntity(gutterPos, GutterOutletBlockEntity.class, be -> {
            be.getTankInventory().drain(1000, IFluidHandler.FluidAction.EXECUTE);
        });
        scene.idle(20);

        scene.world().modifyBlockEntity(tankBottomPos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);
        });
        scene.idle(60);

        scene.markAsFinished();
    }
}