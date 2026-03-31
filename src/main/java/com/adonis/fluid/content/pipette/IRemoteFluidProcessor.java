package com.adonis.fluid.content.pipette;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public interface IRemoteFluidProcessor {
    boolean canProcessFluidItem(ItemStack stack);
    void startFluidProcessing(ItemStack stack, VirtualRelayManager.VirtualRelay relay);
    boolean isFluidProcessingComplete();
    ItemStack getFluidProcessingResult();
    void onFluidProcessingComplete();
    int getProcessingRange();
    FluidStack getHeldFluid();
    void syncFluid(FluidStack fluid);
    void notifyProcessingStarted(BlockPos beltPos);
    void notifyProcessingCompleted(BlockPos beltPos);
    boolean requestFluidForItem(ItemStack stack, BlockPos sourcePos);
}