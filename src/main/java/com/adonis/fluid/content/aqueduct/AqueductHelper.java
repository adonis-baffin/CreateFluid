package com.adonis.fluid.content.aqueduct;

import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

public class AqueductHelper {

    public static boolean hasFluidConnection(Level level, BlockPos pos) {
        // 检查是否有流体连接（管道、容器等）
        for (Direction dir : Direction.values()) {
            BlockPos adjacentPos = pos.relative(dir);
            BlockEntity be = level.getBlockEntity(adjacentPos);

            if (be != null && be.getCapability(ForgeCapabilities.FLUID_HANDLER, dir.getOpposite()).isPresent()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isFluidInput(Level level, BlockPos pos) {
        // 判断连接的流体源是否为输入
        // 这里需要检查是否有动力泵推送流体进来
        for (Direction dir : Direction.values()) {
            if (hasPoweredPump(level, pos.relative(dir))) {
                BlockState pumpState = level.getBlockState(pos.relative(dir));
                if (pumpState.getBlock() instanceof PumpBlock) {
                    Direction pumpFacing = pumpState.getValue(PumpBlock.FACING);
                    // 如果泵朝向水渠，则为输入
                    return pumpFacing == dir.getOpposite();
                }
            }
        }
        return false;
    }

    public static boolean hasPoweredPump(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof PumpBlock) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof PumpBlockEntity) {
                PumpBlockEntity pump = (PumpBlockEntity) be;
                // 通过速度判断泵是否在运行
                return pump.getSpeed() != 0;
            }
        }
        return false;
    }

    public static int getTransferAmount(int available, int space, int transferRate) {
        return Math.min(Math.min(available, space), transferRate);
    }
}