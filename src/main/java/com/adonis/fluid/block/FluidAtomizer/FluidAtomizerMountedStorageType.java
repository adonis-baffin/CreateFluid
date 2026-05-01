package com.adonis.fluid.block.FluidAtomizer;

import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class FluidAtomizerMountedStorageType extends MountedFluidStorageType<FluidAtomizerMountedStorage> {

    public FluidAtomizerMountedStorageType() {
        super(FluidAtomizerMountedStorage.CODEC);
    }

    @Override
    @Nullable
    public FluidAtomizerMountedStorage mount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        if (be instanceof FluidAtomizerBlockEntity atomizer) {
            return FluidAtomizerMountedStorage.fromAtomizer(atomizer);
        }
        return null;
    }
}
