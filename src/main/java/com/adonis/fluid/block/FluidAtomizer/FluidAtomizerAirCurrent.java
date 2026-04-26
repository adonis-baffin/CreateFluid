package com.adonis.fluid.block.FluidAtomizer;

import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.AirFlowParticleData;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;

public class FluidAtomizerAirCurrent extends AirCurrent {

    public FluidAtomizerAirCurrent(com.adonis.fluid.block.FluidAtomizer.FluidAtomizerBlockEntity source) {
        super(source);
    }

    @Override
    public void tick() {
        if (direction == null)
            rebuild();
        Level world = source.getAirCurrentWorld();
        if (world != null && world.isClientSide) {
            float offset = pushing ? 0.5f : maxDistance + .5f;
            Vec3 origin = VecHelper.getCenterOf(source.getAirCurrentPos())
                    .add(Vec3.atLowerCornerOf(direction.getNormal()).scale(offset));

            FluidAtomizerBlockEntity atomizer = (FluidAtomizerBlockEntity) source;
            if (atomizer.isPotionFluid()) {
                // 药水流体：用彩色粒子流替代白色气流粒子，沿路径分布
                for (int i = 0; i < 3; i++) {
                    spawnPotionStreamParticle(world, origin, direction, atomizer.getPotionColor(), maxDistance);
                }
            } else {
                // 普通气流粒子
                if (world.random.nextFloat() < AllConfigs.client().fanParticleDensity.get())
                    world.addParticle(new AirFlowParticleData((Vec3i) source.getAirCurrentPos()), origin.x, origin.y, origin.z, 0, 0, 0);
            }
        }

        tickAffectedEntities(world);
        tickAffectedHandlers();
    }

    /**
     * 沿气流路径生成药水云雾粒子，模拟喷射出去的雾化气流
     */
    private void spawnPotionStreamParticle(Level world, Vec3 origin, Direction direction, int color, float streamLength) {
        // 在气流路径上随机选一个位置（0 ~ streamLength），让粒子分布在整条气流上
        float distanceAlong = world.random.nextFloat() * streamLength;
        Vec3 spawnPos = origin.add(Vec3.atLowerCornerOf(direction.getNormal()).scale(distanceAlong));

        // 横向锥形扩散，越远离出口扩散越大
        float spread = 0.05f + distanceAlong * 0.03f;
        Vec3 sideOffset = Vec3.atLowerCornerOf(direction.getClockWise().getNormal())
                .scale((world.random.nextFloat() - 0.5f) * spread);
        Vec3 upOffset = new Vec3(0, (world.random.nextFloat() - 0.5f) * spread * 0.5f, 0);
        spawnPos = spawnPos.add(sideOffset).add(upOffset);

        // 速度：沿气流方向较快（0.35~0.55），模拟喷射感
        Vec3 motion = Vec3.atLowerCornerOf(direction.getNormal())
                .scale(0.35 + world.random.nextDouble() * 0.2);

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        world.addParticle(
                new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(r, g, b), 1.0f),
                spawnPos.x, spawnPos.y, spawnPos.z,
                motion.x, motion.y, motion.z
        );
    }

    @Override
    protected void tickAffectedEntities(Level world) {
        FluidAtomizerBlockEntity atomizer = (FluidAtomizerBlockEntity) source;
        var potionEffects = atomizer.getPotionEffects();
        boolean isPotion = potionEffects != null && !potionEffects.isEmpty();

        for (Iterator<Entity> iterator = caughtEntities.iterator(); iterator.hasNext(); ) {
            Entity entity = iterator.next();
            if (!entity.isAlive() || !entity.getBoundingBox().intersects(bounds) || AirCurrent.isPlayerCreativeFlying(entity)) {
                iterator.remove();
                continue;
            }

            // 药水效果处理（独立于 FanProcessingType）
            if (isPotion && entity instanceof net.minecraft.world.entity.LivingEntity living) {
                if (world != null && !world.isClientSide) {
                    applyPotionEffects(living, potionEffects);
                }
            }

            double entityDistance = VecHelper.alignedDistanceToFace(entity.position(), source.getAirCurrentPos(), direction);

            FanProcessingType processingType = getTypeAt((float) entityDistance);
            if (processingType == null)
                continue;

            if (entity instanceof ItemEntity itemEntity) {
                if (world != null && world.isClientSide) {
                    processingType.spawnProcessingParticles(world, entity.position());
                    continue;
                }
                if (FanProcessing.canProcess(itemEntity, processingType)) {
                    FanProcessing.applyProcessing(itemEntity, processingType);
                    ((FluidAtomizerBlockEntity) source).award(AllAdvancements.FAN_PROCESSING);
                }
                continue;
            }

            if (world != null)
                processingType.affectEntity(entity, world);
        }
    }

    private void applyPotionEffects(net.minecraft.world.entity.LivingEntity entity, java.util.List<net.minecraft.world.effect.MobEffectInstance> effects) {
        for (net.minecraft.world.effect.MobEffectInstance effectInstance : effects) {
            var effect = effectInstance.getEffect().value();
            if (effect.isInstantenous()) {
                effect.applyInstantenousEffect(null, null, entity, effectInstance.getAmplifier(), 0.5D);
            } else {
                int duration = Math.min(effectInstance.getDuration(), 100);
                entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        effectInstance.getEffect(), duration, effectInstance.getAmplifier(), false, true));
            }
        }
    }

    @Override
    public FanProcessingType getTypeAt(float offset) {
        FanProcessingType internalType = ((FluidAtomizerBlockEntity) source).getInternalProcessingType();
        if (internalType == null) {
            return super.getTypeAt(offset);
        }

        if (offset < 0) {
            return internalType;
        }
        if (offset > maxDistance) {
            return null;
        }

        Level world = source.getAirCurrentWorld();
        if (world == null || direction == null) {
            return internalType;
        }

        FanProcessingType currentType = internalType;
        int limit = getLimit();

        if (pushing) {
            int maxIndex = Math.min(limit, Mth.floor(offset) + 1);
            for (int i = 1; i <= maxIndex; i++) {
                BlockPos pos = source.getAirCurrentPos().relative(direction, i);
                FanProcessingType externalType = FanProcessingType.getAt(world, pos);
                if (externalType != null) {
                    currentType = externalType;
                }
            }
            return currentType;
        }

        int minIndex = Math.max(1, Mth.floor(offset) + 1);
        for (int i = limit; i >= minIndex; i--) {
            BlockPos pos = source.getAirCurrentPos().relative(direction, i);
            FanProcessingType externalType = FanProcessingType.getAt(world, pos);
            if (externalType != null) {
                currentType = externalType;
            }
        }
        return currentType;
    }

    private int getLimit() {
        if ((float) (int) maxDistance == maxDistance) {
            return (int) maxDistance;
        }
        return (int) maxDistance + 1;
    }
}
