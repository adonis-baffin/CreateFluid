package com.adonis.fluid.packet;

import com.adonis.fluid.mixin.accessor.ArmBlockEntityAccessor;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.ListTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.fluids.FluidStack;
import com.adonis.fluid.block.Pipette.PipetteBlockEntity;
import org.joml.Vector3f;

import java.util.Random;

@OnlyIn(Dist.CLIENT)
public class ClientPacketHandler {

    // 处理移液器粒子
    public static void handlePipetteParticle(Vec3 pos, FluidStack fluid) {
        Level level = Minecraft.getInstance().level;
        if (level == null || fluid.isEmpty()) {
            return;
        }

        ParticleOptions particle = com.simibubi.create.content.fluids.FluidFX.getFluidParticle(fluid);

        for (int i = 0; i < 20; i++) {
            Vec3 motion = net.createmod.catnip.math.VecHelper.offsetRandomly(
                    Vec3.ZERO, level.random, 0.125F);
            motion = new Vec3(motion.x, -Math.abs(motion.y) * 0.5 - 0.1, motion.z);

            level.addParticle(particle, pos.x, pos.y, pos.z, motion.x, motion.y, motion.z);
        }
    }

    // 处理机械臂同步
    public static void handleArmSync(BlockPos pos, ListTag pointsTag) {
        Level world = Minecraft.getInstance().level;
        if (world == null) return;

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof ArmBlockEntity arm)) return;

        ArmBlockEntityAccessor accessor = (ArmBlockEntityAccessor) arm;

        accessor.getInputs().clear();
        accessor.getOutputs().clear();
        accessor.setInteractionPointTag(pointsTag);
        accessor.setUpdateInteractionPoints(true);
        accessor.setPhase(ArmBlockEntity.Phase.SEARCH_INPUTS);
        accessor.setChasedPointProgress(0.0F);
        accessor.setChasedPointIndex(-1);

        try {
            var initMethod = ArmBlockEntity.class.getDeclaredMethod("initInteractionPoints");
            initMethod.setAccessible(true);
            initMethod.invoke(arm);
        } catch (Exception e) {
            // 静默处理
        }

        arm.setChanged();
    }

    // 处理移液器同步
    public static void handlePipetteSync(BlockPos pos, ListTag pointsTag) {
        Level world = Minecraft.getInstance().level;
        if (world == null) return;

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof PipetteBlockEntity pipette)) return;

        pipette.inputs.clear();
        pipette.outputs.clear();
        pipette.setInteractionPointTag(pointsTag);
        pipette.setUpdateInteractionPoints(true);
        pipette.forceInitInteractionPoints();
        pipette.resetMovementState();
        pipette.setChanged();
    }

    // 处理移液器客户端请求
    public static void handlePipetteRequest(BlockPos pos) {
        com.adonis.fluid.handler.PipetteFluidInteractionPointHandler.flushSettings(pos);
    }

    // 处理 Frogport 连接反馈
    public static void handleFrogportFeedback(boolean success, BlockPos frogportPos, String messageKey) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            LangBuilder builder = com.simibubi.create.foundation.utility.CreateLang.builder().translate(messageKey);
            if (success) {
                builder.color(10416499).sendStatus(mc.player);
                mc.level.playLocalSound(
                        (double) frogportPos.getX() + 0.5,
                        (double) frogportPos.getY() + 0.5,
                        (double) frogportPos.getZ() + 0.5,
                        SoundEvents.NOTE_BLOCK_CHIME.value(),
                        SoundSource.BLOCKS,
                        0.8F,
                        1.0F,
                        false
                );
            } else {
                builder.color(16736625).sendStatus(mc.player);
            }
        }
    }

    // 处理 Clipboard 地址粒子效果
    public static void handleClipboardParticle(BlockPos pos) {
        spawnClipboardParticles(pos);
        playClipboardSound(pos);
    }

    private static void spawnClipboardParticles(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Random random = new Random();
            for (int i = 0; i < 10; i++) {
                double x = (double) pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
                double y = (double) pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
                double z = (double) pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
                mc.level.addParticle(new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 1.0F), x, y, z, 0.0, 0.0, 0.0);
            }
        }
    }

    private static void playClipboardSound(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.level.playLocalSound(
                    (double) pos.getX() + 0.5,
                    (double) pos.getY() + 0.5,
                    (double) pos.getZ() + 0.5,
                    SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.BLOCKS,
                    0.5F,
                    1.0F,
                    false
            );
        }
    }
}