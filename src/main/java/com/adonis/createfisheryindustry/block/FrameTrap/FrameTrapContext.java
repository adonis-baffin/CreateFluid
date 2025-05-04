package com.adonis.createfisheryindustry.block.FrameTrap;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class FrameTrapContext {
    protected final ItemStack fishingRod;
    protected final FishingHook fishingHook;
    public final RandomSource randomSource; // 公开 randomSource 以供 FrameTrapMovementBehaviour 使用
    protected final Set<BlockPos> visitedBlocks = new HashSet<>();

    protected final int minCatchTime = 800;
    protected final int maxCatchTime = 1400;
    protected final float catchSuccessRate = 0.4f;

    public int timeUntilCatch;

    public FrameTrapContext(ServerLevel level, ItemStack fishingRod) {
        this.fishingRod = fishingRod;
        this.fishingHook = new FishingHook(EntityType.FISHING_BOBBER, level);
        // 通过反射获取 syncronizedRandom
        RandomSource tempRandomSource;
        try {
            Field randomField = FishingHook.class.getDeclaredField("syncronizedRandom");
            randomField.setAccessible(true);
            tempRandomSource = (RandomSource) randomField.get(this.fishingHook);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // 失败时创建新的 RandomSource
            tempRandomSource = RandomSource.create();
        }
        this.randomSource = tempRandomSource;
        this.reset(level);
    }

    public void reset(ServerLevel level) {
        this.visitedBlocks.clear();
        this.timeUntilCatch = Mth.nextInt(randomSource, minCatchTime, maxCatchTime);
    }

    public boolean visitNewPosition(ServerLevel level, BlockPos pos) {
        boolean inWater = level.getFluidState(pos).isSource();
        if (!inWater) return false;
        visitedBlocks.add(pos);
        return true;
    }

    public LootParams buildLootContext(com.simibubi.create.content.contraptions.behaviour.MovementContext context, ServerLevel level, BlockPos pos) {
        fishingHook.setPos(context.position.x, context.position.y, context.position.z);
        return new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, context.position)
                .withParameter(LootContextParams.TOOL, fishingRod)
                .withParameter(LootContextParams.THIS_ENTITY, fishingHook)
                .withLuck(EnchantmentHelper.getFishingLuckBonus(fishingRod)) // 1.20.1 Forge 签名
                .create(LootContextParamSets.FISHING);
    }

    public boolean canCatch() {
        return randomSource.nextFloat() < catchSuccessRate;
    }

    public void invalidate(ServerLevel level) {
        reset(level);
        fishingHook.discard();
    }

    public FishingHook getFishingHook() {
        return fishingHook;
    }

    public ItemStack getFishingRod() {
        return fishingRod;
    }
}