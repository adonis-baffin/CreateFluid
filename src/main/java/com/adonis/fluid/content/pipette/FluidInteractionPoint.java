package com.adonis.fluid.content.pipette;

import com.adonis.fluid.block.FluidInterface.FluidInterfaceBlockEntity;
import com.adonis.fluid.block.SmartFluidInterface.SmartFluidInterfaceBlockEntity;
import com.adonis.fluid.registry.CFBlock;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

public class FluidInteractionPoint {
    private BlockPos pos;
    private Direction face;
    private Mode mode;
    private Level level;
    private long lastKnownValid = -1;

    public FluidInteractionPoint(Level level, BlockPos pos, BlockState state) {
        this.level = level;
        this.pos = pos;
        this.face = Direction.UP; // 默认从上方访问

        // 根据方块类型设置默认模式
        if (isBlazeBurner(state)) {
            this.mode = Mode.DEPOSIT; // 烈焰人燃烧室只能作为输出端
        } else if (isBeehive(state)) {
            this.mode = Mode.TAKE; // 蜂巢只能作为输入端
        } else {
            this.mode = Mode.DEPOSIT; // 其他方块默认为存放模式
        }
    }

    public static FluidInteractionPoint create(Level level, BlockPos pos, BlockState state) {
        // 检查是否为支持的方块类型
        if (isValidFluidBlock(state)) {
            return new FluidInteractionPoint(level, pos, state);
        }
        return null;
    }

    private static boolean isValidFluidBlock(BlockState state) {
        // 支持工作盆、流体接口、智能流体接口
        if (AllBlocks.BASIN.has(state) ||
                CFBlock.FLUID_INTERFACE.has(state) ||
                CFBlock.SMART_FLUID_INTERFACE.has(state)) {
            return true;
        }

        // 支持烈焰人燃烧室（作为输出端，接受岩浆）
        if (isBlazeBurner(state)) {
            return true;
        }

        // 直接支持蜂巢/蜂箱（作为输入端，提供蜂蜜）
        if (isBeehive(state)) {
            return true;
        }

        return false;
    }

    /**
     * 检查是否为烈焰人燃烧室
     */
    private static boolean isBlazeBurner(BlockState state) {
        // 使用Create的AllBlocks来检查
        return com.simibubi.create.AllBlocks.BLAZE_BURNER.has(state) ||
                com.simibubi.create.AllBlocks.LIT_BLAZE_BURNER.has(state);
    }

    /**
     * 检查是否为蜂巢或蜂箱
     */
    private static boolean isBeehive(BlockState state) {
        return state.getBlock() instanceof net.minecraft.world.level.block.BeehiveBlock;
    }

    public boolean isValid() {
        if (level == null) return false;

        long gameTime = level.getGameTime();
        if (gameTime == lastKnownValid) return true;

        BlockState state = level.getBlockState(pos);
        boolean valid = isValidFluidBlock(state) &&
                level.getBlockEntity(pos) != null &&
                getFluidHandler() != null;

        if (valid) {
            lastKnownValid = gameTime;
        }

        return valid;
    }

    @Nullable
    private IFluidHandler getFluidHandler() {
        BlockEntity be = level.getBlockEntity(pos);
        BlockState state = level.getBlockState(pos);

        // 特殊处理：烈焰人燃烧室（只接受岩浆）
        if (isBlazeBurner(state)) {
            return new BlazeBurnerFluidHandler(level, pos, state);
        }

        // 特殊处理：蜂巢/蜂箱（只提供蜂蜜）
        if (isBeehive(state)) {
            return new BeehiveFluidHandler(level, pos, state);
        }

        // 标准流体处理
        if (be == null) return null;

        return be.getCapability(ForgeCapabilities.FLUID_HANDLER, face).orElse(null);
    }

