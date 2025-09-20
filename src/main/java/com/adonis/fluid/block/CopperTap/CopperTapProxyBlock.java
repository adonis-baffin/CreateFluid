package com.adonis.fluid.block.CopperTap;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CopperTapProxyBlock extends Block implements IBE<CopperTapProxyBlockEntity> {

    public CopperTapProxyBlock(Properties properties) {
        super(properties.noCollission().noOcclusion().noLootTable());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public Class<CopperTapProxyBlockEntity> getBlockEntityClass() {
        return CopperTapProxyBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CopperTapProxyBlockEntity> getBlockEntityType() {
        return com.adonis.fluid.registry.CFBlockEntity.COPPER_TAP_PROXY.get();
    }
}