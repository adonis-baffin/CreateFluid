package com.adonis.fluid.fluid.quicksand;

import com.adonis.fluid.registry.CFFluids;
import com.adonis.fluid.registry.CFItems;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.NotNull;

/**
 * 流沙桶处理器 - 为流沙桶附加流体能力，完全参照细雪桶设计
 */
public class QuicksandBucketHandler {

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerItem(
                Capabilities.FluidHandler.ITEM,
                (stack, context) -> new QuicksandBucketFluidHandler(stack),
                CFItems.QUICKSAND_BUCKET.get()
        );
    }

    public static class QuicksandBucketFluidHandler implements IFluidHandlerItem {

        private final ItemStack container;

        public QuicksandBucketFluidHandler(ItemStack container) {
            this.container = container.copy();
            this.container.setCount(1);
        }

        @Override
        public @NotNull ItemStack getContainer() {
            return container.copy();
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            if (tank != 0) return FluidStack.EMPTY;
            if (container.is(CFItems.QUICKSAND_BUCKET.get())) {
                return new FluidStack(CFFluids.QUICKSAND_SOURCE.get(), FluidType.BUCKET_VOLUME);
            }
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank == 0 ? FluidType.BUCKET_VOLUME : 0;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return tank == 0 && stack.is(CFFluids.QUICKSAND_SOURCE.get());
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            if (!resource.is(CFFluids.QUICKSAND_SOURCE.get())) {
                return FluidStack.EMPTY;
            }
            return drain(resource.getAmount(), action);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            if (!container.is(CFItems.QUICKSAND_BUCKET.get())) {
                return FluidStack.EMPTY;
            }
            if (maxDrain < FluidType.BUCKET_VOLUME) {
                return FluidStack.EMPTY;
            }
            return new FluidStack(CFFluids.QUICKSAND_SOURCE.get(), FluidType.BUCKET_VOLUME);
        }
    }
}
