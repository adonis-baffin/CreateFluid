package com.adonis.fluid.content.pipette;

import com.adonis.fluid.registry.CFBlock;
import com.simibubi.create.AllBlocks;
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

public class FluidInteractionPoint {
    protected BlockPos pos;
    protected Direction face;
    protected Mode mode;
    protected Level level;
    protected long lastKnownValid = -1;

    public FluidInteractionPoint(Level level, BlockPos pos, BlockState state) {
        this.level = level;
        this.pos = pos;
        this.face = Direction.UP;

        // 根据方块类型设置默认模式
        if (isBlazeBurner(state)) {
            this.mode = Mode.DEPOSIT;
        } else if (isBeehive(state)) {
            this.mode = Mode.TAKE;
        } else if (AllBlocks.BELT.has(state)) {
            // 传送带默认作为输出端（接收流体）
            this.mode = Mode.DEPOSIT;
        } else if (AllBlocks.ITEM_DRAIN.has(state)) {
            // 分液池只能作为输入端
            this.mode = Mode.TAKE;
        } else {
            this.mode = Mode.DEPOSIT;
        }
    }

    @Nullable
    public static FluidInteractionPoint create(Level level, BlockPos pos, BlockState state) {
        // 优先检查是否为置物台
        if (AllBlocks.DEPOT.has(state)) {
            return new DepotFluidInteractionPoint(level, pos, state);
        }

        // 检查是否为分液池
        if (AllBlocks.ITEM_DRAIN.has(state)) {
            return new ItemDrainFluidInteractionPoint(level, pos, state);
        }

        // 传送带创建普通的交互点（用于选择），实际处理由虚拟中继器完成
        if (AllBlocks.BELT.has(state)) {
            // 只有能传输物品的传送带才创建交互点
            if (com.simibubi.create.content.kinetics.belt.BeltBlock.canTransportObjects(state)) {
                return new FluidInteractionPoint(level, pos, state);
            }
            return null;
        }

        // 检查其他有效的流体方块
        if (isValidFluidBlock(state)) {
            return new FluidInteractionPoint(level, pos, state);
        }

        return null;
    }

    private static boolean isValidFluidBlock(BlockState state) {
        // 支持传送带
        if (AllBlocks.BELT.has(state)) {
            return true;
        }

        // 支持置物台
        if (AllBlocks.DEPOT.has(state)) {
            return true;
        }

        // 支持分液池
        if (AllBlocks.ITEM_DRAIN.has(state)) {
            return true;
        }

        // 支持工作盆、流体接口等
        if (AllBlocks.BASIN.has(state) ||
                CFBlock.FLUID_INTERFACE.has(state) ||
                CFBlock.SMART_FLUID_INTERFACE.has(state)) {
            return true;
        }

        // 支持烈焰人燃烧室
        if (isBlazeBurner(state)) {
            return true;
        }

        // 支持蜂巢/蜂箱
        if (isBeehive(state)) {
            return true;
        }

        return false;
    }

    private static boolean isBlazeBurner(BlockState state) {
        return AllBlocks.BLAZE_BURNER.has(state) ||
                AllBlocks.LIT_BLAZE_BURNER.has(state);
    }

    private static boolean isBeehive(BlockState state) {
        return state.getBlock() instanceof net.minecraft.world.level.block.BeehiveBlock;
    }

    public boolean isValid() {
        if (level == null) return false;

        long gameTime = level.getGameTime();
        if (gameTime == lastKnownValid) return true;

        BlockState state = level.getBlockState(pos);

        // 传送带的特殊处理 - 始终有效（由虚拟中继器处理实际功能）
        if (AllBlocks.BELT.has(state)) {
            if (com.simibubi.create.content.kinetics.belt.BeltBlock.canTransportObjects(state)) {
                lastKnownValid = gameTime;
                return true;
            }
            return false;
        }

        // 分液池的特殊处理 - 只要存在就有效
        if (AllBlocks.ITEM_DRAIN.has(state)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity) {
                lastKnownValid = gameTime;
                return true;
            }
            return false;
        }

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
        BlockState state = level.getBlockState(pos);

        // 传送带不提供流体处理器（由虚拟中继器处理）
        if (AllBlocks.BELT.has(state)) {
            return null;
        }

        BlockEntity be = level.getBlockEntity(pos);

        // 特殊处理：烈焰人燃烧室
        if (isBlazeBurner(state)) {
            return new BlazeBurnerFluidHandler(level, pos, state);
        }

        // 特殊处理：蜂巢/蜂箱
        if (isBeehive(state)) {
            return new BeehiveFluidHandler(level, pos, state);
        }

