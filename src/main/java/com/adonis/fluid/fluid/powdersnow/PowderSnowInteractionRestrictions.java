package com.adonis.fluid.fluid.powdersnow;

import com.adonis.fluid.registry.CFFluid;
import com.simibubi.create.AllBlocks;
import net.minecraft.core.BlockPos;
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
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "fluid")
public class PowderSnowInteractionRestrictions {
    
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        ItemStack heldItem = event.getItemStack();
        
        // 只处理细雪桶
        if (!heldItem.is(Items.POWDER_SNOW_BUCKET)) {
            return;
        }
        
        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        
        // 1. 注液器 - 完全禁止交互
        if (AllBlocks.SPOUT.has(state)) {
            event.setCanceled(true);
            return;
        }
        
        // 2. 流体储罐 - 生存模式禁止
        if ((AllBlocks.FLUID_TANK.has(state) || AllBlocks.CREATIVE_FLUID_TANK.has(state)) 
            && !player.isCreative()) {
            event.setCanceled(true);
            return;
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEmptyBucketUse(PlayerInteractEvent.RightClickBlock event) {
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
        
        BlockState state = level.getBlockState(pos);
        
        // 检查是否包含细雪流体
        boolean containsPowderSnow = be.getCapability(ForgeCapabilities.FLUID_HANDLER)
            .map(handler -> {
                for (int i = 0; i < handler.getTanks(); i++) {
                    if (CFFluid.isPowderSnowFluid(handler.getFluidInTank(i).getFluid())) {
                        return true;
                    }
                }
                return false;
            }).orElse(false);
        
        if (!containsPowderSnow) {
            return;
        }
        
        // 1. 注液器 - 完全禁止
        if (AllBlocks.SPOUT.has(state)) {
            event.setCanceled(true);
            return;
        }
        
        // 2. 流体储罐 - 生存模式禁止
        if ((AllBlocks.FLUID_TANK.has(state) || AllBlocks.CREATIVE_FLUID_TANK.has(state)) 
            && !player.isCreative()) {
            event.setCanceled(true);
        }
    }
}