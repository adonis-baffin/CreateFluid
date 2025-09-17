package com.adonis.fluid.fluid.powdersnow;

import com.adonis.fluid.registry.CFFluid;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * 处理空桶装填细雪流体的事件处理器
 *
 * 交互规则：
 * - 流体储罐：仅创造模式可用桶交互
 * - 工作盆：生存和创造模式都可以
 * - 注液器：不允许桶交互
 * - 分液池：可以倒入，但不能取出
 */
@Mod.EventBusSubscriber(modid = "fluid")
public class PowderSnowBucketFiller {

    /**
     * 处理空桶与流体容器的交互 - 取出细雪流体
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEmptyBucketUseOnFluidContainer(PlayerInteractEvent.RightClickBlock event) {
        ItemStack heldItem = event.getItemStack();
        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        InteractionHand hand = event.getHand();

        // 检查是否持有空桶
        if (!heldItem.is(Items.BUCKET)) {
            return;
        }

        // 获取方块实体
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return;
        }

        BlockState state = level.getBlockState(pos);

        // 检查不同容器类型的规则
        boolean canInteract = false;

        // 流体储罐 - 仅创造模式
        if (AllBlocks.FLUID_TANK.has(state) || AllBlocks.CREATIVE_FLUID_TANK.has(state)) {
            if (player.isCreative()) {
                canInteract = true;
            } else {
                return; // 生存模式不处理
            }
        }
        // 工作盆 - 都可以
        else if (AllBlocks.BASIN.has(state)) {
            canInteract = true;
        }
        // 注液器 - 不允许
        else if (state.getBlock().getDescriptionId().contains("spout")) {
            return;
        }
        // 分液池 - 不允许取出
        else if (AllBlocks.ITEM_DRAIN.has(state)) {
            return;
        }

        if (!canInteract) {
            return;
        }

        // 对于流体储罐的特殊处理
        if (be instanceof FluidTankBlockEntity tankBE) {
            // 直接访问储罐的内部库存
            IFluidHandler tankInventory = tankBE.getTankInventory();

            // 检查是否有细雪流体
            FluidStack simulatedDrain = tankInventory.drain(1000, IFluidHandler.FluidAction.SIMULATE);

            if (!simulatedDrain.isEmpty() && CFFluid.isPowderSnowFluid(simulatedDrain.getFluid())) {
                if (simulatedDrain.getAmount() >= 1000) {
                    // 执行实际抽取
                    FluidStack drained = tankInventory.drain(1000, IFluidHandler.FluidAction.EXECUTE);

                    if (!drained.isEmpty() && drained.getAmount() == 1000) {
                        // 成功抽取，处理物品
                        handleBucketFill(player, hand, heldItem, Items.POWDER_SNOW_BUCKET);

                        // 播放音效
                        level.playSound(null, pos, SoundEvents.BUCKET_FILL,
                                SoundSource.BLOCKS, 1.0F, 1.0F);

                        // 更新储罐
                        tankBE.setChanged();
                        tankBE.sendData();

                        event.setCanceled(true);
                        event.setCancellationResult(InteractionResult.SUCCESS);
                    }
                }
            }
            return;
        }

        // 通用处理（工作盆等）
        be.getCapability(ForgeCapabilities.FLUID_HANDLER).ifPresent(fluidHandler -> {
            // 检查容器中是否有细雪流体
            FluidStack simulatedDrain = fluidHandler.drain(1000, IFluidHandler.FluidAction.SIMULATE);

            if (!simulatedDrain.isEmpty() && CFFluid.isPowderSnowFluid(simulatedDrain.getFluid())) {
                if (simulatedDrain.getAmount() >= 1000) {
                    // 执行实际抽取
                    FluidStack drained = fluidHandler.drain(1000, IFluidHandler.FluidAction.EXECUTE);

                    if (!drained.isEmpty() && drained.getAmount() == 1000) {
                        // 成功抽取，处理物品
                        handleBucketFill(player, hand, heldItem, Items.POWDER_SNOW_BUCKET);

                        // 播放音效
                        level.playSound(null, pos, SoundEvents.BUCKET_FILL,
                                SoundSource.BLOCKS, 1.0F, 1.0F);

                        // 取消原事件
                        event.setCanceled(true);
                        event.setCancellationResult(InteractionResult.SUCCESS);
                    }
                }
            }
        });
    }

    /**
     * 处理细雪桶与流体容器的交互 - 倒入细雪流体
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPowderSnowBucketUseOnFluidContainer(PlayerInteractEvent.RightClickBlock event) {
        ItemStack heldItem = event.getItemStack();
        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        InteractionHand hand = event.getHand();

        // 检查是否持有细雪桶
        if (!heldItem.is(Items.POWDER_SNOW_BUCKET)) {
            return;
        }

        // 获取方块实体
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return;
        }

        BlockState state = level.getBlockState(pos);

        // 检查不同容器类型的规则
        boolean canInteract = false;

        // 流体储罐 - 仅创造模式
        if (AllBlocks.FLUID_TANK.has(state) || AllBlocks.CREATIVE_FLUID_TANK.has(state)) {
            if (player.isCreative()) {
                canInteract = true;
            } else {
                return; // 生存模式不处理
            }
        }
        // 工作盆 - 都可以
        else if (AllBlocks.BASIN.has(state)) {
            canInteract = true;
        }
        // 注液器 - 不允许
        else if (state.getBlock().getDescriptionId().contains("spout")) {
            return;
        }
        // 分液池 - 由PowderSnowBucketHandler处理
        else if (AllBlocks.ITEM_DRAIN.has(state)) {
            return; // 已有专门处理
        }

        if (!canInteract) {
            return;
        }

        // 对于流体储罐的特殊处理
        if (be instanceof FluidTankBlockEntity tankBE) {
            // 直接访问储罐的内部库存
            IFluidHandler tankInventory = tankBE.getTankInventory();

            // 尝试填充细雪流体
            FluidStack powderSnow = CFFluid.getPowderSnowFluidStack(1000);
            int filled = tankInventory.fill(powderSnow, IFluidHandler.FluidAction.SIMULATE);

            if (filled == 1000) {
                // 执行实际填充
                tankInventory.fill(powderSnow, IFluidHandler.FluidAction.EXECUTE);

                // 处理物品
                handleBucketEmpty(player, hand, heldItem);

                // 播放音效
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY,
                        SoundSource.BLOCKS, 1.0F, 1.0F);

                // 更新储罐
                tankBE.setChanged();
                tankBE.sendData();

                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }

        // 通用处理（工作盆等）
        be.getCapability(ForgeCapabilities.FLUID_HANDLER).ifPresent(fluidHandler -> {
            // 尝试填充细雪流体
            FluidStack powderSnow = CFFluid.getPowderSnowFluidStack(1000);
            int filled = fluidHandler.fill(powderSnow, IFluidHandler.FluidAction.SIMULATE);

            if (filled == 1000) {
                // 执行实际填充
                fluidHandler.fill(powderSnow, IFluidHandler.FluidAction.EXECUTE);

                // 处理物品
                handleBucketEmpty(player, hand, heldItem);

                // 播放音效
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY,
                        SoundSource.BLOCKS, 1.0F, 1.0F);

                // 取消原事件，防止放置细雪方块
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        });
    }

    /**
     * 处理桶装满后的物品替换
     */
    private static void handleBucketFill(Player player, InteractionHand hand, ItemStack emptyBucket, net.minecraft.world.item.Item filledBucketItem) {
        if (!player.isCreative()) {
            emptyBucket.shrink(1);
            ItemStack filledBucket = new ItemStack(filledBucketItem);

            if (emptyBucket.isEmpty()) {
                player.setItemInHand(hand, filledBucket);
            } else if (!player.getInventory().add(filledBucket)) {
                player.drop(filledBucket, false);
            }
        }
    }

    /**
     * 处理桶倒空后的物品替换
     */
    private static void handleBucketEmpty(Player player, InteractionHand hand, ItemStack filledBucket) {
        if (!player.isCreative()) {
            filledBucket.shrink(1);
            ItemStack emptyBucket = new ItemStack(Items.BUCKET);

            if (filledBucket.isEmpty()) {
                player.setItemInHand(hand, emptyBucket);
            } else if (!player.getInventory().add(emptyBucket)) {
                player.drop(emptyBucket, false);
            }
        }
    }
}