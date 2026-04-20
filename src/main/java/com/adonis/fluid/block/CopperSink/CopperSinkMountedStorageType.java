package com.adonis.fluid.block.CopperSink;

import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

public class CopperSinkMountedStorageType extends MountedFluidStorageType<CopperSinkMountedStorage> {

    public CopperSinkMountedStorageType() {
        super(CopperSinkMountedStorage.CODEC);
    }

    @Override
    @Nullable
    public CopperSinkMountedStorage mount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        if (be instanceof CopperSinkBlockEntity sink) {
            return CopperSinkMountedStorage.fromSink(sink);
        }
        return null;
    }
}
