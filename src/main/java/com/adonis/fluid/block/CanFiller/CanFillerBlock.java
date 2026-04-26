package com.adonis.fluid.block.CanFiller;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;

public class CanFillerBlock extends PackagerBlock {

    public CanFillerBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<PackagerBlockEntity> getBlockEntityClass() {
        return (Class<PackagerBlockEntity>) (Class<?>) CanFillerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PackagerBlockEntity> getBlockEntityType() {
        return com.adonis.fluid.registry.CFBlockEntities.CAN_FILLER.get();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction preferredFacing = null;
        for (Direction face : context.getNearestLookingDirections()) {
            BlockEntity be = context.getLevel()
                    .getBlockEntity(context.getClickedPos()
                            .relative(face));
            if (be instanceof PackagerBlockEntity)
                continue;
            if (be != null && be.hasLevel()) {
                boolean hasItemHandler = be.getLevel().getCapability(
                        Capabilities.ItemHandler.BLOCK, be.getBlockPos(), null) != null;
                boolean hasFluidHandler = be.getLevel().getCapability(
                        Capabilities.FluidHandler.BLOCK, be.getBlockPos(), null) != null;
                if (hasItemHandler || hasFluidHandler) {
                    preferredFacing = face.getOpposite();
                    break;
                }
            }
        }

        Player player = context.getPlayer();
        if (preferredFacing == null) {
            Direction facing = context.getNearestLookingDirection();
            preferredFacing = player != null && player.isShiftKeyDown() ? facing : facing.getOpposite();
        }

        if (player != null && !(player instanceof FakePlayer)) {
            if (AllBlocks.PORTABLE_STORAGE_INTERFACE.has(context.getLevel()
                    .getBlockState(context.getClickedPos()
                            .relative(preferredFacing.getOpposite())))) {
                CreateLang.translate("packager.no_portable_storage")
                        .sendStatus(player);
                return null;
            }
        }

        return defaultBlockState()
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()))
                .setValue(FACING, preferredFacing);
    }
}
