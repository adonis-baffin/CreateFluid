package com.adonis.fluid.block.CentrifugalPump;

import com.adonis.fluid.registry.CFBlockEntity;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CentrifugalPumpBlock extends FaceAttachedHorizontalDirectionalBlock implements IBE<CentrifugalPumpBlockEntity>, IWrenchable, ProperWaterloggedBlock {
    public CentrifugalPumpBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, WATERLOGGED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        AttachFace face = state.getValue(FACE);
        Direction facing = state.getValue(FACING);

        // Base 12x12x12 centered collision box (0.125 to 0.875 in each axis, as 12/16 = 0.75, offset by 0.125)
        VoxelShape base = Shapes.box(0.125, 0.125, 0.125, 0.875, 0.875, 0.875);

        // Protrusions: 8x8x2 pixels (0.5x0.5x0.125 in block coordinates, centered on each face)
        VoxelShape eastProtrusion = Shapes.box(0.875, 0.25, 0.25, 1.0, 0.75, 0.75); // East
        VoxelShape westProtrusion = Shapes.box(0.0, 0.25, 0.25, 0.125, 0.75, 0.75); // West
        VoxelShape topProtrusion = Shapes.box(0.25, 0.875, 0.25, 0.75, 1.0, 0.75); // Top
        VoxelShape northProtrusion = Shapes.box(0.25, 0.25, 0.0, 0.75, 0.75, 0.125); // North

        // Define shapes for each state
        if (face == AttachFace.FLOOR) {
            if (facing == Direction.NORTH) {
                return Shapes.or(base, eastProtrusion, westProtrusion, topProtrusion, northProtrusion);
            } else if (facing == Direction.EAST) {
                return Shapes.or(base, eastProtrusion, westProtrusion, topProtrusion, northProtrusion);
            } else if (facing == Direction.SOUTH) {
                return Shapes.or(base, eastProtrusion, westProtrusion, topProtrusion, northProtrusion);
            } else if (facing == Direction.WEST) {
                return Shapes.or(base, eastProtrusion, westProtrusion, topProtrusion, northProtrusion);
            }
        } else if (face == AttachFace.CEILING) {
            VoxelShape topRotated = Shapes.box(0.25, 0.0, 0.25, 0.75, 0.125, 0.75); // Top rotated to Bottom
            if (facing == Direction.NORTH) {
                return Shapes.or(base, eastProtrusion, westProtrusion, topRotated, northProtrusion);
            } else if (facing == Direction.EAST) {
                return Shapes.or(base, eastProtrusion, westProtrusion, topRotated, northProtrusion);
            } else if (facing == Direction.SOUTH) {
                return Shapes.or(base, eastProtrusion, westProtrusion, topRotated, northProtrusion);
            } else if (facing == Direction.WEST) {
                return Shapes.or(base, eastProtrusion, westProtrusion, topRotated, northProtrusion);
            }
        } else if (face == AttachFace.WALL) {
            if (facing == Direction.NORTH) {
                VoxelShape eastRotated = Shapes.box(0.25, 0.25, 0.875, 0.75, 0.75, 1.0); // East rotated to South
                VoxelShape westRotated = Shapes.box(0.25, 0.25, 0.0, 0.75, 0.75, 0.125); // West rotated to North
                VoxelShape topRotated = Shapes.box(0.25, 0.875, 0.25, 0.75, 1.0, 0.75); // Top stays Top
                VoxelShape northRotated = Shapes.box(0.875, 0.25, 0.25, 1.0, 0.75, 0.75); // North rotated to East
                return Shapes.or(base, eastRotated, westRotated, topRotated, northRotated);
            } else if (facing == Direction.EAST) {
                VoxelShape eastRotated = Shapes.box(0.25, 0.25, 0.0, 0.75, 0.75, 0.125); // East rotated to North
                VoxelShape westRotated = Shapes.box(0.25, 0.25, 0.875, 0.75, 0.75, 1.0); // West rotated to South
                VoxelShape topRotated = Shapes.box(0.25, 0.875, 0.25, 0.75, 1.0, 0.75); // Top stays Top
                VoxelShape northRotated = Shapes.box(0.0, 0.25, 0.25, 0.125, 0.75, 0.75); // North rotated to West
                return Shapes.or(base, eastRotated, westRotated, topRotated, northRotated);
            } else if (facing == Direction.SOUTH) {
                VoxelShape eastRotated = Shapes.box(0.25, 0.25, 0.0, 0.75, 0.75, 0.125); // East rotated to North
                VoxelShape westRotated = Shapes.box(0.25, 0.25, 0.875, 0.75, 0.75, 1.0); // West rotated to South
                VoxelShape topRotated = Shapes.box(0.25, 0.875, 0.25, 0.75, 1.0, 0.75); // Top stays Top
                VoxelShape northRotated = Shapes.box(0.0, 0.25, 0.25, 0.125, 0.75, 0.75); // North rotated to West
                return Shapes.or(base, eastRotated, westRotated, topRotated, northRotated);
            } else if (facing == Direction.WEST) {
                VoxelShape eastRotated = Shapes.box(0.25, 0.25, 0.875, 0.75, 0.75, 1.0); // East rotated to South
                VoxelShape westRotated = Shapes.box(0.25, 0.25, 0.0, 0.75, 0.75, 0.125); // West rotated to North
                VoxelShape topRotated = Shapes.box(0.25, 0.875, 0.25, 0.75, 1.0, 0.75); // Top stays Top
                VoxelShape northRotated = Shapes.box(0.875, 0.25, 0.25, 1.0, 0.75, 0.75); // North rotated to East
                return Shapes.or(base, eastRotated, westRotated, topRotated, northRotated);
            }
        }

        // Default fallback
        return Shapes.or(base, eastProtrusion, westProtrusion, topProtrusion, northProtrusion);
    }

    @Override
    public Class<CentrifugalPumpBlockEntity> getBlockEntityClass() {
        return CentrifugalPumpBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CentrifugalPumpBlockEntity> getBlockEntityType() {
        return CFBlockEntity.CENTRIFUGAL_PUMP.get();
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return fluidState(state);
    }
}