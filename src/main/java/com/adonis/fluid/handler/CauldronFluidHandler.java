package com.adonis.fluid.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;

public class CauldronFluidHandler implements IFluidHandler {
    private final Level level;
    private final BlockPos pos;
    private BlockState state;

    // 炼药锅容量常量
    private static final int FULL_AMOUNT = 1000; // 满炼药锅统一为1000mB
    private static final int WATER_LEVEL_CAPACITY = 250; // 水炼药锅每层250mB

    public CauldronFluidHandler(Level level, BlockPos pos, BlockState state) {
        this.level = level;
        this.pos = pos;
        this.state = state;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Nonnull
    @Override
    public FluidStack getFluidInTank(int tank) {
        if (tank != 0) return FluidStack.EMPTY;

        // 更新状态
        state = level.getBlockState(pos);

        // 水炼药锅 - 只有满的才能抽取
        if (state.is(Blocks.WATER_CAULDRON)) {
            int level = state.getValue(LayeredCauldronBlock.LEVEL);
            if (level == 3) { // 只有满的水炼药锅才返回流体
                return new FluidStack(Fluids.WATER, FULL_AMOUNT);
            }
            return FluidStack.EMPTY;
        }

        // 岩浆炼药锅（满的）
        if (state.is(Blocks.LAVA_CAULDRON)) {
            return new FluidStack(Fluids.LAVA, FULL_AMOUNT);
        }

        // TODO: 细雪炼药锅支持 - 待细雪流体迁移后添加
        /*
        if (state.is(Blocks.POWDER_SNOW_CAULDRON)) {
            int level = state.getValue(LayeredCauldronBlock.LEVEL);
            if (level == 3) {
                return new FluidStack(PowderSnowFluid, FULL_AMOUNT);
            }
            return FluidStack.EMPTY;
        }
        */

        return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return FULL_AMOUNT;
    }

    @Override
    public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
        if (tank != 0 || stack.isEmpty()) return false;

        // 检查流体类型是否被支持
        return stack.getFluid() == Fluids.WATER ||
                stack.getFluid() == Fluids.LAVA;
        // TODO: 添加细雪流体检查
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !isFluidValid(0, resource)) {
            return 0;
        }

        // 更新状态
        state = level.getBlockState(pos);

        // 空炼药锅
        if (state.is(Blocks.CAULDRON)) {
            BlockState newState = null;
            int fillAmount = 0;

            if (resource.getFluid() == Fluids.WATER) {
                // 水：250mB填充一层
                if (resource.getAmount() >= WATER_LEVEL_CAPACITY) {
                    int levels = Math.min(3, resource.getAmount() / WATER_LEVEL_CAPACITY);
                    fillAmount = levels * WATER_LEVEL_CAPACITY;
                    newState = Blocks.WATER_CAULDRON.defaultBlockState()
                            .setValue(LayeredCauldronBlock.LEVEL, levels);
                }
            } else if (resource.getFluid() == Fluids.LAVA) {
                // 岩浆：必须一次性填满1000mB
                if (resource.getAmount() >= FULL_AMOUNT) {
                    newState = Blocks.LAVA_CAULDRON.defaultBlockState();
                    fillAmount = FULL_AMOUNT;
                }
            }
            // TODO: 添加细雪流体填充逻辑

            if (newState != null && fillAmount > 0) {
                if (action.execute()) {
                    level.setBlockAndUpdate(pos, newState);
                    level.gameEvent(null, net.minecraft.world.level.gameevent.GameEvent.BLOCK_CHANGE, pos);

                    // 播放音效
                    if (resource.getFluid() == Fluids.WATER) {
                        level.levelEvent(1047, pos, 0); // 水的音效
                    } else if (resource.getFluid() == Fluids.LAVA) {
                        level.levelEvent(1046, pos, 0); // 岩浆的音效
                    }
                }
                return fillAmount;
            }
            return 0;
        }

        // 部分填充的水炼药锅 - 可以继续填充
        if (state.is(Blocks.WATER_CAULDRON) && resource.getFluid() == Fluids.WATER) {
            int currentLevel = state.getValue(LayeredCauldronBlock.LEVEL);
            if (currentLevel < 3) {
                int maxLevelsCanAdd = 3 - currentLevel;
                int levelsToAdd = Math.min(maxLevelsCanAdd, resource.getAmount() / WATER_LEVEL_CAPACITY);

                if (levelsToAdd > 0) {
                    int fillAmount = levelsToAdd * WATER_LEVEL_CAPACITY;

                    if (action.execute()) {
                        int newLevel = currentLevel + levelsToAdd;
                        level.setBlockAndUpdate(pos, state.setValue(LayeredCauldronBlock.LEVEL, newLevel));
                        level.gameEvent(null, net.minecraft.world.level.gameevent.GameEvent.BLOCK_CHANGE, pos);
                        level.levelEvent(1047, pos, 0);
                    }

                    return fillAmount;
                }
            }
        }

        // 细雪炼药锅暂时不支持部分填充

        return 0;
    }

    @Nonnull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;

        FluidStack contained = getFluidInTank(0);
        if (contained.isEmpty() || !FluidStack.isSameFluidSameComponents(contained, resource)) {
            return FluidStack.EMPTY;
        }

        return drain(resource.getAmount(), action);
    }

    @Nonnull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) return FluidStack.EMPTY;

        // 更新状态
        state = level.getBlockState(pos);
        FluidStack result = FluidStack.EMPTY;

        // 水炼药锅 - 只能抽取满的，一次性抽取1000mB
        if (state.is(Blocks.WATER_CAULDRON)) {
            int currentLevel = state.getValue(LayeredCauldronBlock.LEVEL);
            if (currentLevel == 3 && maxDrain >= FULL_AMOUNT) {
                result = new FluidStack(Fluids.WATER, FULL_AMOUNT);

                if (action.execute()) {
                    level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
                    level.gameEvent(null, net.minecraft.world.level.gameevent.GameEvent.BLOCK_CHANGE, pos);
                }
            }
        }
        // 岩浆炼药锅 - 一次性抽取1000mB
        else if (state.is(Blocks.LAVA_CAULDRON)) {
            if (maxDrain >= FULL_AMOUNT) {
                result = new FluidStack(Fluids.LAVA, FULL_AMOUNT);

                if (action.execute()) {
                    level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
                    level.gameEvent(null, net.minecraft.world.level.gameevent.GameEvent.BLOCK_CHANGE, pos);
                }
            }
        }
        // TODO: 细雪炼药锅抽取逻辑
        /*
        else if (state.is(Blocks.POWDER_SNOW_CAULDRON)) {
            int currentLevel = state.getValue(LayeredCauldronBlock.LEVEL);
            if (currentLevel == 3 && maxDrain >= FULL_AMOUNT) {
                result = new FluidStack(PowderSnowFluid, FULL_AMOUNT);

                if (action.execute()) {
                    level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
                    level.gameEvent(null, net.minecraft.world.level.gameevent.GameEvent.BLOCK_CHANGE, pos);
                }
            }
        }
        */

        return result;
    }
}