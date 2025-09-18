// java/com/adonis/fluid/fluid/powdersnow/PowderSnowBucketHandler.java
package com.adonis.fluid.fluid.powdersnow;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.registry.CFFluid;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

@Mod.EventBusSubscriber(modid = CreateFluid.MODID)
public class PowderSnowBucketHandler {

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<ItemStack> event) {
        ItemStack stack = event.getObject();
        if (stack.getItem() == Items.POWDER_SNOW_BUCKET || stack.getItem() == Items.BUCKET) {
            event.addCapability(
                    new ResourceLocation(CreateFluid.MODID, "powder_snow_handler"),
                    new Provider(stack)
            );
        }
    }

    static class Provider implements ICapabilityProvider {
        private final LazyOptional<IFluidHandlerItem> holder;

        Provider(ItemStack stack) {
            this.holder = LazyOptional.of(() -> new Handler(stack));
        }

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, Direction side) {
            if (cap == ForgeCapabilities.FLUID_HANDLER_ITEM) {
                return holder.cast();
            }
            return LazyOptional.empty();
        }
    }

    static class Handler implements IFluidHandlerItem {
        private ItemStack container;

        Handler(ItemStack container) {
            this.container = container;
        }

        @Override
        public @NotNull ItemStack getContainer() {
            return container;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            if (container.getItem() == Items.POWDER_SNOW_BUCKET) {
                return new FluidStack(CFFluid.POWDER_SNOW.get(), 1000);
            }
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return 1000;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return stack.getFluid() == CFFluid.POWDER_SNOW.get();
        }

        @Override
        public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
            if (container.getItem() != Items.BUCKET || container.getCount() != 1) {
                return 0;
            }

            if (resource.getFluid() != CFFluid.POWDER_SNOW.get() || resource.getAmount() < 1000) {
                return 0;
            }

            if (action.execute()) {
                container = new ItemStack(Items.POWDER_SNOW_BUCKET);
            }
            return 1000;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action) {
            return drain(resource.getAmount(), action);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
            if (container.getItem() != Items.POWDER_SNOW_BUCKET || maxDrain < 1000) {
                return FluidStack.EMPTY;
            }

            FluidStack result = new FluidStack(CFFluid.POWDER_SNOW.get(), 1000);
            if (action.execute()) {
                container = new ItemStack(Items.BUCKET);
            }
            return result;
        }
    }
}