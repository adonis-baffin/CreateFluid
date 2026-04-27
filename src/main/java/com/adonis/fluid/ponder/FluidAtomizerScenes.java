package com.adonis.fluid.ponder;

import com.adonis.fluid.block.FluidAtomizer.FluidAtomizerBlockEntity;
import com.simibubi.create.content.fluids.FluidFX;
import com.simibubi.create.content.fluids.potion.PotionFluid;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.simibubi.create.foundation.ponder.element.BeltItemElement;
import net.createmod.catnip.math.Pointing;
import net.createmod.catnip.math.VecHelper;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class FluidAtomizerScenes {

    public static void processing(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("fluid_atomizer", "Using Fluid Atomizers");
        scene.configureBasePlate(0, 0, 6);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos beltStart = util.grid().at(1, 1, 2);
        BlockPos beltMiddle = util.grid().at(3, 1, 2);
        BlockPos beltEnd = util.grid().at(5, 1, 2);
        BlockPos depotPos = util.grid().at(3, 1, 3);
        BlockPos atomizerBase = util.grid().at(3, 1, 5);
        BlockPos atomizerPos = util.grid().at(3, 2, 5);
        Direction facing = Direction.NORTH;

        Selection atomizerSel = util.select().position(atomizerPos);
        Selection supportSel = util.select().position(atomizerBase);
        Selection depotSel = util.select().position(depotPos);
        Selection beltSel = util.select().fromTo(1, 1, 2, 5, 1, 2);
        Selection kineticsSel = util.select().position(3, 2, 6)
                .add(util.select().position(4, 1, 6));
        Selection airCurrentSel = util.select().fromTo(3, 1, 2, 3, 2, 4);

        scene.world().setKineticSpeed(atomizerSel.add(kineticsSel).add(beltSel), 0);
        scene.world().showSection(beltSel, Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(depotSel, Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(supportSel.add(atomizerSel), Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(kineticsSel, Direction.WEST);
        scene.idle(18);

        scene.overlay().showText(80)
                .colored(PonderPalette.GREEN)
                .text(tr("fluid.ponder.fluid_atomizer.text_1"))
                .pointAt(util.vector().topOf(atomizerPos))
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showControls(util.vector().blockSurface(atomizerPos, Direction.WEST), Pointing.RIGHT, 30)
                .rightClick()
                .withItem(new ItemStack(Items.WATER_BUCKET));
        fillAtomizer(scene, atomizerPos, new FluidStack(Fluids.WATER, 1000));
        scene.idle(20);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text(tr("fluid.ponder.fluid_atomizer.text_2"))
                .pointAt(util.vector().blockSurface(atomizerPos, facing))
                .placeNearTarget();
        scene.idle(90);

        scene.world().setKineticSpeed(atomizerSel, 32);
        scene.world().setKineticSpeed(kineticsSel, -16);
        scene.world().setKineticSpeed(beltSel, 16);
        scene.idle(20);

        scene.overlay().showOutlineWithText(airCurrentSel, 70)
                .colored(PonderPalette.BLUE)
                .text(tr("fluid.ponder.fluid_atomizer.text_3"))
                .pointAt(util.vector().centerOf(3, 1, 3))
                .placeNearTarget();
        scene.idle(80);

        ElementLink<BeltItemElement> gravel = scene.world().createItemOnBelt(beltStart, Direction.EAST, new ItemStack(Items.GRAVEL));
        scene.idle(32);
        scene.world().stallBeltItem(gravel, true);
        sprayFluid(scene, nozzle(util, atomizerPos, facing), util.vector().topOf(beltMiddle), new FluidStack(Fluids.WATER, 250), 18, 0.08f);
        scene.idle(16);
        scene.world().changeBeltItemTo(gravel, new ItemStack(Items.FLINT));
        scene.idle(10);
        scene.world().stallBeltItem(gravel, false);
        scene.idle(10);

        scene.world().modifyBlockEntity(depotPos, DepotBlockEntity.class, depot -> depot.setHeldItem(new ItemStack(Items.SAND)));
        scene.idle(8);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text(tr("fluid.ponder.fluid_atomizer.text_4"))
                .pointAt(util.vector().topOf(depotPos))
                .placeNearTarget();
        scene.idle(70);

        sprayFluid(scene, nozzle(util, atomizerPos, facing), util.vector().topOf(depotPos), new FluidStack(Fluids.WATER, 250), 16, 0.08f);
        scene.idle(10);
        scene.world().modifyBlockEntity(depotPos, DepotBlockEntity.class, depot -> depot.setHeldItem(new ItemStack(Items.CLAY_BALL)));
        scene.idle(45);
        scene.world().removeItemsFromBelt(beltEnd);
        scene.idle(10);

        scene.overlay().showControls(util.vector().blockSurface(atomizerPos, Direction.WEST), Pointing.RIGHT, 30)
                .rightClick()
                .withItem(new ItemStack(Items.LAVA_BUCKET));
        fillAtomizer(scene, atomizerPos, new FluidStack(Fluids.LAVA, 1000));
        scene.idle(35);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.RED)
                .text(tr("fluid.ponder.fluid_atomizer.text_5"))
                .pointAt(util.vector().topOf(atomizerPos))
                .placeNearTarget();
        scene.idle(90);

        sprayFluid(scene, nozzle(util, atomizerPos, facing), util.vector().topOf(depotPos), new FluidStack(Fluids.LAVA, 250), 16, 0.08f);
        scene.idle(10);
        scene.world().modifyBlockEntity(depotPos, DepotBlockEntity.class, depot -> depot.setHeldItem(new ItemStack(Items.BRICK)));
        scene.idle(20);

        ElementLink<BeltItemElement> rawGold = scene.world().createItemOnBelt(beltStart, Direction.EAST, new ItemStack(Items.RAW_GOLD));
        scene.idle(32);
        scene.world().stallBeltItem(rawGold, true);
        sprayFluid(scene, nozzle(util, atomizerPos, facing), util.vector().topOf(beltMiddle), new FluidStack(Fluids.LAVA, 250), 18, 0.08f);
        scene.idle(16);
        scene.world().changeBeltItemTo(rawGold, new ItemStack(Items.GOLD_INGOT));
        scene.idle(10);
        scene.world().stallBeltItem(rawGold, false);
        scene.idle(30);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text(tr("fluid.ponder.fluid_atomizer.text_6"))
                .pointAt(util.vector().blockSurface(atomizerPos, facing))
                .placeNearTarget();
        scene.idle(100);

        scene.markAsFinished();
    }

    public static void potionCloud(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("fluid_atomizer_potion", "Potion Clouds from Fluid Atomizers");
        scene.configureBasePlate(0, 0, 6);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos sheepStand = util.grid().at(3, 1, 2);
        BlockPos atomizerBase = util.grid().at(3, 1, 5);
        BlockPos atomizerPos = util.grid().at(3, 2, 5);
        BlockPos tankPos = util.grid().at(5, 1, 5);
        Direction facing = Direction.NORTH;

        Selection standSel = util.select().fromTo(2, 1, 2, 4, 1, 2);
        Selection atomizerSel = util.select().position(atomizerPos);
        Selection supportSel = util.select().position(atomizerBase);
        Selection tankSel = util.select().fromTo(5, 1, 5, 5, 3, 5);
        Selection kineticsSel = util.select().position(3, 2, 6)
                .add(util.select().position(4, 1, 6));
        Selection cloudSel = util.select().fromTo(3, 2, 2, 3, 2, 4);

        scene.world().setKineticSpeed(atomizerSel.add(kineticsSel), 0);
        scene.world().showSection(standSel, Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(supportSel.add(atomizerSel), Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(tankSel, Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(kineticsSel, Direction.WEST);
        scene.idle(18);

        ItemStack lingeringPoison = PotionContents.createItemStack(Items.LINGERING_POTION, Potions.POISON);
        PotionContents poisonContents = new PotionContents(Potions.POISON);
        FluidStack potionFluid = PotionFluid.of(1000, poisonContents, PotionFluid.BottleType.LINGERING);

        scene.overlay().showControls(util.vector().blockSurface(atomizerPos, Direction.WEST), Pointing.RIGHT, 30)
                .rightClick()
                .withItem(lingeringPoison);
        fillAtomizer(scene, atomizerPos, potionFluid);
        fillTank(scene, tankPos, PotionFluid.of(16000, poisonContents, PotionFluid.BottleType.LINGERING));
        scene.idle(20);

        scene.overlay().showText(90)
                .colored(PonderPalette.GREEN)
                .text(tr("fluid.ponder.fluid_atomizer_potion.text_1"))
                .pointAt(util.vector().topOf(atomizerPos))
                .placeNearTarget();
        scene.idle(100);

        scene.world().setKineticSpeed(atomizerSel, 32);
        scene.world().setKineticSpeed(kineticsSel, -16);
        scene.idle(20);

        ElementLink<EntityElement> sheep = scene.world().createEntity(level -> {
            Sheep entity = EntityType.SHEEP.create(level);
            Vec3 p = util.vector().topOf(sheepStand);
            entity.setPos(p.x, p.y, p.z);
            entity.xo = p.x;
            entity.yo = p.y;
            entity.zo = p.z;
            WalkAnimationState animation = entity.walkAnimation;
            animation.update(-animation.position(), 1);
            animation.setSpeed(1);
            entity.yRotO = 180;
            entity.setYRot(180);
            entity.yHeadRotO = 180;
            entity.yHeadRot = 180;
            return entity;
        });
        scene.idle(10);

        scene.overlay().showOutlineWithText(cloudSel, 70)
                .attachKeyFrame()
                .colored(PonderPalette.MEDIUM)
                .text(tr("fluid.ponder.fluid_atomizer_potion.text_2"))
                .pointAt(util.vector().topOf(sheepStand))
                .placeNearTarget();
        scene.idle(40);

        Vec3 cloudCenter = util.vector().centerOf(3, 2, 3).add(0, 0.05, 0);
        Vec3 pathMid = util.vector().centerOf(3, 2, 4).add(0, 0.05, 0);
        Vec3 sheepBody = util.vector().centerOf(sheepStand).add(0, 0.8, 0);
        Vec3 sheepCloud = util.vector().topOf(sheepStand).add(0, 0.45, 0);
        for (int i = 0; i < 4; i++) {
            Vec3 nozzle = nozzle(util, atomizerPos, facing);
            sprayFluid(scene, nozzle, pathMid, potionFluid, 12, 0.06f);
            sprayFluid(scene, pathMid, sheepCloud, potionFluid, 14, 0.08f);
            emitCloud(scene, pathMid, potionFluid, 8, 0.08f);
            scene.idle(10);
        }

        scene.world().modifyEntity(sheep, entity -> {
            if (entity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
            }
        });
        emitCloud(scene, sheepCloud, potionFluid, 14, 0.18f);
        emitCloud(scene, sheepBody, potionFluid, 10, 0.16f);
        scene.idle(45);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .colored(PonderPalette.WHITE)
                .text(tr("fluid.ponder.fluid_atomizer_potion.text_3"))
                .pointAt(cloudCenter)
                .placeNearTarget();
        scene.idle(90);

        scene.world().modifyEntity(sheep, entity -> entity.discard());
        scene.idle(20);
        scene.markAsFinished();
    }

    private static void fillAtomizer(CreateSceneBuilder scene, BlockPos atomizerPos, FluidStack fluidStack) {
        scene.world().modifyBlockEntity(atomizerPos, FluidAtomizerBlockEntity.class, be -> {
            IFluidHandler handler = be.getTankInventory();
            if (handler == null) {
                return;
            }
            handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
            handler.fill(fluidStack.copy(), IFluidHandler.FluidAction.EXECUTE);
        });
    }

    private static void fillTank(CreateSceneBuilder scene, BlockPos tankPos, FluidStack fluidStack) {
        scene.world().modifyBlockEntity(tankPos, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
            be.getTankInventory().fill(fluidStack.copy(), IFluidHandler.FluidAction.EXECUTE);
        });
    }

    private static void sprayFluid(CreateSceneBuilder scene, Vec3 start, Vec3 end, FluidStack fluidStack, int particles, float spread) {
        ParticleOptions particle = FluidFX.getFluidParticle(fluidStack);
        RandomSource random = scene.getScene().getWorld().random;
        Vec3 motion = end.subtract(start).normalize().scale(0.06f);
        Vec3 line = end.subtract(start);

        for (int i = 0; i < particles; i++) {
            float t = particles == 1 ? 1f : i / (particles - 1f);
            Vec3 pos = start.add(line.scale(t));
            pos = pos.add(VecHelper.offsetRandomly(Vec3.ZERO, random, spread));
            scene.effects().emitParticles(pos,
                    scene.effects().simpleParticleEmitter(particle,
                            VecHelper.offsetRandomly(motion, random, spread * 0.5f)),
                    0.2f, 1);
        }
    }

    private static void emitCloud(CreateSceneBuilder scene, Vec3 center, FluidStack fluidStack, int particles, float spread) {
        ParticleOptions particle = FluidFX.getFluidParticle(fluidStack);
        RandomSource random = scene.getScene().getWorld().random;
        for (int i = 0; i < particles; i++) {
            scene.effects().emitParticles(
                    center.add(VecHelper.offsetRandomly(Vec3.ZERO, random, spread)),
                    scene.effects().simpleParticleEmitter(particle,
                            VecHelper.offsetRandomly(new Vec3(0, 0.02, 0), random, spread * 0.2f)),
                    0.15f, 1);
        }
    }

    private static Vec3 nozzle(SceneBuildingUtil util, BlockPos atomizerPos, Direction facing) {
        return util.vector().centerOf(atomizerPos)
                .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.62))
                .add(0, 0.02, 0);
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }
}
