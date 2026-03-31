package com.adonis.fluid.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightningBolt.class)
public abstract class LightningBoltMixin {

    @Shadow
    private int life;

    // 使用 @Shadow 访问私有方法
    @Shadow
    protected abstract BlockPos getStrikePosition();

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (this.life == 2) { // 在第一个有效tick执行
            LightningBolt bolt = (LightningBolt) (Object) this;
            Level level = bolt.level();

            if (!level.isClientSide) {
                // 使用 shadow 方法获取雷击位置
                BlockPos strikePos = this.getStrikePosition();

                // 检查3x3x3区域内的金块
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                            BlockPos checkPos = strikePos.offset(x, y, z);
                            BlockState state = level.getBlockState(checkPos);

                            if (state.is(Blocks.GOLD_BLOCK)) {
                                // 将金块变成钻石块
                                level.setBlock(checkPos, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);

                                // 添加粒子效果
                                level.levelEvent(2001, checkPos,
                                        Blocks.GOLD_BLOCK.defaultBlockState().hashCode());

                                // 打印日志确认Mixin工作
                                System.out.println("Lightning struck gold block at " + checkPos +
                                        " and turned it into diamond!");
                            }
                        }
                    }
                }
            }
        }
    }
}