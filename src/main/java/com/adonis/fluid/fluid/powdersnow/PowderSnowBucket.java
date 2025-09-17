// 完整的细雪桶集成方案 - 无需 Mixin
package com.adonis.fluid.fluid.powdersnow;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.registry.CFFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.common.Mod;

/**
 * 完整的细雪桶集成方案
 * 1. 通过 Capability 让细雪桶能被倒空
 * 2. 通过事件监听让空桶能填充细雪
 */
@Mod.EventBusSubscriber(modid = "fluid")
public class PowderSnowBucket {

    // ========== Part 1: Capability 系统 ==========

    @SubscribeEvent
    public static void onAttachItemCapabilities(AttachCapabilitiesEvent<ItemStack> event) {
        ItemStack stack = event.getObject();

        // 为细雪桶添加流体处理能力（用于倒空）
        if (stack.is(Items.POWDER_SNOW_BUCKET)) {
            event.addCapability(
                    CreateFluid.asResource("powder_snow_bucket_handler"),
                    new CapabilityProvider<>(new PowderSnowBucketHandler(stack))
            );
        }

        // 为空桶添加特殊的流体处理能力（用于填充细雪）
        if (stack.is(Items.BUCKET)) {
            event.addCapability(
                    CreateFluid.asResource("empty_bucket_powder_snow_handler"),
                    new CapabilityProvider<>(new EmptyBucketHandler(stack))
            );
        }
    }

    private static class CapabilityProvider<T extends IFluidHandlerItem> implements ICapabilityProvider {
        private final LazyOptional<IFluidHandlerItem> holder;

        public CapabilityProvider(T handler) {
            this.holder = LazyOptional.of(() -> handler);
        }

        @Override
        public <C> LazyOptional<C> getCapability(Capability<C> cap, Direction side) {
            if (cap == ForgeCapabilities.FLUID_HANDLER_ITEM) {
                return holder.cast();
            }
            return LazyOptional.empty();
        }

        public void invalidateCaps() {
            holder.invalidate();
        }
    }

    /**
     * 细雪桶的流体处理器（用于倒空）
     */
    private static class PowderSnowBucketHandler implements IFluidHandlerItem {
        private final ItemStack originalContainer;
        private boolean drained = false;

        public PowderSnowBucketHandler(ItemStack container) {
            this.originalContainer = container.copy();
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (tank != 0 || drained) {
                return FluidStack.EMPTY;
            }
            return CFFluid.getPowderSnowFluidStack(1000);
        }

        @Override
        public int getTankCapacity(int tank) {
            return 1000;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action) {
            if (!CFFluid.isPowderSnowFluid(resource.getFluid())) {
                return FluidStack.EMPTY;
            }
            return drain(resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
            if (drained || maxDrain < 1000) {
                return FluidStack.EMPTY;
            }

            if (action.execute()) {
                drained = true;
            }

            return CFFluid.getPowderSnowFluidStack(1000);
        }

        @Override
        public ItemStack getContainer() {
            if (drained) {
                return new ItemStack(Items.BUCKET);
            }
            return originalContainer.copy();
        }
    }

    /**
     * 空桶的特殊流体处理器（允许填充细雪）
     */
    private static class EmptyBucketHandler implements IFluidHandlerItem {
        private final ItemStack originalContainer;
        private boolean filled = false;

        public EmptyBucketHandler(ItemStack container) {
            this.originalContainer = container.copy();
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return 1000;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            // 关键：只接受细雪流体
            return CFFluid.isPowderSnowFluid(stack.getFluid());
        }

        @Override
        public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
            if (filled || !CFFluid.isPowderSnowFluid(resource.getFluid()) || resource.getAmount() < 1000) {
                return 0;
            }

            if (action.execute()) {
                filled = true;
            }

            return 1000;
        }

        @Override
        public FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public ItemStack getContainer() {
            if (filled) {
                return new ItemStack(Items.POWDER_SNOW_BUCKET);
            }
            return originalContainer.copy();
        }
    }

    // ========== Part 2: 事件监听系统（补充处理） ==========

    /**
     * 优先处理空桶与容器的交互
     * 在机械动力处理之前拦截，确保空桶能正确填充细雪
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEmptyBucketRightClick(PlayerInteractEvent.RightClickBlock event) {
        ItemStack heldItem = event.getItemStack();

        // 只处理空桶
        if (!heldItem.is(Items.BUCKET)) {
            return;
        }

        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);

        if (be == null) {
            return;
        }

        // 尝试从容器中抽取细雪流体
        be.getCapability(ForgeCapabilities.FLUID_HANDLER, event.getFace()).ifPresent(handler -> {
            // 检查容器中是否有细雪流体
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack fluid = handler.getFluidInTank(tank);

                if (CFFluid.isPowderSnowFluid(fluid.getFluid()) && fluid.getAmount() >= 1000) {
                    // 模拟抽取
                    FluidStack drained = handler.drain(1000, IFluidHandler.FluidAction.SIMULATE);

                    if (!drained.isEmpty() && drained.getAmount() == 1000) {
                        // 执行实际抽取
                        handler.drain(1000, IFluidHandler.FluidAction.EXECUTE);

                        // 给玩家细雪桶
                        if (!player.isCreative()) {
                            heldItem.shrink(1);
                            ItemStack snowBucket = new ItemStack(Items.POWDER_SNOW_BUCKET);

                            if (heldItem.isEmpty()) {
                                player.setItemInHand(event.getHand(), snowBucket);
                            } else if (!player.getInventory().add(snowBucket)) {
                                player.drop(snowBucket, false);
                            }
                        }

                        // 播放音效
                        level.playSound(null, pos, SoundEvents.BUCKET_FILL_POWDER_SNOW,
                                SoundSource.BLOCKS, 1.0F, 1.0F);

                        // 阻止进一步处理
                        event.setCanceled(true);
                        event.setCancellationResult(InteractionResult.SUCCESS);
                        return;
                    }
                }
            }
        });
    }

    /**
     * 处理细雪桶倒入时防止放置细雪方块
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPowderSnowBucketRightClick(PlayerInteractEvent.RightClickBlock event) {
        ItemStack heldItem = event.getItemStack();

        if (!heldItem.is(Items.POWDER_SNOW_BUCKET)) {
            return;
        }

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);

        if (be == null) {
            return;
        }

        // 尝试使用 FluidUtil 进行标准流体交互
        boolean success = FluidUtil.interactWithFluidHandler(
                event.getEntity(), event.getHand(), level, pos, event.getFace()
        );

        if (success) {
            // 阻止放置细雪方块
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}