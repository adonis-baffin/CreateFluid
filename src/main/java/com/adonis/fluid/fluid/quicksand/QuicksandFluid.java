package com.adonis.fluid.fluid.quicksand;

import com.adonis.fluid.registry.CFFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

import java.util.function.Supplier;

/**
 * 流沙流体 - 完全参照细雪流体设计
 */
public abstract class QuicksandFluid extends BaseFlowingFluid {

    // 使用holder避免循环引用 - 延迟解析
    public static final Properties PROPERTIES = new Properties(
            () -> CFFluids.QUICKSAND_FLUID_TYPE.get(),
            () -> CFFluids.QUICKSAND_SOURCE.get(),
            () -> CFFluids.QUICKSAND_FLOWING.get()
    );

    private static Supplier<Item> bucketSupplier;

    public static void setBucketSupplier(Supplier<Item> supplier) {
        bucketSupplier = supplier;
    }

    protected QuicksandFluid(Properties properties) {
        super(properties);
    }

    @Override
    public Item getBucket() {
        return bucketSupplier != null ? bucketSupplier.get() : Items.BUCKET;
    }

    @Override
    public Vec3 getFlow(BlockGetter blockReader, BlockPos pos, FluidState fluidState) {
        return Vec3.ZERO;
    }

    @Override
    protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluid, Direction direction) {
        return false;
    }

    @Override
    protected BlockState createLegacyBlock(FluidState state) {
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public boolean isSource(FluidState state) {
        return false;
    }

    @Override
    public int getAmount(FluidState state) {
        return 0;
    }

    @Override
    public VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    public static class Flowing extends QuicksandFluid {
        public Flowing(Properties properties) {
            super(properties);
            registerDefaultState(getStateDefinition().any().setValue(LEVEL, 7));
        }

        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }

    public static class Source extends QuicksandFluid {
        public Source(Properties properties) {
            super(properties);
        }

        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }
}
