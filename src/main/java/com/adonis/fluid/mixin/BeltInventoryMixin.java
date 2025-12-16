package com.adonis.fluid.mixin;

import com.adonis.fluid.content.pipette.VirtualRelayManager;
import com.adonis.fluid.content.tap.TapVirtualRelayManager;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BeltInventory.class, remap = false)
public class BeltInventoryMixin {

    @Shadow @Final
    private BeltBlockEntity belt;

    /**
     * 注入 getBeltProcessingAtSegment，返回虚拟中继器的 BeltProcessingBehaviour
     */
    @Inject(method = "getBeltProcessingAtSegment",
            at = @At("HEAD"),
            cancellable = true)
    private void injectVirtualRelay(int segment, CallbackInfoReturnable<BeltProcessingBehaviour> cir) {
        BlockPos beltPos = BeltHelper.getPositionForOffset(belt, segment);
        BlockPos checkPos = beltPos.above(2);

        // 1. 先检查移液器虚拟中继器
        VirtualRelayManager.VirtualRelay pipetteRelay = VirtualRelayManager.getRelayAt(checkPos);
        if (pipetteRelay != null) {
            BeltProcessingBehaviour behaviour = pipetteRelay.getProcessingBehaviour();
            if (behaviour != null) {
                cir.setReturnValue(behaviour);
                return;
            }
        }

        // 2. 再检查铜龙头虚拟中继器
        TapVirtualRelayManager.TapVirtualRelay tapRelay = TapVirtualRelayManager.getRelayAt(checkPos);
        if (tapRelay != null) {
            BeltProcessingBehaviour behaviour = tapRelay.getProcessingBehaviour();
            if (behaviour != null) {
                cir.setReturnValue(behaviour);
                return;
            }
        }
    }

    /**
     * 注入 getTransportedItemStackHandlerAtSegment，返回虚拟中继器的 Handler
     */
    @Inject(method = "getTransportedItemStackHandlerAtSegment",
            at = @At("HEAD"),
            cancellable = true)
    private void injectVirtualRelayHandler(int segment, CallbackInfoReturnable<TransportedItemStackHandlerBehaviour> cir) {
        BlockPos beltPos = BeltHelper.getPositionForOffset(belt, segment);
        BlockPos checkPos = beltPos.above(2);

        // 1. 先检查移液器虚拟中继器
        VirtualRelayManager.VirtualRelay pipetteRelay = VirtualRelayManager.getRelayAt(checkPos);
        if (pipetteRelay != null) {
            TransportedItemStackHandlerBehaviour handler = BlockEntityBehaviour.get(
                    belt.getLevel(), beltPos, TransportedItemStackHandlerBehaviour.TYPE);
            if (handler != null) {
                cir.setReturnValue(handler);
                return;
            }
        }

        // 2. 再检查铜龙头虚拟中继器
        TapVirtualRelayManager.TapVirtualRelay tapRelay = TapVirtualRelayManager.getRelayAt(checkPos);
        if (tapRelay != null) {
            TransportedItemStackHandlerBehaviour handler = BlockEntityBehaviour.get(
                    belt.getLevel(), beltPos, TransportedItemStackHandlerBehaviour.TYPE);
            if (handler != null) {
                cir.setReturnValue(handler);
                return;
            }
        }
    }

    /**
     * 修复版：手动处理铜龙头的传送带加工
     * 支持超高转速（255/256 RPM+），物品不会掠过
     */
    @Inject(method = "handleBeltProcessingAndCheckIfRemoved",
            at = @At("HEAD"),
            cancellable = true)
    private void handleTapProcessing(TransportedItemStack currentItem, float nextOffset, boolean noMovement,
                                     CallbackInfoReturnable<Boolean> cir) {
        // 计算当前 segment
        int currentSegment = (int) currentItem.beltPosition;
        float currentPos = currentItem.beltPosition;
        float segmentCenter = currentSegment + 0.5f;

        BlockPos beltPos = BeltHelper.getPositionForOffset(belt, currentSegment);
        BlockPos checkPos = beltPos.above(2);

        TapVirtualRelayManager.TapVirtualRelay tapRelay = TapVirtualRelayManager.getRelayAt(checkPos);
        if (tapRelay == null) {
            return; // 没有铜龙头，交给原方法处理
        }

        BeltProcessingBehaviour behaviour = tapRelay.getProcessingBehaviour();
        TransportedItemStackHandlerBehaviour handler = BlockEntityBehaviour.get(
                belt.getLevel(), beltPos, TransportedItemStackHandlerBehaviour.TYPE);

        if (behaviour == null || handler == null) {
            return;
        }

        boolean beltMovementPositive = belt.getDirectionAwareBeltMovementSpeed() > 0;

        // 已锁定物品 → 处理 held 状态
        if (currentItem.locked) {
            BeltProcessingBehaviour.ProcessingResult result = behaviour.handleHeldItem(currentItem, handler);

            if (result == BeltProcessingBehaviour.ProcessingResult.REMOVE) {
                cir.setReturnValue(true);
            } else if (result == BeltProcessingBehaviour.ProcessingResult.HOLD) {
                cir.setReturnValue(false);
            } else { // PASS
                currentItem.locked = false;
                belt.notifyUpdate();
                cir.setReturnValue(false);
            }
            return;
        }

        // 未锁定物品 → 判断是否应该触发处理
        // 宽容条件：
        // 1. 当前位置已经在中心或之后（高速跳过时可能直接进入）
        // 2. 或这个 tick 会跨越中心
        // 3. 或当前小数部分在合理处理范围内（防止极端浮点误差）
        float fractional = currentPos - currentSegment;
        boolean inProcessingRange = fractional >= 0.0f && fractional < 1.0f; // 本 segment 内
        boolean alreadyPastCenter = fractional >= 0.5f;
        boolean willCrossCenter = currentPos < segmentCenter && nextOffset >= segmentCenter;

        if (!noMovement && inProcessingRange && (willCrossCenter || alreadyPastCenter)) {
            // 触发接收处理
            BeltProcessingBehaviour.ProcessingResult result = behaviour.handleReceivedItem(currentItem, handler);

            if (result == BeltProcessingBehaviour.ProcessingResult.REMOVE) {
                cir.setReturnValue(true);
                return;
            }

            if (result == BeltProcessingBehaviour.ProcessingResult.HOLD) {
                // 强制锁定并微调位置到中心附近，防止继续高速前进
                currentItem.beltPosition = segmentCenter + (beltMovementPositive ? 0.03125f : -0.03125f); // 1/32 格偏移
                currentItem.locked = true;
                belt.notifyUpdate();
                cir.setReturnValue(false);
                return;
            }

            // PASS → 继续正常移动
        }
    }
}