package com.adonis.fluid.fluid.powdersnow;

import com.adonis.fluid.mixin.accessor.ItemDrainBlockEntityAccessor;
import com.adonis.fluid.registry.CFFluid;
import com.simibubi.create.content.fluids.drain.ItemDrainBlock;
import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.fluid.FluidHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.Mod;

/**
 * 处理细雪桶与流体系统的交互
 */
@Mod.EventBusSubscriber(modid = "fluid")
public class PowderSnowBucketHandler {

    /**
     * 处理细雪桶与分液池的交互
     * 优先级设为HIGHEST确保在Create之前处理
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemDrainInteract(PlayerInteractEvent.RightClickBlock event) {
        ItemStack heldItem = event.getItemStack();
        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        InteractionHand hand = event.getHand();

        // 检查是否持有细雪桶
        if (!heldItem.is(Items.POWDER_SNOW_BUCKET)) {
            return;
        }

        // 检查是否对着分液池
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ItemDrainBlock)) {
            return;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ItemDrainBlockEntity drainBE)) {
            return;
        }

        // 如果是SmartBlockEntity，使用FluidHelper
        if (be instanceof SmartBlockEntity smartBE) {
            boolean success = FluidHelper.tryEmptyItemIntoBE(level, player, hand, heldItem, smartBE);
            if (success) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
        }

        // 备用方法：使用Accessor访问internalTank
        if (drainBE instanceof ItemDrainBlockEntityAccessor accessor) {
            SmartFluidTankBehaviour internalTank = accessor.getInternalTank();

            if (internalTank != null) {
                // 允许插入
                internalTank.allowInsertion();

                try {
                    // 尝试填充
                    FluidStack powderSnow = CFFluid.getPowderSnowFluidStack(1000);
                    int filled = internalTank.getPrimaryHandler().fill(powderSnow, IFluidHandler.FluidAction.SIMULATE);

                    if (filled == 1000) {
                        // 实际填充
                        internalTank.getPrimaryHandler().fill(powderSnow, IFluidHandler.FluidAction.EXECUTE);

                        // 消耗细雪桶
                        if (!player.isCreative()) {
                            heldItem.shrink(1);
                            ItemStack bucket = new ItemStack(Items.BUCKET);
                            if (!player.getInventory().add(bucket)) {
                                player.drop(bucket, false);
                            }
                        }

                        event.setCanceled(true);
                        event.setCancellationResult(InteractionResult.SUCCESS);
                    }
                } finally {
                    // 确保禁止插入
                    internalTank.forbidInsertion();
                }
            }
        }

        // 如果以上都失败，尝试通过capability
        if (!event.isCanceled()) {
            be.getCapability(ForgeCapabilities.FLUID_HANDLER).ifPresent(fluidHandler -> {
                FluidStack powderSnow = CFFluid.getPowderSnowFluidStack(1000);
                int filled = fluidHandler.fill(powderSnow, IFluidHandler.FluidAction.SIMULATE);

                if (filled == 1000) {
                    // 实际填充
                    fluidHandler.fill(powderSnow, IFluidHandler.FluidAction.EXECUTE);

                    // 消耗细雪桶
                    if (!player.isCreative()) {
                        heldItem.shrink(1);
                        ItemStack bucket = new ItemStack(Items.BUCKET);
                        if (!player.getInventory().add(bucket)) {
                            player.drop(bucket, false);
                        }
                    }

                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                }
            });
        }
    }

    /**
     * 处理流体储罐和注液器的交互 - 完全禁止
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onFluidContainerInteract(PlayerInteractEvent.RightClickBlock event) {
        ItemStack heldItem = event.getItemStack();

        // 检查是否持有细雪桶
        if (!heldItem.is(Items.POWDER_SNOW_BUCKET)) {
            return;
        }

        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (be == null) {
            return;
        }

        String className = be.getClass().getSimpleName();

        // 禁止向流体储罐和注液器直接倒入（任何模式）
        if (className.contains("FluidTank") || className.contains("Spout")) {
            // 不取消事件，让原版行为（放置细雪方块）生效
            return;
        }
    }

    /**
     * 处理空桶从储罐取出 - 完全禁止
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEmptyBucketUse(PlayerInteractEvent.RightClickBlock event) {
        ItemStack heldItem = event.getItemStack();

        if (!heldItem.is(Items.BUCKET)) {
            return;
        }

        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (be == null) {
            return;
        }

        String className = be.getClass().getSimpleName();

        // 禁止从储罐直接取出
        if (className.contains("FluidTank")) {
            be.getCapability(ForgeCapabilities.FLUID_HANDLER).ifPresent(fluidHandler -> {
                FluidStack fluid = fluidHandler.getFluidInTank(0);
                if (CFFluid.isPowderSnowFluid(fluid.getFluid())) {
                    // 如果储罐中有细雪流体，不处理（应该用注液器）
                    return;
                }
            });
        }
    }
}