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
                System.out.println("[BeltMixin] getBeltProcessing: segment=" + segment +
                        ", beltPos=" + beltPos.toShortString() +
                        ", checkPos=" + checkPos.toShortString() +
                        " -> PIPETTE relay found!");
                cir.setReturnValue(behaviour);
                return;
            }
        }

        // 2. 再检查铜龙头虚拟中继器
        TapVirtualRelayManager.TapVirtualRelay tapRelay = TapVirtualRelayManager.getRelayAt(checkPos);
        if (tapRelay != null) {
            BeltProcessingBehaviour behaviour = tapRelay.getProcessingBehaviour();
            if (behaviour != null) {
                System.out.println("[BeltMixin] getBeltProcessing: segment=" + segment +
                        ", beltPos=" + beltPos.toShortString() +
                        ", checkPos=" + checkPos.toShortString() +
                        " -> TAP relay found! Behaviour=" + behaviour);
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
            System.out.println("[BeltMixin] getHandler: segment=" + segment + " -> PIPETTE relay found");
            TransportedItemStackHandlerBehaviour handler = BlockEntityBehaviour.get(
                    belt.getLevel(), beltPos, TransportedItemStackHandlerBehaviour.TYPE);
            System.out.println("[BeltMixin] getHandler: Belt handler = " + handler);
            if (handler != null) {
                cir.setReturnValue(handler);
                return;
            }
        }

        // 2. 再检查铜龙头虚拟中继器
        TapVirtualRelayManager.TapVirtualRelay tapRelay = TapVirtualRelayManager.getRelayAt(checkPos);
        if (tapRelay != null) {
            System.out.println("[BeltMixin] getHandler: segment=" + segment + " -> TAP relay found");
            TransportedItemStackHandlerBehaviour handler = BlockEntityBehaviour.get(
                    belt.getLevel(), beltPos, TransportedItemStackHandlerBehaviour.TYPE);
            System.out.println("[BeltMixin] getHandler: Belt handler = " + handler);
            if (handler != null) {
                cir.setReturnValue(handler);
                return;
            } else {
                System.out.println("[BeltMixin] getHandler: WARNING - Handler is NULL!");
            }
        }
    }

    /**
     * 手动处理铜龙头的传送带加工
     * 因为铜龙头在传送带上方1格，会被 isBlocked 检查阻止
     * 所以我们需要绕过这个检查，手动调用处理逻辑
     */
    @Inject(method = "handleBeltProcessingAndCheckIfRemoved",
            at = @At("HEAD"),
            cancellable = true)
    private void handleTapProcessing(TransportedItemStack currentItem, float nextOffset, boolean noMovement,
                                     CallbackInfoReturnable<Boolean> cir) {
        int currentSegment = (int) currentItem.beltPosition;
        boolean beltMovementPositive = belt.getDirectionAwareBeltMovementSpeed() > 0;

        // 检查当前 segment 是否有 tap relay
        BlockPos beltPos = BeltHelper.getPositionForOffset(belt, currentSegment);
        BlockPos checkPos = beltPos.above(2);
        TapVirtualRelayManager.TapVirtualRelay tapRelay = TapVirtualRelayManager.getRelayAt(checkPos);

        if (tapRelay == null) {
            return; // 没有铜龙头，让原方法处理
        }

        BeltProcessingBehaviour behaviour = tapRelay.getProcessingBehaviour();
        TransportedItemStackHandlerBehaviour handler = BlockEntityBehaviour.get(
                belt.getLevel(), beltPos, TransportedItemStackHandlerBehaviour.TYPE);

        if (behaviour == null || handler == null) {
            return;
        }

        // 如果物品已经被锁定，调用 handleHeldItem
        if (currentItem.locked) {
            BeltProcessingBehaviour.ProcessingResult result = behaviour.handleHeldItem(currentItem, handler);

            if (result == BeltProcessingBehaviour.ProcessingResult.REMOVE) {
                cir.setReturnValue(true); // 物品被移除
                return;
            }

            if (result == BeltProcessingBehaviour.ProcessingResult.HOLD) {
                cir.setReturnValue(false); // 继续锁定
                return;
            }

            // PASS - 解锁物品
            currentItem.locked = false;
            belt.notifyUpdate();
            cir.setReturnValue(false);
            return;
        }

        // 物品没有被锁定，检查是否跨越中心点
        float segmentCenter = currentSegment + 0.5f;
        boolean willCross = currentItem.beltPosition < segmentCenter && nextOffset >= segmentCenter;

        if (!willCross || noMovement) {
            return; // 物品没有跨越中心点，让原方法处理
        }

        // 物品正在跨越铜龙头下方的 segment 中心点
        // 手动调用处理逻辑，绕过 isBlocked 检查

        // 调用 handleReceivedItem
        BeltProcessingBehaviour.ProcessingResult result = behaviour.handleReceivedItem(currentItem, handler);

        if (result == BeltProcessingBehaviour.ProcessingResult.REMOVE) {
            cir.setReturnValue(true); // 物品被移除
            return;
        }

        if (result == BeltProcessingBehaviour.ProcessingResult.HOLD) {
            // 锁定物品
            currentItem.beltPosition = segmentCenter + (beltMovementPositive ? 1/512f : -1/512f);
            currentItem.locked = true;
            belt.notifyUpdate();
            cir.setReturnValue(false); // 物品被锁定，但没有被移除
            return;
        }

        // PASS - 让原方法继续处理
    }
}