    public FluidStack extract(int maxAmount, boolean simulate) {
        IFluidHandler handler = getFluidHandler();
        if (handler == null) return FluidStack.EMPTY;

        return handler.drain(maxAmount, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
    }

    public FluidStack insert(FluidStack stack, boolean simulate) {
        IFluidHandler handler = getFluidHandler();
        if (handler == null) return stack;

        int filled = handler.fill(stack, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        FluidStack remainder = stack.copy();
        remainder.shrink(filled);
        return remainder;
    }

    public boolean canExtract() {
        IFluidHandler handler = getFluidHandler();
        if (handler == null) return false;

        // 尝试抽取少量流体来检查是否可以抽取
        // 对于智能流体接口，这会自动应用过滤逻辑
        return !handler.drain(1, IFluidHandler.FluidAction.SIMULATE).isEmpty();
    }

    public boolean canInsert(FluidStack stack) {
        IFluidHandler handler = getFluidHandler();
        if (handler == null) return false;

        // 对于智能流体接口，这会自动应用过滤逻辑
        return handler.fill(stack, IFluidHandler.FluidAction.SIMULATE) > 0;
    }

    /**
     * 检查是否可以抽取特定的流体类型
     * 这对智能流体接口的过滤特别有用
     */
    public boolean canExtractFluid(FluidStack fluidType) {
        IFluidHandler handler = getFluidHandler();
        if (handler == null) return false;

        // 创建一个小量的测试流体
        FluidStack testStack = fluidType.copy();
        testStack.setAmount(1);

        return !handler.drain(testStack, IFluidHandler.FluidAction.SIMULATE).isEmpty();
    }

    public void cycleMode() {
        // 烈焰人燃烧室只能作为输出端，不允许切换模式
        if (isBlazeBurner(level.getBlockState(pos))) {
            return;
        }

        // 蜂巢只能作为输入端，不允许切换模式
        if (isBeehive(level.getBlockState(pos))) {
            return;
        }

        // 其他方块可以正常切换模式
        mode = mode == Mode.TAKE ? Mode.DEPOSIT : Mode.TAKE;
    }

    public Mode getMode() {
        return mode;
    }

    public BlockPos getPos() {
        return pos;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public void keepAlive() {
        lastKnownValid = level != null ? level.getGameTime() : -1;
    }

    public void updateCachedState() {
        lastKnownValid = -1;
    }

    public CompoundTag serialize(BlockPos armPos) {
        CompoundTag nbt = new CompoundTag();
        nbt.put("Pos", net.minecraft.nbt.NbtUtils.writeBlockPos(pos.subtract(armPos)));
        nbt.putString("Mode", mode.name());
        nbt.putString("Face", face.name());
        return nbt;
    }

    @Nullable
    public static FluidInteractionPoint deserialize(CompoundTag nbt, Level level, BlockPos armPos) {
        BlockPos pos = net.minecraft.nbt.NbtUtils.readBlockPos(nbt.getCompound("Pos")).offset(armPos);
        BlockState state = level.getBlockState(pos);

        FluidInteractionPoint point = create(level, pos, state);
        if (point != null) {
            if (nbt.contains("Mode")) {
                try {
                    point.mode = Mode.valueOf(nbt.getString("Mode"));
                } catch (IllegalArgumentException e) {
                    point.mode = Mode.DEPOSIT;
                }
            }
            if (nbt.contains("Face")) {
                try {
                    point.face = Direction.valueOf(nbt.getString("Face"));
                } catch (IllegalArgumentException e) {
                    point.face = Direction.UP;
                }
            }
        }
        return point;
    }

    public static void transformPos(CompoundTag nbt, StructureTransform transform) {
        BlockPos pos = net.minecraft.nbt.NbtUtils.readBlockPos(nbt.getCompound("Pos"));
        pos = transform.apply(pos);
        nbt.put("Pos", net.minecraft.nbt.NbtUtils.writeBlockPos(pos));

        if (nbt.contains("Face")) {
            Direction face = Direction.valueOf(nbt.getString("Face"));
            // 先镜像，后旋转
            face = transform.mirrorFacing(face);
            face = transform.rotateFacing(face);
            nbt.putString("Face", face.name());
        }
    }

    public enum Mode {
        TAKE("fluid.mechanical_pipette.extract", 0xD73A3A),
        DEPOSIT("fluid.mechanical_pipette.deposit", 0x3AD73A);

        private final String translationKey;
        private final int color;

        Mode(String translationKey, int color) {
            this.translationKey = translationKey;
            this.color = color;
        }

        public String getTranslationKey() {
            return translationKey;
        }

        public int getColor() {
            return color;
        }
    }

    /**
     * 烈焰人燃烧室的特殊流体处理器 - 只接受岩浆
     */
    private static class BlazeBurnerFluidHandler implements IFluidHandler {
        private final Level level;
        private final BlockPos pos;
        private final BlockState state;

        public BlazeBurnerFluidHandler(Level level, BlockPos pos, BlockState state) {
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
            return FluidStack.EMPTY; // 烈焰人燃烧室不存储流体
        }

        @Override
        public int getTankCapacity(int tank) {
            return 1000; // 每次可接受1000mB岩浆
        }

        @Override
        public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
            // 只接受岩浆
            return stack.getFluid() == net.minecraft.world.level.material.Fluids.LAVA;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            // 只接受岩浆
            if (!isFluidValid(0, resource)) {
                return 0;
            }

            // 模拟用岩浆桶
            net.minecraft.world.item.ItemStack lavaBucket = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LAVA_BUCKET);

            try {
                // 直接调用Create的BlazeBurnerBlock.tryInsert方法
                net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> result =
                        com.simibubi.create.content.processing.burner.BlazeBurnerBlock.tryInsert(
                                state, level, pos, lavaBucket, true, false, true);

                if (result.getResult() == net.minecraft.world.InteractionResult.SUCCESS) {
                    if (action.execute()) {
                        // 实际执行插入
                        com.simibubi.create.content.processing.burner.BlazeBurnerBlock.tryInsert(
                                state, level, pos, lavaBucket, true, false, false);
                    }
                    return Math.min(resource.getAmount(), 1000);
                }
            } catch (Exception e) {
                // 如果调用失败，静默处理
                return 0;
            }

            return 0;
        }

        @Nonnull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY; // 不支持抽取
        }

        @Nonnull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY; // 不支持抽取
        }
    }

    /**
     * 蜂巢的特殊流体处理器 - 只提供蜂蜜
     */
    private static class BeehiveFluidHandler implements IFluidHandler {
        private final Level level;
        private final BlockPos pos;
        private final BlockState state;

        public BeehiveFluidHandler(Level level, BlockPos pos, BlockState state) {
            this.level = level;
            this.pos = pos;
            this.state = state;
        }

        /**
         * 获取蜂蜜流体 - 目前使用水作为占位符
         * TODO: 可以替换为真正的蜂蜜流体（如果Create或其他模组提供）
         */
        private net.minecraft.world.level.material.Fluid getHoneyFluid() {
            // 尝试获取Create的蜂蜜流体，如果没有则使用水
            try {
                // 检查是否有Create的蜂蜜流体
                net.minecraft.resources.ResourceLocation honeyLocation = new net.minecraft.resources.ResourceLocation("create", "honey");
                net.minecraft.world.level.material.Fluid honeyFluid = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(honeyLocation);
                if (honeyFluid != null && honeyFluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                    return honeyFluid;
                }
            } catch (Exception ignored) {
                // 如果获取失败，使用水作为后备
            }

            // 后备选项：使用水代表蜂蜜
            return net.minecraft.world.level.material.Fluids.WATER;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Nonnull
        @Override
        public FluidStack getFluidInTank(int tank) {
            // 如果蜂蜜等级为满，返回蜂蜜，否则返回空
            if (isHoneyFull()) {
                return new FluidStack(getHoneyFluid(), 250); // 250mB蜂蜜，相当于一瓶蜂蜜
            }
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return 250; // 一次提供250mB蜂蜜
        }

        @Override
        public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
            return false; // 蜂巢不接受流体输入
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0; // 不支持填充
        }

        @Nonnull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            // 只允许抽取蜂蜜
            if (!resource.isEmpty() && resource.getFluid() == getHoneyFluid()) {
                return drain(resource.getAmount(), action);
            }
            return FluidStack.EMPTY;
        }

        @Nonnull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (!isHoneyFull()) {
                return FluidStack.EMPTY;
            }

            int drainAmount = Math.min(maxDrain, 250);
            FluidStack result = new FluidStack(getHoneyFluid(), drainAmount);

            if (action.execute()) {
                // 重置蜂蜜等级
                try {
                    net.minecraft.world.level.block.BeehiveBlock beehiveBlock = (net.minecraft.world.level.block.BeehiveBlock) state.getBlock();
                    beehiveBlock.resetHoneyLevel(level, state, pos);
                } catch (Exception e) {
                    // 如果重置失败，尝试直接设置方块状态
                    level.setBlock(pos, state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_HONEY, 0), 3);
                }
            }

            return result;
        }

        /**
         * 检查蜂蜜是否满级
         */
        private boolean isHoneyFull() {
            try {
                BlockState currentState = level.getBlockState(pos);
                if (currentState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_HONEY)) {
                    return currentState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_HONEY) >= 5;
                }
            } catch (Exception e) {
                return false;
            }
            return false;
        }
    }
}