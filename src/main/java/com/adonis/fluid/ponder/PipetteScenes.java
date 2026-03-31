package com.adonis.fluid.ponder;

import com.adonis.fluid.block.Pipette.PipetteBlockEntity;
import com.adonis.fluid.block.SmartFluidInterface.SmartFluidInterfaceBlockEntity;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllFluids;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.nbt.NBTHelper;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.simibubi.create.content.fluids.FluidFX;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.minecraft.core.registries.BuiltInRegistries;

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

        scene.world().modifyBlockEntity(splashingPos, ItemDrainBlockEntity.class, be -> {
            SmartFluidTankBehaviour tankBehaviour = be.getBehaviour(SmartFluidTankBehaviour.TYPE);
            if (tankBehaviour != null) {
                tankBehaviour.allowInsertion();
                IFluidHandler handler = be.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, splashingPos, null);
                if (handler != null) {
                    handler.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
                }
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

        scene.world().modifyBlockEntity(splashingPos, ItemDrainBlockEntity.class, be -> {
            IFluidHandler handler = be.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, splashingPos, null);
            if (handler != null) {
                handler.drain(1000, IFluidHandler.FluidAction.EXECUTE);
            }
        });
        scene.idle(10);

        instructPipette(scene, pipettePos, PipetteBlockEntity.Phase.SEARCH_OUTPUTS,
                new FluidStack(Fluids.WATER, 1000), -1);
        scene.idle(20);

        instructPipette(scene, pipettePos, PipetteBlockEntity.Phase.MOVE_TO_OUTPUT,
                new FluidStack(Fluids.WATER, 1000), 0);
        scene.idle(24);

        scene.world().modifyBlockEntity(basinPos, BasinBlockEntity.class, be -> {
            IFluidHandler handler = be.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, basinPos, null);
            if (handler != null) {
                handler.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
            }
        });
        scene.idle(10);

        instructPipette(scene, pipettePos, PipetteBlockEntity.Phase.SEARCH_INPUTS, FluidStack.EMPTY, -1);
        scene.idle(20);

        // 显示储罐和流体接口
        scene.world().showSection(tankSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(fluidInterfaceSel, Direction.DOWN);
        scene.idle(10);

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

        scene.world().modifyBlockEntity(tankPos, FluidTankBlockEntity.class, be -> {
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

    public static void filtering(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("mechanical_pipette_filter", "Filtering Fluids with Smart Fluid Interface");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        // 定义位置
        BlockPos pipettePos = util.grid().at(2, 1, 2);
        BlockPos basinPos = util.grid().at(4, 2, 1);
        BlockPos blazeBurnerPos = util.grid().at(4, 1, 1); // 烈焰人在工作盆下方
        BlockPos smartInterfacePos = util.grid().at(3, 2, 1);
        BlockPos tankPos = util.grid().at(0, 1, 3);
        BlockPos fluidInterfacePos = util.grid().at(0, 2, 2);
        BlockPos mixerPos = util.grid().at(4, 4, 1);
        BlockPos mixerShaftPos = util.grid().at(5, 3, 1); // 传动杆
        BlockPos mixerGearPos = util.grid().at(5, 4, 1); // 搅拌器旁边的齿轮
        BlockPos lowerGearPos = util.grid().at(5, 0, 1); // 下方齿轮

        // 选择区域
        Selection pipetteSel = util.select().position(pipettePos);
        Selection basinSel = util.select().position(basinPos);
        Selection blazeBurnerSel = util.select().position(blazeBurnerPos);
        Selection smartInterfaceSel = util.select().position(smartInterfacePos);
        Selection tankSel = util.select().fromTo(0, 1, 3, 0, 3, 3);
        Selection fluidInterfaceSel = util.select().position(fluidInterfacePos);
        Selection mixerSel = util.select().position(mixerPos);
        Selection mixerShaftSel = util.select().fromTo(5, 1, 1, 5, 3, 1); // 包含传动杆
        Selection mixerGearSel = util.select().position(mixerGearPos);
        Selection lowerGearSel = util.select().position(lowerGearPos);
        Selection gearsSel = util.select().fromTo(2, 1, 5, 2, 1, 3)
                .add(util.select().position(2, 0, 5));

        // 初始设置
        scene.world().setKineticSpeed(pipetteSel, 0);
        scene.world().setKineticSpeed(gearsSel, 0);
        scene.world().setKineticSpeed(mixerSel, 0);
        scene.world().setKineticSpeed(mixerGearSel, 0);

        scene.idle(20);

        // 显示移液器
        scene.world().showSection(pipetteSel, Direction.DOWN);
        scene.idle(10);

        // 问题1修复：同时显示工作盆和烈焰人燃烧室
        scene.world().showSection(blazeBurnerSel, Direction.DOWN);
        scene.idle(5);
        // 设置烈焰人为kindled状态
        scene.world().modifyBlock(blazeBurnerPos, s -> {
            if (AllBlocks.BLAZE_BURNER.has(s)) {
                return s.setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.KINDLED);
            }
            return s;
        }, false);
        scene.idle(5);
        scene.world().showSection(basinSel, Direction.DOWN);
        scene.idle(10);

        // 工作盆已有牛奶（在NBT中设置）
        scene.world().modifyBlockEntity(basinPos, BasinBlockEntity.class, be -> {
            IFluidHandler handler = be.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, basinPos, null);
            if (handler != null) {
                // 添加牛奶
                FluidStack milk = new FluidStack(BuiltInRegistries.FLUID.get(
                        ResourceLocation.fromNamespaceAndPath("minecraft", "milk")), 1000);
                if (milk.getFluid() == Fluids.EMPTY) {
                    milk = new FluidStack(Fluids.WATER, 1000); // 后备
                }
                handler.fill(milk, IFluidHandler.FluidAction.EXECUTE);
            }
        });

        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("有时，你会想着利用某种过滤限制动力移液器的目标")
                .pointAt(util.vector().blockSurface(basinPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        // 向工作盆添加糖和可可豆 - 问题3修复：使用LEFT和RIGHT而不是都用DOWN
        ItemStack sugar = new ItemStack(Items.SUGAR);
        ItemStack cocoaBeans = new ItemStack(Items.COCOA_BEANS);

        scene.overlay().showControls(util.vector().topOf(basinPos), Pointing.LEFT, 30)
                .withItem(sugar);
        scene.world().modifyBlockEntity(basinPos, BasinBlockEntity.class, be -> {
            be.getInputInventory().insertItem(0, sugar.copy(), false);
        });

        scene.overlay().showControls(util.vector().topOf(basinPos), Pointing.RIGHT, 30)
                .withItem(cocoaBeans);
        scene.world().modifyBlockEntity(basinPos, BasinBlockEntity.class, be -> {
            be.getInputInventory().insertItem(1, cocoaBeans.copy(), false);
        });
        scene.idle(30);

        // 问题2修复：显示完整传动系统
        scene.world().showSection(lowerGearSel, Direction.UP);
        scene.idle(5);
        scene.world().showSection(mixerShaftSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(mixerSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(mixerGearSel, Direction.DOWN);
        scene.idle(10);

        // 启动传动系统
        scene.world().setKineticSpeed(lowerGearSel, 32);
        scene.world().setKineticSpeed(mixerShaftSel, 32);
        scene.world().setKineticSpeed(mixerSel, 32);
        scene.world().setKineticSpeed(mixerGearSel, 32);

        // 问题4修复：让搅拌器下去做做样子
        scene.world().modifyBlockEntity(mixerPos, MechanicalMixerBlockEntity.class,
                mixer -> mixer.startProcessingBasin());
        scene.idle(40);

        // 生成巧克力
        Fluid chocolateFluid = BuiltInRegistries.FLUID.get(ResourceLocation.fromNamespaceAndPath("create", "chocolate"));
        if (chocolateFluid == null || chocolateFluid == Fluids.EMPTY) {
            chocolateFluid = Fluids.WATER; // 后备方案
        }
        final Fluid finalChocolateFluid = chocolateFluid;

        scene.world().modifyBlockEntity(basinPos, BasinBlockEntity.class, be -> {
            // 清空物品
            be.getInputInventory().extractItem(0, 64, false);
            be.getInputInventory().extractItem(1, 64, false);
            // 清空牛奶，添加巧克力流体
            IFluidHandler handler = be.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, basinPos, null);
            if (handler != null) {
                handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE); // 先清空
                handler.fill(new FluidStack(finalChocolateFluid, 1000), IFluidHandler.FluidAction.EXECUTE);
            }
        });
        scene.idle(20);

        // 显示智能流体接口
        scene.world().showSection(smartInterfaceSel, Direction.DOWN);
        scene.idle(20);

        scene.overlay().showOutlineWithText(smartInterfaceSel, 80)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("智能流体接口因为这种需求而存在")
                .pointAt(util.vector().blockSurface(smartInterfacePos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        // 问题5修复：修正过滤槽位置（x坐标+3）
        Vec3 filterSlot = util.vector().of(3.6, 2.5, 1.5);
        scene.overlay().showFilterSlotInput(filterSlot, Direction.WEST, 80);
        scene.idle(10);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text("智能流体接口的过滤槽可以应用至动力移液器上")
                .pointAt(filterSlot)
                .placeNearTarget();
        scene.idle(90);

        // 在过滤槽设置巧克力桶
        ItemStack chocolateBucket = AllFluids.CHOCOLATE.get().getFluidType()
                .getBucket(new FluidStack(finalChocolateFluid, 1000));

        scene.overlay().showControls(filterSlot, Pointing.LEFT, 30)
                .rightClick()
                .withItem(chocolateBucket);
        scene.idle(7);

        // 设置智能流体接口的过滤器
        scene.world().setFilterData(util.select().position(smartInterfacePos),
                SmartFluidInterfaceBlockEntity.class, chocolateBucket);
        scene.idle(20);

        // 显示储罐和流体接口
        scene.world().showSection(tankSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(fluidInterfaceSel, Direction.DOWN);
        scene.idle(10);

        // 启动移液器
        scene.world().showSection(gearsSel, Direction.DOWN);
        scene.idle(10);
        scene.world().setKineticSpeed(pipetteSel, -48);
        scene.world().setKineticSpeed(gearsSel, -48);
        scene.world().multiplyKineticSpeed(util.select().position(2, 1, 5), -1);
        scene.idle(20);

        // 问题6修复：移液器从智能流体接口抽取巧克力（而不是牛奶）
        instructPipette(scene, pipettePos, PipetteBlockEntity.Phase.MOVE_TO_INPUT, FluidStack.EMPTY, 0);
        scene.idle(24);

        // 从工作盆抽取巧克力
        scene.world().modifyBlockEntity(basinPos, BasinBlockEntity.class, be -> {
            IFluidHandler handler = be.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, basinPos, null);
            if (handler != null) {
                handler.drain(1000, IFluidHandler.FluidAction.EXECUTE);
            }
        });
        scene.idle(10);

        // 问题6修复：确保移液器持有巧克力而不是牛奶
        FluidStack chocolateStack = new FluidStack(finalChocolateFluid, 1000);
        instructPipette(scene, pipettePos, PipetteBlockEntity.Phase.SEARCH_OUTPUTS, chocolateStack, -1);
        scene.idle(20);

        // 问题7修复：让移液器移动到流体接口（储罐）
        instructPipette(scene, pipettePos, PipetteBlockEntity.Phase.MOVE_TO_OUTPUT, chocolateStack, 0);
        scene.idle(24);

        // 向储罐注入巧克力
        scene.world().modifyBlockEntity(tankPos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().fill(chocolateStack, IFluidHandler.FluidAction.EXECUTE);
        });
        scene.idle(10);

        // 移液器回到搜索输入状态
        instructPipette(scene, pipettePos, PipetteBlockEntity.Phase.SEARCH_INPUTS, FluidStack.EMPTY, -1);
        scene.idle(20);

        scene.markAsFinished();
    }

    public static void filling(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("mechanical_pipette_fill", "Spout Processing with Mechanical Pipette");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        // 定义位置
        BlockPos pipettePos = util.grid().at(3, 1, 2);
        BlockPos milkTankPos = util.grid().at(2, 1, 4);
        BlockPos honeyTankPos = util.grid().at(0, 1, 3);
        BlockPos milkInterfacePos = util.grid().at(2, 2, 3);
        BlockPos honeyInterfacePos = util.grid().at(0, 2, 2);
        BlockPos depotPos = util.grid().at(1, 1, 1);
        BlockPos beltStartPos = util.grid().at(2, 1, 0);
        BlockPos beltEndPos = util.grid().at(4, 1, 0);
        BlockPos funnelPos = util.grid().at(4, 2, 0);
        BlockPos casingPos = util.grid().at(5, 1, 0);
        BlockPos chestPos = util.grid().at(5, 2, 0);
        BlockPos plusPos = util.grid().at(4, 1, 1);
        BlockPos bigplusPos = util.grid().at(5, 2, 1);

        // 定义选择区域
        Selection pipetteSel = util.select().position(pipettePos);
        Selection milkTankSel = util.select().fromTo(2, 1, 4, 2, 3, 4);
        Selection honeyTankSel = util.select().fromTo(0, 1, 3, 0, 3, 3);
        Selection milkInterfaceSel = util.select().position(milkInterfacePos);
        Selection honeyInterfaceSel = util.select().position(honeyInterfacePos);
        Selection depotSel = util.select().position(depotPos);
        Selection beltSel = util.select().fromTo(2, 1, 0, 4, 1, 0);
        Selection funnelSel = util.select().position(funnelPos);
        Selection casingSel = util.select().position(casingPos);
        Selection plusSel = util.select().position(plusPos);
        Selection bigplusSel = util.select().position(bigplusPos);
        Selection chestSel = util.select().position(chestPos);
        Selection gearsSel = util.select().fromTo(3, 1, 3, 3, 1, 5)
                .add(util.select().position(3, 0, 5));

        // 初始设置 - 让储罐有流体
        scene.world().modifyBlockEntity(milkTankPos, FluidTankBlockEntity.class, be -> {
            ResourceLocation milkId = ResourceLocation.fromNamespaceAndPath("minecraft", "milk");
            Fluid milk = BuiltInRegistries.FLUID.get(milkId);
            if (milk != null && milk != Fluids.EMPTY) {
                be.getTankInventory().fill(new FluidStack(milk, 4000), IFluidHandler.FluidAction.EXECUTE);
            }
        });

        scene.world().modifyBlockEntity(honeyTankPos, FluidTankBlockEntity.class, be -> {
            Fluid honey = AllFluids.HONEY.get();
            be.getTankInventory().fill(new FluidStack(honey, 4000), IFluidHandler.FluidAction.EXECUTE);
        });

        scene.idle(20);

        // 显示除了齿轮外的所有部分
        scene.world().showSection(pipetteSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(milkTankSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(honeyTankSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(milkInterfaceSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(honeyInterfaceSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(depotSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(beltSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(plusSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(bigplusSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(funnelSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(casingSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(chestSel, Direction.DOWN);
        scene.idle(10);

        // 第一句话：输入
        scene.overlay().showOutlineWithText(milkInterfaceSel.add(honeyInterfaceSel), 40)
                .colored(PonderPalette.INPUT)
                .text("输入")
                .pointAt(util.vector().blockSurface(milkInterfacePos, Direction.WEST))
                .placeNearTarget();
        scene.idle(5);

        // 第二句话：输出
        scene.overlay().showOutlineWithText(depotSel.add(beltSel), 40)
                .colored(PonderPalette.OUTPUT)
                .text("输出")
                .pointAt(util.vector().blockSurface(depotPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(40);

        // 第三句话：除了处理物流，动力移液器也可以应用于加工场景
        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("除了处理物流，动力移液器也可以应用于加工场景")
                .pointAt(util.vector().blockSurface(pipettePos, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        // 第四句话：当选定置物台或传送带作为输出端时...
        scene.overlay().showOutlineWithText(depotSel.add(beltSel), 100)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text("当选定置物台或传送带作为输出端时，动力移液器会时刻留意上方是否有可以执行注液加工的原料")
                .pointAt(util.vector().blockSurface(depotPos, Direction.UP))
                .placeNearTarget();
        scene.idle(110);

        // 在置物台上放面包
        ItemStack bread = new ItemStack(Items.BREAD);
        scene.world().createItemOnBeltLike(depotPos, Direction.DOWN, bread);

        scene.overlay().showControls(util.vector().topOf(depotPos), Pointing.DOWN, 30)
                .withItem(bread);
        scene.world().modifyBlockEntity(depotPos, BasinBlockEntity.class, be -> {
            be.getInputInventory().insertItem(0, bread.copy(), false);
        });

        scene.idle(20);

        // 第五句话：动力移液器会从所有的输入端中寻找所需的流体原料
        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("动力移液器会从所有的输入端中寻找所需的流体原料，然后执行注液加工")
                .pointAt(util.vector().blockSurface(honeyTankPos.above(), Direction.WEST))
                .placeNearTarget();
        scene.idle(110);

        // 显示齿轮并启动
        scene.world().setKineticSpeed(pipetteSel, -48);
        scene.world().setKineticSpeed(gearsSel, -48);
        scene.world().showSection(gearsSel, Direction.DOWN);
        scene.world().multiplyKineticSpeed(util.select().position(3, 1, 5), -1);
        scene.world().setKineticSpeed(beltSel, 16);
        scene.world().setKineticSpeed(plusSel, 16);
        scene.world().setKineticSpeed(bigplusSel, -16);
        scene.world().setKineticSpeed(funnelSel, 16);
        scene.idle(10);

        // 模拟注液加工过程
        // 1. 移液器移动到牛奶储罐
        instructPipette(scene, pipettePos, PipetteBlockEntity.Phase.MOVE_TO_INPUT, FluidStack.EMPTY, 0);
        scene.idle(24);

        // 2. 从牛奶储罐抽取
        ResourceLocation milkId = ResourceLocation.fromNamespaceAndPath("minecraft", "milk");
        Fluid milk = BuiltInRegistries.FLUID.get(milkId);
        FluidStack milkStack = milk != null && milk != Fluids.EMPTY ?
                new FluidStack(milk, 250) : new FluidStack(Fluids.WATER, 250);

        scene.world().modifyBlockEntity(milkTankPos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().drain(250, IFluidHandler.FluidAction.EXECUTE);
        });
        scene.idle(10);

        instructPipette(scene, pipettePos, PipetteBlockEntity.Phase.SEARCH_OUTPUTS, milkStack, -1);
        scene.idle(20);

        // 3. 移液器移动到置物台
        instructPipette(scene, pipettePos, PipetteBlockEntity.Phase.MOVE_TO_OUTPUT, milkStack, 0);
        scene.idle(24);

        // 4. 执行注液加工 - 产生粒子效果（修正版）
        Vec3 depotTop = util.vector().topOf(depotPos);
        ParticleOptions fluidParticle = FluidFX.getFluidParticle(milkStack);
        RandomSource random = RandomSource.create();

        for (int i = 0; i < 10; i++) {
            scene.effects().emitParticles(
                    depotTop.add(0, 0.0625, 0),
                    scene.effects().simpleParticleEmitter(fluidParticle, VecHelper.offsetRandomly(Vec3.ZERO, random, 0.1f)),
                    1.0f,
                    1
            );
        }

        // 5. 把面包变成甜甜卷
        ItemStack sweetRoll = AllItems.SWEET_ROLL.asStack();
        scene.world().removeItemsFromBelt(depotPos);
        scene.world().createItemOnBeltLike(depotPos, Direction.DOWN, sweetRoll);
        scene.idle(10);

        // 6. 移液器收回
        instructPipette(scene, pipettePos, PipetteBlockEntity.Phase.SEARCH_INPUTS, FluidStack.EMPTY, -1);
        scene.idle(30);

        scene.markAsFinished();
    }

    private static void instructPipette(CreateSceneBuilder scene, BlockPos pipettePos,
                                        PipetteBlockEntity.Phase phase, FluidStack heldFluid, int targetedPoint) {
        // 使用与 instructArm 相同的方式：通过 NBT 修改来触发动画
        // 从 PonderScene 的世界获取 HolderLookup.Provider
        scene.world().modifyBlockEntityNBT(scene.getScene().getSceneBuildingUtil().select().position(pipettePos),
                PipetteBlockEntity.class, compound -> {
                    NBTHelper.writeEnum(compound, "Phase", phase);
                    if (!heldFluid.isEmpty()) {
                        // 使用 scene.getScene().getWorld().registryAccess() 获取 HolderLookup.Provider
                        compound.put("HeldFluid", heldFluid.saveOptional(scene.getScene().getWorld().registryAccess()));
                    } else {
                        compound.putBoolean("EmptyFluid", true);
                    }
                    compound.putInt("TargetPointIndex", targetedPoint);
                    compound.putFloat("MovementProgress", 0);
                });
    }
}