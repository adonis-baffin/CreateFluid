package com.adonis.fluid.block.GutterOutlet;

import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

public class GutterOutletMountedStorageType extends MountedFluidStorageType<GutterOutletMountedStorage> {

    public GutterOutletMountedStorageType() {
        super(GutterOutletMountedStorage.CODEC);
    }

    @Override
    @Nullable
    public GutterOutletMountedStorage mount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        if (be instanceof GutterOutletBlockEntity gutter) {
            return GutterOutletMountedStorage.fromGutter(gutter);
        }
        return null;
    }
}
