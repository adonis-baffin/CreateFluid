package com.adonis.fluid.content.pipette;

import com.adonis.fluid.registry.CFBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class FluidInteractionPoint {
    protected BlockPos pos;
    protected Direction face;
    protected Mode mode;
    protected Level level;
    protected long lastKnownValid = -1;

    // Update constructor to handle belt default mode
    public FluidInteractionPoint(Level level, BlockPos pos, BlockState state) {
        this.level = level;
        this.pos = pos;
        this.face = Direction.UP;

        // Set default modes based on block type
        if (isBlazeBurner(state)) {
            this.mode = Mode.DEPOSIT;
        } else if (isBeehive(state)) {
            this.mode = Mode.TAKE;
        } else if (AllBlocks.BELT.has(state)) {
            // Belts can only be outputs (deposit mode)
            this.mode = Mode.DEPOSIT;
        } else if (isCauldron(state)) {
            this.mode = Mode.DEPOSIT;
        } else {
            this.mode = Mode.DEPOSIT;
        }
    }

    @Nullable
    public static FluidInteractionPoint create(Level level, BlockPos pos, BlockState state) {
        // Priority check for depot
        if (AllBlocks.DEPOT.has(state) || AllBlocks.WEIGHTED_EJECTOR.has(state)) {
            return new DepotFluidInteractionPoint(level, pos, state);
        }

        // 检查分液池 - 添加这部分
        if (AllBlocks.ITEM_DRAIN.has(state)) {
            return new ItemDrainFluidInteractionPoint(level, pos, state);
        }

        // Belt support - create interaction point for selection only
        // Actual processing handled by VirtualRelayManager
        if (AllBlocks.BELT.has(state)) {
            // Only create interaction point for belts that can transport objects
            if (com.simibubi.create.content.kinetics.belt.BeltBlock.canTransportObjects(state)) {
                return new FluidInteractionPoint(level, pos, state) {
                    @Override
                    public boolean canExtract() {
                        return false; // Belts cannot be fluid sources
                    }

                    @Override
                    public boolean canInsert(FluidStack stack) {
                        // Always show as valid output for selection
                        return this.mode == Mode.DEPOSIT;
                    }

                    @Override
                    public FluidStack extract(int maxAmount, boolean simulate) {
                        return FluidStack.EMPTY;
                    }

                    @Override
                    public FluidStack insert(FluidStack stack, boolean simulate) {
                        // Return original stack (no direct insertion)
                        return stack;
                    }

                    @Override
                    public void cycleMode() {
                        // Belts can only be outputs
                        this.mode = Mode.DEPOSIT;
                    }
                };
            }
            return null;
        }

        // Cauldron support
        if (isCauldron(state)) {
            return new FluidInteractionPoint(level, pos, state);
        }

        // Other valid blocks
        if (isValidFluidBlock(state)) {
            return new FluidInteractionPoint(level, pos, state);
        }

        return null;
    }

    private static boolean isValidFluidBlock(BlockState state) {
        // Belt support
        if (AllBlocks.BELT.has(state)) {
            return com.simibubi.create.content.kinetics.belt.BeltBlock.canTransportObjects(state);
        }

        if (AllBlocks.DEPOT.has(state) || AllBlocks.WEIGHTED_EJECTOR.has(state)) {
            return true;
        }

        if (AllBlocks.ITEM_DRAIN.has(state)) {
            return true;
        }

        if (AllBlocks.BASIN.has(state) ||
                CFBlocks.FLUID_INTERFACE.has(state) ||
                CFBlocks.SMART_FLUID_INTERFACE.has(state)) {
            return true;
        }

        if (isBlazeBurner(state) || isBeehive(state) || isCauldron(state)) {
            return true;
        }

        return false;
    }

    private static boolean isBlazeBurner(BlockState state) {
        return AllBlocks.BLAZE_BURNER.has(state) || AllBlocks.LIT_BLAZE_BURNER.has(state);
    }

    private static boolean isBeehive(BlockState state) {
        return state.getBlock() instanceof net.minecraft.world.level.block.BeehiveBlock;
    }

    public static boolean isCauldron(BlockState state) {
        return state.is(Blocks.CAULDRON) ||
                state.is(Blocks.WATER_CAULDRON) ||
                state.is(Blocks.LAVA_CAULDRON) ||
                state.is(Blocks.POWDER_SNOW_CAULDRON);
    }

    public boolean isValid() {
        if (level == null) return false;

        long gameTime = level.getGameTime();
        if (gameTime == lastKnownValid) return true;

        BlockState state = level.getBlockState(pos);

        // Belts are always valid if they can transport
        if (AllBlocks.BELT.has(state)) {
            if (com.simibubi.create.content.kinetics.belt.BeltBlock.canTransportObjects(state)) {
                lastKnownValid = gameTime;
                return true;
            }
            return false;
        }

        // 炼药锅特殊处理
        if (isCauldron(state)) {
            lastKnownValid = gameTime;
            return true;
        }

        // 检查流体处理器
        BlockEntity be = level.getBlockEntity(pos);
        boolean valid = false;

        if (be != null) {
            valid = isValidFluidBlock(state) && getFluidHandler() != null;
        } else {
            valid = isValidFluidBlock(state);
        }

        if (valid) {
            lastKnownValid = gameTime;
        }

        return valid;
    }

    @Nullable
    private IFluidHandler getFluidHandler() {
        BlockState state = level.getBlockState(pos);

        // 炼药锅使用自定义处理器
        if (isCauldron(state)) {
            return new CauldronFluidHandler(level, pos, state);
        }

        BlockEntity be = level.getBlockEntity(pos);

        // 烈焰人燃烧室
        if (isBlazeBurner(state)) {
            return new BlazeBurnerFluidHandler(level, pos, state);
        }

        // 蜂巢
        if (isBeehive(state)) {
            return new BeehiveFluidHandler(level, pos, state);
        }

        // 标准流体处理
        if (be == null) return null;

        return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
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
        BlockState state = level.getBlockState(pos);

        if (state.is(Blocks.CAULDRON)) {
            return false;
        }

        if (state.is(Blocks.WATER_CAULDRON)) {
            return state.getValue(LayeredCauldronBlock.LEVEL) == 3;
        }

        if (state.is(Blocks.LAVA_CAULDRON)) {
            return true;
        }

        if (state.is(Blocks.POWDER_SNOW_CAULDRON)) {
            return state.getValue(LayeredCauldronBlock.LEVEL) == 3;
        }

        IFluidHandler handler = getFluidHandler();
        if (handler == null) return false;

        return !handler.drain(1, IFluidHandler.FluidAction.SIMULATE).isEmpty();
    }

    public boolean canInsert(FluidStack stack) {
        BlockState state = level.getBlockState(pos);

        // 炼药锅特殊处理
        if (isCauldron(state)) {
            if (state.is(Blocks.CAULDRON)) {
                // 空炼药锅只接受水和岩浆
                return stack.getFluid() == Fluids.WATER ||
                        stack.getFluid() == Fluids.LAVA;
                // TODO: 添加细雪流体支持
            }

            if (state.is(Blocks.WATER_CAULDRON)) {
                int level = state.getValue(LayeredCauldronBlock.LEVEL);
                return level < 3 && stack.getFluid() == Fluids.WATER;
            }

            if (state.is(Blocks.LAVA_CAULDRON)) {
                return false; // 满的岩浆炼药锅不能再填充
            }

            // 细雪炼药锅暂时返回false
            if (state.is(Blocks.POWDER_SNOW_CAULDRON)) {
                return false; // TODO: 待细雪流体迁移后更新
            }
        }

        IFluidHandler handler = getFluidHandler();
        if (handler == null) return false;

        return handler.fill(stack, IFluidHandler.FluidAction.SIMULATE) > 0;
    }

    public void cycleMode() {
        BlockState state = level.getBlockState(pos);

        if (isBlazeBurner(state) || isBeehive(state)) {
            return;
        }

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
        BlockPos pos = net.minecraft.nbt.NbtUtils.readBlockPos(nbt, "Pos").orElse(BlockPos.ZERO).offset(armPos);
        BlockState state = level.getBlockState(pos);

        FluidInteractionPoint point = create(level, pos, state);
        if (point != null) {
            if (nbt.contains("Mode")) {
                try {
                    Mode deserializedMode = Mode.valueOf(nbt.getString("Mode"));
                    point.mode = deserializedMode;
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
        BlockPos pos = net.minecraft.nbt.NbtUtils.readBlockPos(nbt, "Pos").orElse(BlockPos.ZERO);
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
        TAKE("fluid.mechanical_pipette.extract", 8375776),
        DEPOSIT("fluid.mechanical_pipette.deposit", 14532966);

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

    // 烈焰人燃烧室流体处理器
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
            return stack.getFluid() == Fluids.LAVA;
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

    // 蜂巢流体处理器
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
                ResourceLocation honeyLocation = ResourceLocation.fromNamespaceAndPath("create", "honey");
                net.minecraft.core.Registry<net.minecraft.world.level.material.Fluid> registry =
                        level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.FLUID);
                net.minecraft.world.level.material.Fluid honeyFluid = registry.get(honeyLocation);
                if (honeyFluid != null && honeyFluid != Fluids.EMPTY) {
                    return honeyFluid;
                }
            } catch (Exception ignored) {
            }
            return Fluids.WATER;
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
                    net.minecraft.world.level.block.BeehiveBlock beehiveBlock =
                            (net.minecraft.world.level.block.BeehiveBlock) state.getBlock();
                    beehiveBlock.resetHoneyLevel(level, state, pos);
                } catch (Exception e) {
                    level.setBlock(pos, state.setValue(
                            net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_HONEY, 0), 3);
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

    // 炼药锅流体处理器
    private static class CauldronFluidHandler implements IFluidHandler {
        private final Level level;
        private final BlockPos pos;
        private final BlockState state;

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
            if (state.is(Blocks.WATER_CAULDRON)) {
                int level = state.getValue(LayeredCauldronBlock.LEVEL);
                return new FluidStack(Fluids.WATER, level * 333);
            } else if (state.is(Blocks.LAVA_CAULDRON)) {
                return new FluidStack(Fluids.LAVA, 1000);
            }
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return 1000;
        }

        @Override
        public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
            return stack.getFluid() == Fluids.WATER || stack.getFluid() == Fluids.LAVA;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (state.is(Blocks.CAULDRON)) {
                if (resource.getFluid() == Fluids.WATER && resource.getAmount() >= 250) {
                    if (action.execute()) {
                        level.setBlock(pos, Blocks.WATER_CAULDRON.defaultBlockState()
                                .setValue(LayeredCauldronBlock.LEVEL, 1), 3);
                    }
                    return 250;
                } else if (resource.getFluid() == Fluids.LAVA && resource.getAmount() >= 1000) {
                    if (action.execute()) {
                        level.setBlock(pos, Blocks.LAVA_CAULDRON.defaultBlockState(), 3);
                    }
                    return 1000;
                }
            } else if (state.is(Blocks.WATER_CAULDRON)) {
                int currentLevel = state.getValue(LayeredCauldronBlock.LEVEL);
                if (currentLevel < 3 && resource.getFluid() == Fluids.WATER && resource.getAmount() >= 250) {
                    if (action.execute()) {
                        level.setBlock(pos, state.setValue(LayeredCauldronBlock.LEVEL, currentLevel + 1), 3);
                    }
                    return 250;
                }
            }
            return 0;
        }

        @Nonnull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            FluidStack inTank = getFluidInTank(0);
            if (inTank.isEmpty() || !inTank.getFluid().equals(resource.getFluid())) {
                return FluidStack.EMPTY;
            }
            return drain(resource.getAmount(), action);
        }

        @Nonnull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (state.is(Blocks.WATER_CAULDRON)) {
                int level = state.getValue(LayeredCauldronBlock.LEVEL);
                if (level == 3 && maxDrain >= 1000) {
                    if (action.execute()) {
                        this.level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                    }
                    return new FluidStack(Fluids.WATER, 1000);
                }
            } else if (state.is(Blocks.LAVA_CAULDRON)) {
                if (maxDrain >= 1000) {
                    if (action.execute()) {
                        level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                    }
                    return new FluidStack(Fluids.LAVA, 1000);
                }
            }
            return FluidStack.EMPTY;
        }
    }
}