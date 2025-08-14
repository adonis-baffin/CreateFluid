//package com.adonis.fluid.handler;
//
//import com.adonis.fluid.item.PipetteItem;
//import com.adonis.fluid.content.pipette.FluidInteractionPoint;
//import net.minecraft.world.InteractionResult;
//import net.minecraft.world.item.ItemStack;
//import net.minecraftforge.event.entity.player.PlayerInteractEvent;
//import net.minecraftforge.eventbus.api.EventPriority;
//import net.minecraftforge.eventbus.api.SubscribeEvent;
//import net.minecraftforge.fml.common.Mod;
//
//@Mod.EventBusSubscriber(modid = "fluid", bus = Mod.EventBusSubscriber.Bus.FORGE)
//public class PipetteInteractionHandler {
//
//    @SubscribeEvent(priority = EventPriority.HIGHEST)
//    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
//        ItemStack heldItem = event.getItemStack();
//
//        // 如果手持移液器物品
//        if (heldItem.getItem() instanceof PipetteItem) {
//            // 检查目标方块是否可以作为流体交互点
//            if (FluidInteractionPoint.create(event.getLevel(), event.getPos(),
//                    event.getLevel().getBlockState(event.getPos())) != null) {
//
//                // 取消事件，阻止置物台处理
//                event.setCanceled(true);
//                event.setCancellationResult(InteractionResult.SUCCESS);
//
//                // 让 PipetteFluidInteractionPointHandler 处理选择逻辑
//                // 它的事件处理器会在之后运行
//            }
//        }
//    }
//}