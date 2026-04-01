package com.adonis.fluid.handler;

import com.adonis.fluid.item.BatonItem;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import com.adonis.fluid.CreateFluid;

/**
 * 专门处理粉碎轮控制器的指挥棒交互
 * 
 * 粉碎轮控制器的碰撞箱在某些情况下会返回 Shapes.empty()，
 * 导致玩家无法选中该方块，从而无法触发 LeftClickBlock 事件。
 * 
 * 这个处理器使用射线追踪来检测玩家是否看着粉碎轮控制器，
 * 即使碰撞箱为空也能正确处理交互点取消。
 */
@EventBusSubscriber(modid = CreateFluid.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class CrushingWheelBatonHandler {

    /**
     * 拦截鼠标左键点击事件
     * 这个事件在 LeftClickBlock 之前触发，可以处理碰撞箱为空的方块
     */
    @SubscribeEvent
    public static void onMouseClick(InputEvent.MouseButton.Pre event) {
        // 检查是否是左键（按钮 0）
        if (event.getButton() != 0) {
            return;
        }

        // 检查是否是按下动作（1 = 按下，0 = 释放）
        if (event.getAction() != 1) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        
        if (player == null) {
            return;
        }

        // 检查是否手持指挥棒
        ItemStack heldItem = player.getMainHandItem();
        if (!(heldItem.getItem() instanceof BatonItem)) {
            return;
        }

        // 检查是否处于ARM选择模式
        if (!BatonInteractionHandler.isInSelectionMode() || 
            BatonInteractionHandler.getSelectionType() != BatonInteractionHandler.SelectionType.ARM) {
            return;
        }

        Level level = player.level();
        
        // 进行射线追踪，使用 VISUAL 模式来检测即使碰撞箱为空的方块
        // COLLIDER 模式只检测有碰撞箱的方块
        // VISUAL 模式会检测方块的视觉边界
        // NeoForge 1.21: 使用属性获取触及距离
        double reach = mc.player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE).getValue();
        Vec3 eyePos = player.getEyePosition(1.0F);
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(lookVec.x * reach, lookVec.y * reach, lookVec.z * reach);
        
        // 首先尝试 COLLIDER 模式
        BlockHitResult hitResult = level.clip(new ClipContext(
            eyePos, endPos,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            player
        ));

        BlockPos pos = null;
        BlockState state = null;
        
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            pos = hitResult.getBlockPos();
            state = level.getBlockState(pos);
        }
        
        // 如果 COLLIDER 模式没有命中，尝试使用 OUTLINE 模式
        // 这会检测方块的轮廓，即使碰撞箱为空
        if (pos == null) {
            hitResult = level.clip(new ClipContext(
                eyePos, endPos,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
            ));
            
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                pos = hitResult.getBlockPos();
                state = level.getBlockState(pos);
            }
        }

        if (pos == null) {
            return;
        }

        // 检查是否是粉碎轮控制器
        if (!AllBlocks.CRUSHING_WHEEL_CONTROLLER.has(state)) {
            return;
        }

        // 检查该位置是否有交互点
        boolean hasInteractionPoint = false;
        for (ArmInteractionPoint point : BatonInteractionHandler.getCurrentArmSelection()) {
            BlockPos pointPos = point.getPos();
            if (pointPos.getX() == pos.getX() && 
                pointPos.getY() == pos.getY() && 
                pointPos.getZ() == pos.getZ()) {
                hasInteractionPoint = true;
                break;
            }
        }

        if (!hasInteractionPoint) {
            return;
        }

        // 在NeoForge 1.21中，MouseButton事件无法直接取消，我们需要通过其他方式处理
        // 这里我们通过设置一个标志或依赖LeftClickBlock事件的处理

        // 移除交互点
        boolean removed = BatonInteractionHandler.removeArmPointAt(pos);

        if (removed) {
            CreateLang.builder()
                    .translate("fluid.baton.interaction_point_removed")
                    .style(ChatFormatting.RED)
                    .sendStatus(player);
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 0.8f, false);
        }

        // 执行左键挥动手臂动画
        player.swing(InteractionHand.MAIN_HAND);
    }
}
