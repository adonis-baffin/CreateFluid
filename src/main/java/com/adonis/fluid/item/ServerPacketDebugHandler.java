package com.adonis.fluid.item;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmPlacementPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;

import java.lang.reflect.Field;
import java.util.List;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerPacketDebugHandler {
    
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        // 这里可以添加服务器端的定期检查
    }
    
    // 使用反射来监听包处理
    public static void debugArmPacket(NetworkEvent.Context ctx, ArmPlacementPacket packet) {
        System.out.println("[SERVER DEBUG] Received ArmPlacementPacket");
        
        try {
            // 使用反射获取包的内容
            Field posField = ArmPlacementPacket.class.getDeclaredField("pos");
            posField.setAccessible(true);
            Object pos = posField.get(packet);
            System.out.println("[SERVER DEBUG] Target position: " + pos);
            
            Field selectionField = ArmPlacementPacket.class.getDeclaredField("selection");
            selectionField.setAccessible(true);
            List<?> selection = (List<?>) selectionField.get(packet);
            System.out.println("[SERVER DEBUG] Selection size: " + (selection != null ? selection.size() : "null"));
            
            // 检查目标动力臂
            if (ctx.getSender() != null && ctx.getSender().level() instanceof ServerLevel serverLevel) {
                BlockEntity be = serverLevel.getBlockEntity((net.minecraft.core.BlockPos) pos);
                if (be instanceof ArmBlockEntity arm) {
                    System.out.println("[SERVER DEBUG] Found ARM at position");
                    
                    // 使用反射检查动力臂的输入输出
                    Field inputsField = ArmBlockEntity.class.getDeclaredField("inputs");
                    inputsField.setAccessible(true);
                    List<?> inputs = (List<?>) inputsField.get(arm);
                    
                    Field outputsField = ArmBlockEntity.class.getDeclaredField("outputs");
                    outputsField.setAccessible(true);
                    List<?> outputs = (List<?>) outputsField.get(arm);
                    
                    System.out.println("[SERVER DEBUG] ARM before update - Inputs: " + inputs.size() + ", Outputs: " + outputs.size());
                    
                    // 等一个tick后再检查
                    ctx.enqueueWork(() -> {
                        try {
                            Thread.sleep(100);
                            List<?> inputsAfter = (List<?>) inputsField.get(arm);
                            List<?> outputsAfter = (List<?>) outputsField.get(arm);
                            System.out.println("[SERVER DEBUG] ARM after update - Inputs: " + inputsAfter.size() + ", Outputs: " + outputsAfter.size());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }
            }
            
        } catch (Exception e) {
            System.out.println("[SERVER DEBUG] Error debugging packet: " + e.getMessage());
            e.printStackTrace();
        }
    }
}