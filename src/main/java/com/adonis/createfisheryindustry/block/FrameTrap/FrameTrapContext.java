package com.adonis.createfisheryindustry.block.FrameTrap;

import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class FrameTrapContext {
    protected final ItemStack fishingRod;
    protected final FishingHook fishingHook;
    protected final RandomSource randomSource;
    protected final Set<BlockPos> visitedBlocks = new HashSet<>();

    // 基础时间范围（将通过配置倍数调整）
    protected final int baseMinCatchTime = 800;
    protected final int baseMaxCatchTime = 1400;

    public int timeUntilCatch;

    public FrameTrapContext(ServerLevel level, ItemStack fishingRod) {
        this.fishingRod = fishingRod;
        this.fishingHook = new FishingHook(EntityType.FISHING_BOBBER, level);
        this.randomSource = RandomSource.create();
        this.reset(level);
    }

    public void reset(ServerLevel level) {
        this.visitedBlocks.clear();

        // 使用钓鱼的配置
        double cooldownMultiplier = CreateFisheryCommonConfig.getFishingCooldownMultiplier();
        int adjustedMinTime = (int) (baseMinCatchTime * cooldownMultiplier);
        int adjustedMaxTime = (int) (baseMaxCatchTime * cooldownMultiplier);

        this.timeUntilCatch = Mth.nextInt(randomSource, adjustedMinTime, adjustedMaxTime);
    }

    public boolean visitNewPosition(ServerLevel level, BlockPos pos) {
        // 检查是否在水中 - 使用我们自己的检查方法
        boolean inWater = isWaterBlock(level, pos);

        if (!inWater) return false;

        visitedBlocks.add(pos);
        return true;
    }

    /**
     * 检查位置是否为水源方块
     */
    private boolean isWaterBlock(ServerLevel level, BlockPos pos) {
        var fluidState = level.getFluidState(pos);
        return fluidState.is(FluidTags.WATER) && fluidState.isSource();
    }

    /**
     * 计算开放水域（简化版本）
     */
    private boolean calculateOpenWater(ServerLevel level, BlockPos pos) {
        // 检查5x4x5区域是否为开放水域
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 2; y++) {
                    BlockPos checkPos = pos.offset(x, y, z);

                    if (y == -1) {
                        // 底层必须是水
                        if (!isWaterBlock(level, checkPos)) {
                            return false;
                        }
                    } else {
                        // 其他层必须是水或空气
                        if (!isWaterBlock(level, checkPos) && !level.getBlockState(checkPos).isAir()) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * 获取钓鱼战利品表
     */
    public LootTable getLootTable(ServerLevel level) {
        return level.getServer().getLootData().getLootTable(BuiltInLootTables.FISHING);
    }

    public LootParams buildLootContext(com.simibubi.create.content.contraptions.behaviour.MovementContext context, ServerLevel level, BlockPos pos) {
        fishingHook.setPos(context.position.x, context.position.y, context.position.z);

        // 使用我们自己的开放水域检查
        boolean openWater = calculateOpenWater(level, pos);

        return new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, context.position)
                .withParameter(LootContextParams.TOOL, fishingRod)
                .withParameter(LootContextParams.THIS_ENTITY, fishingHook)
                .withLuck(EnchantmentHelper.getFishingLuckBonus(fishingRod)) // 1.20.1 Forge 签名
                .create(LootContextParamSets.FISHING);
    }

    public boolean canCatch() {
        // 使用配置的成功率
        double successRate = CreateFisheryCommonConfig.getFishingSuccessRate();
        return randomSource.nextFloat() < successRate;
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

    public RandomSource getRandomSource() {
        return randomSource;
    }
}