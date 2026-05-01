package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.block.CopperSink.CopperSinkMountedStorageType;
import com.adonis.fluid.block.FluidAtomizer.FluidAtomizerMountedStorageType;
import com.adonis.fluid.block.GutterOutlet.GutterOutletMountedStorageType;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;
import com.tterrag.registrate.util.entry.RegistryEntry;

import java.util.function.Supplier;

public class CFMountedStorageTypes {

    public static final RegistryEntry<MountedFluidStorageType<?>, CopperSinkMountedStorageType> COPPER_SINK =
            simpleFluid("copper_sink", CopperSinkMountedStorageType::new);

    public static final RegistryEntry<MountedFluidStorageType<?>, GutterOutletMountedStorageType> GUTTER_OUTLET =
            simpleFluid("gutter_outlet", GutterOutletMountedStorageType::new);

    public static final RegistryEntry<MountedFluidStorageType<?>, FluidAtomizerMountedStorageType> FLUID_ATOMIZER =
            simpleFluid("fluid_atomizer", FluidAtomizerMountedStorageType::new);

    private static <T extends MountedFluidStorageType<?>> RegistryEntry<MountedFluidStorageType<?>, T> simpleFluid(String name, Supplier<T> supplier) {
        return CreateFluid.REGISTRATE.mountedFluidStorage(name, supplier).register();
    }

    public static void register() {
        // Forces class loading to register the mounted storage types
    }
}
