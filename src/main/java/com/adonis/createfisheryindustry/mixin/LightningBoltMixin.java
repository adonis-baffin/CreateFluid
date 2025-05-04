package com.adonis.createfisheryindustry.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightningBolt.class)
public abstract class LightningBoltMixin extends Entity {
    @Shadow
    protected abstract BlockPos getStrikePosition();

    private LightningBoltMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick$transformGrassToDiamond(CallbackInfo ci) {
        Level level = this.level();
        BlockPos strikePos = this.getStrikePosition();

        // 检查以打击点为中心，3x3x3范围内的草方块
        for (BlockPos pos : BlockPos.betweenClosed(strikePos.offset(-1, -1, -1), strikePos.offset(1, 1, 1))) {
            if (level.getBlockState(pos).is(Blocks.GRASS_BLOCK)) {
                level.setBlockAndUpdate(pos, Blocks.DIAMOND_BLOCK.defaultBlockState());
            }
        }
    }
}