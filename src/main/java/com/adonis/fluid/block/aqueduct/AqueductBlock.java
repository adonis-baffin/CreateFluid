package com.adonis.fluid.block.aqueduct;

import com.adonis.fluid.registry.CFBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public class AqueductBlock extends AbstractAqueductBlock {

    public AqueductBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<AbstractAqueductBlockEntity> getBlockEntityClass() {
        // 返回父类期望的类型，但实际创建的是AqueductBlockEntity
        return (Class<AbstractAqueductBlockEntity>) (Class<?>) AqueductBlockEntity.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public BlockEntityType<? extends AbstractAqueductBlockEntity> getBlockEntityType() {
        // 返回实际的BlockEntityType，但转换为父类期望的类型
        return (BlockEntityType<? extends AbstractAqueductBlockEntity>) CFBlockEntity.AQUEDUCT.get();
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);

        if (!level.isClientSide) {
            // 检查水源方块
            checkForWaterSource(level, pos, state);
        }
    }

    private void checkForWaterSource(Level level, BlockPos pos, BlockState state) {
        Direction inputDir = state.getValue(FACING).getOpposite();
        BlockPos sourcePos = pos.relative(inputDir);

        if (level.getBlockState(sourcePos).getBlock() == Fluids.WATER.defaultFluidState().createLegacyBlock().getBlock()) {
            withBlockEntityDo(level, pos, be -> {
                if (be instanceof AqueductBlockEntity) {
                    ((AqueductBlockEntity) be).setHasWaterSource(true);
                }
            });
        }
    }
}