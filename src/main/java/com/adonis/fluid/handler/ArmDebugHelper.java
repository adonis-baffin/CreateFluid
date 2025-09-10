package com.adonis.fluid.handler;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.lang.reflect.Method;
import java.util.List;

public class ArmDebugHelper {
    
    @OnlyIn(Dist.CLIENT)
    public static void testDirectUpdate(Level level, BlockPos armPos, List<ArmInteractionPoint> points) {
        System.out.println("[DEBUG] Testing direct update to ARM at " + armPos);
        
        BlockEntity be = level.getBlockEntity(armPos);
        if (!(be instanceof ArmBlockEntity arm)) {
            System.out.println("[DEBUG] No ARM found at position");
            return;
        }
        
        try {
            // 查找 setInteractionPoints 方法
            Method[] methods = ArmBlockEntity.class.getDeclaredMethods();
            for (Method method : methods) {
                System.out.println("[DEBUG] Found method: " + method.getName());
            }
            
            // 尝试找到处理交互点的方法
            Method setPointsMethod = null;
            for (Method method : methods) {
                if (method.getName().contains("setInteractionPoints") || 
                    method.getName().contains("updateInteractionPoints")) {
                    setPointsMethod = method;
                    break;
                }
            }
            
            if (setPointsMethod != null) {
                System.out.println("[DEBUG] Found setPoints method: " + setPointsMethod.getName());
                setPointsMethod.setAccessible(true);
                setPointsMethod.invoke(arm, points);
                System.out.println("[DEBUG] Called setPoints method");
            }
            
        } catch (Exception e) {
            System.out.println("[DEBUG] Error in direct update: " + e.getMessage());
            e.printStackTrace();
        }
    }
}