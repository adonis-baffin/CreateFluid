package com.adonis.fluid.fluid.powdersnow;

import com.adonis.fluid.registry.CFFluids;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.NotNull;

/**
 * 细雪桶处理器 - 为原版细雪桶附加流体能力
 */
public class PowderSnowBucketHandler {

    /**
     * 注册细雪桶的能力
     */
    public static void register(RegisterCapabilitiesEvent event) {
        // 为细雪桶注册流体处理能力
        event.registerItem(
                Capabilities.FluidHandler.ITEM,
                (stack, context) -> new PowderSnowBucketFluidHandler(stack),
                Items.POWDER_SNOW_BUCKET
        );
    }

    /**
     * 细雪桶流体处理器实现
     */
    public static class PowderSnowBucketFluidHandler implements IFluidHandlerItem {

        private final ItemStack container;

        public PowderSnowBucketFluidHandler(ItemStack container) {
            this.container = container.copy();
            // 确保只处理单个物品
            this.container.setCount(1);
        }

        @Override
        public @NotNull ItemStack getContainer() {
            // 返回原始容器（细雪桶），而不是空桶
            // 这样 Create 的 GenericItemFilling 就不会为细雪桶生成注液配方
            // 因为填充后的结果与原始物品相同，表示已经满了
            return container.copy();
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            if (tank != 0) return FluidStack.EMPTY;
            if (container.is(Items.POWDER_SNOW_BUCKET)) {
                return new FluidStack(CFFluids.POWDER_SNOW.get(), FluidType.BUCKET_VOLUME);
            }
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank == 0 ? FluidType.BUCKET_VOLUME : 0;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return tank == 0 && stack.is(CFFluids.POWDER_SNOW.get());
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            // 细雪桶不能填充（已经是满的）
            return 0;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            if (!resource.is(CFFluids.POWDER_SNOW.get())) {
                return FluidStack.EMPTY;
            }
            return drain(resource.getAmount(), action);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            if (!container.is(Items.POWDER_SNOW_BUCKET)) {
                return FluidStack.EMPTY;
            }
            // 必须一次性抽取1000mB（一整桶）
            if (maxDrain < FluidType.BUCKET_VOLUME) {
                return FluidStack.EMPTY;
            }
            
            // 返回细雪流体，但不直接修改原容器
            // 容器的修改由 getContainer() 返回的空桶处理
            return new FluidStack(CFFluids.POWDER_SNOW.get(), FluidType.BUCKET_VOLUME);
        }
    }
}