        // 分液池特殊处理
        if (AllBlocks.ITEM_DRAIN.has(state)) {
            if (be == null) return null;
            // 分液池必须使用Direction.DOWN获取流体处理器
            return be.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.DOWN).orElse(null);
        }

        // 标准流体处理
        if (be == null) return null;

        return be.getCapability(ForgeCapabilities.FLUID_HANDLER, face).orElse(null);
    }

    public FluidStack extract(int maxAmount, boolean simulate) {
        // 传送带不能抽取流体
        if (AllBlocks.BELT.has(level.getBlockState(pos))) {
            return FluidStack.EMPTY;
        }

        IFluidHandler handler = getFluidHandler();
        if (handler == null) return FluidStack.EMPTY;

        return handler.drain(maxAmount, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
    }

    public FluidStack insert(FluidStack stack, boolean simulate) {
        // 传送带的注液由虚拟中继器处理
        if (AllBlocks.BELT.has(level.getBlockState(pos))) {
            // 返回原流体表示无法直接插入（需要通过虚拟中继器）
            return stack;
        }

        // 分液池不接受流体输入
        if (AllBlocks.ITEM_DRAIN.has(level.getBlockState(pos))) {
            return stack;
        }

        IFluidHandler handler = getFluidHandler();
        if (handler == null) return stack;

        int filled = handler.fill(stack, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        FluidStack remainder = stack.copy();
        remainder.shrink(filled);
        return remainder;
    }

    public boolean canExtract() {
        // 传送带不能抽取
        if (AllBlocks.BELT.has(level.getBlockState(pos))) {
            return false;
        }

        IFluidHandler handler = getFluidHandler();
        if (handler == null) return false;

        return !handler.drain(1, IFluidHandler.FluidAction.SIMULATE).isEmpty();
    }

    public boolean canInsert(FluidStack stack) {
        // 传送带始终显示可以接收（实际由虚拟中继器判断）
        if (AllBlocks.BELT.has(level.getBlockState(pos))) {
            return this.mode == Mode.DEPOSIT;
        }

        // 分液池不接受流体输入
        if (AllBlocks.ITEM_DRAIN.has(level.getBlockState(pos))) {
            return false;
        }

        IFluidHandler handler = getFluidHandler();
        if (handler == null) return false;

        return handler.fill(stack, IFluidHandler.FluidAction.SIMULATE) > 0;
    }

    public void cycleMode() {
        BlockState state = level.getBlockState(pos);

        // 烈焰人燃烧室只能作为输出端
        if (isBlazeBurner(state)) {
            return;
        }

        // 蜂巢只能作为输入端
        if (isBeehive(state)) {
            return;
        }

        // 分液池只能作为输入端
        if (AllBlocks.ITEM_DRAIN.has(state)) {
            return;
        }

        // 传送带只能作为输出端（接收流体）
        if (AllBlocks.BELT.has(state)) {
            if (this.mode != Mode.DEPOSIT) {
                this.mode = Mode.DEPOSIT;
            }
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
                    Mode deserializedMode = Mode.valueOf(nbt.getString("Mode"));
                    // 传送带强制为DEPOSIT模式
                    if (AllBlocks.BELT.has(state)) {
                        point.mode = Mode.DEPOSIT;
                    }
                    // 分液池强制为TAKE模式
                    else if (AllBlocks.ITEM_DRAIN.has(state)) {
                        point.mode = Mode.TAKE;
                    } else {
                        point.mode = deserializedMode;
                    }
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
            face = transform.mirrorFacing(face);
            face = transform.rotateFacing(face);
            nbt.putString("Face", face.name());
        }
    }

    public enum Mode {
        TAKE("fluid.mechanical_pipette.extract", 8375776),  // 蓝色（输入）
        DEPOSIT("fluid.mechanical_pipette.deposit", 14532966); // 黄色（输出）

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

    // 内部类：烈焰人燃烧室流体处理器
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
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return 1000;
        }

        @Override
        public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
            return stack.getFluid() == net.minecraft.world.level.material.Fluids.LAVA;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!isFluidValid(0, resource)) {
                return 0;
            }

            net.minecraft.world.item.ItemStack lavaBucket = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LAVA_BUCKET);

            try {
                net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> result =
                        com.simibubi.create.content.processing.burner.BlazeBurnerBlock.tryInsert(
                                state, level, pos, lavaBucket, true, false, true);

                if (result.getResult() == net.minecraft.world.InteractionResult.SUCCESS) {
                    if (action.execute()) {
                        com.simibubi.create.content.processing.burner.BlazeBurnerBlock.tryInsert(
                                state, level, pos, lavaBucket, true, false, false);
                    }
                    return Math.min(resource.getAmount(), 1000);
                }
            } catch (Exception e) {
                return 0;
            }

            return 0;
        }

        @Nonnull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Nonnull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }

    // 内部类：蜂巢流体处理器
    private static class BeehiveFluidHandler implements IFluidHandler {
        private final Level level;
        private final BlockPos pos;
        private final BlockState state;

        public BeehiveFluidHandler(Level level, BlockPos pos, BlockState state) {
            this.level = level;
            this.pos = pos;
            this.state = state;
        }

        private net.minecraft.world.level.material.Fluid getHoneyFluid() {
            try {
                net.minecraft.resources.ResourceLocation honeyLocation = new net.minecraft.resources.ResourceLocation("create", "honey");
                net.minecraft.world.level.material.Fluid honeyFluid = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(honeyLocation);
                if (honeyFluid != null && honeyFluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                    return honeyFluid;
                }
            } catch (Exception ignored) {
            }
            return net.minecraft.world.level.material.Fluids.WATER;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Nonnull
        @Override
        public FluidStack getFluidInTank(int tank) {
            if (isHoneyFull()) {
                return new FluidStack(getHoneyFluid(), 250);
            }
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return 250;
        }

        @Override
        public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Nonnull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
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
                try {
                    net.minecraft.world.level.block.BeehiveBlock beehiveBlock = (net.minecraft.world.level.block.BeehiveBlock) state.getBlock();
                    beehiveBlock.resetHoneyLevel(level, state, pos);
                } catch (Exception e) {
                    level.setBlock(pos, state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_HONEY, 0), 3);
                }
            }

            return result;
        }

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