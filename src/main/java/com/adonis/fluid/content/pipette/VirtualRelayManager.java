package com.adonis.fluid.content.pipette;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemHandlerHelper;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualRelayManager {
    private static final Map<BlockPos, VirtualRelay> activeRelays = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Set<BlockPos>> workstationToRelays = new ConcurrentHashMap<>();

    public static class VirtualRelay {
        private final BlockPos beltSegmentPos;
        private final BlockPos relayPos;
        private final BlockPos workstationPos;
        private WeakReference<BlockEntity> workstationRef;
        private final BeltProcessingBehaviour processingBehaviour;
        private final TransportedItemStackHandlerBehaviour itemHandler;
        private TransportedItemStack currentlyProcessing;
        private int localProcessingTicks = -1;
        private boolean waitingForFluid = false;
        private static final int FILLING_TIME = 20;

        public VirtualRelay(BlockPos beltPos, BlockPos workstationPos, Level level) {
            this.beltSegmentPos = beltPos;
            this.relayPos = beltPos.above(2);
            this.workstationPos = workstationPos;

            BlockEntity workstation = level.getBlockEntity(workstationPos);
            this.workstationRef = new WeakReference<>(workstation);

            this.processingBehaviour = new BeltProcessingBehaviour(null) {
                @Override
                public ProcessingResult handleReceivedItem(TransportedItemStack transported,
                                                           TransportedItemStackHandlerBehaviour handler) {
                    return onItemReceived(transported, handler);
                }

                @Override
                public ProcessingResult handleHeldItem(TransportedItemStack transported,
                                                       TransportedItemStackHandlerBehaviour handler) {
                    return whenItemHeld(transported, handler);
                }
            };

            this.itemHandler = new TransportedItemStackHandlerBehaviour(null, this::applyToItems);
        }

        private BeltProcessingBehaviour.ProcessingResult onItemReceived(TransportedItemStack transported,
                                                                        TransportedItemStackHandlerBehaviour handler) {
            BlockEntity workstation = workstationRef.get();
            if (!(workstation instanceof IRemoteFluidProcessor processor)) {
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 检查物品是否可以被填充
            if (!FillingBySpout.canItemBeFilled(workstation.getLevel(), transported.stack)) {
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 检查是否有流体
            FluidStack fluid = processor.getHeldFluid();

            if (fluid.isEmpty()) {
                // 请求取液
                if (processor.requestFluidForItem(transported.stack, beltSegmentPos)) {
                    currentlyProcessing = transported;
                    waitingForFluid = true;
                    return BeltProcessingBehaviour.ProcessingResult.HOLD;
                }
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 检查流体是否足够（只检查单个物品的需求）
            ItemStack singleItem = ItemHandlerHelper.copyStackWithSize(transported.stack, 1);
            int required = FillingBySpout.getRequiredAmountForItem(
                    workstation.getLevel(), singleItem, fluid);

            if (required == -1 || required > fluid.getAmount()) {
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 开始处理
            currentlyProcessing = transported;
            localProcessingTicks = FILLING_TIME;
            waitingForFluid = false;

            // 触发移液器动画
            processor.notifyProcessingStarted(beltSegmentPos);

            return BeltProcessingBehaviour.ProcessingResult.HOLD;
        }

        private BeltProcessingBehaviour.ProcessingResult whenItemHeld(TransportedItemStack transported,
                                                                      TransportedItemStackHandlerBehaviour handler) {
            if (currentlyProcessing != transported) {
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            BlockEntity workstation = workstationRef.get();
            if (!(workstation instanceof IRemoteFluidProcessor processor)) {
                currentlyProcessing = null;
                waitingForFluid = false;
                localProcessingTicks = -1;
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 等待流体
            if (waitingForFluid) {
                FluidStack fluid = processor.getHeldFluid();
                if (fluid.isEmpty()) {
                    return BeltProcessingBehaviour.ProcessingResult.HOLD;
                }

                ItemStack singleItem = ItemHandlerHelper.copyStackWithSize(transported.stack, 1);
                int required = FillingBySpout.getRequiredAmountForItem(
                        workstation.getLevel(), singleItem, fluid);

                if (required == -1 || required > fluid.getAmount()) {
                    currentlyProcessing = null;
                    waitingForFluid = false;
                    return BeltProcessingBehaviour.ProcessingResult.PASS;
                }

                // 开始处理
                waitingForFluid = false;
                localProcessingTicks = FILLING_TIME;
                processor.notifyProcessingStarted(beltSegmentPos);
            }

            // 处理计时
            if (localProcessingTicks > 0) {
                localProcessingTicks--;

                // 在倒计时第5个tick执行填充（与原版相同）
                if (localProcessingTicks == 5) {
                    FluidStack fluid = processor.getHeldFluid();
                    Level level = workstation.getLevel();

                    // 始终逐个处理，除非是单个物品
                    boolean isSingle = transported.stack.getCount() == 1;

                    // 获取单个物品的流体需求
                    ItemStack singleItem = ItemHandlerHelper.copyStackWithSize(transported.stack, 1);
                    int required = FillingBySpout.getRequiredAmountForItem(level, singleItem, fluid);

                    if (required > 0 && required <= fluid.getAmount()) {
                        // 使用FillingBySpout执行填充
                        // 注意：fillItem会消耗传入的ItemStack
                        ItemStack toFill = transported.stack.copy();
                        toFill.setCount(1);

                        ItemStack filledResult = FillingBySpout.fillItem(
                                level, required, toFill, fluid.copy());

                        if (!filledResult.isEmpty()) {
                            // 清除风扇处理数据
                            transported.clearFanProcessingData();

                            // 准备输出列表
                            List<TransportedItemStack> outList = new ArrayList<>();
                            TransportedItemStack resultStack = transported.copy();
                            resultStack.stack = filledResult;
                            outList.add(resultStack);

                            // 消耗流体
                            fluid.shrink(required);
                            processor.syncFluid(fluid);

                            if (isSingle) {
                                // 单个物品：直接替换
                                handler.handleProcessingOnItem(transported,
                                        TransportedItemStackHandlerBehaviour.TransportedResult.convertTo(outList));
                            } else {
                                // 多个物品：减少原堆栈，保留剩余
                                transported.stack.shrink(1);

                                TransportedItemStack leftover = null;
                                if (!transported.stack.isEmpty()) {
                                    leftover = transported.copy();
                                    leftover.stack = transported.stack.copy();
                                }

                                handler.handleProcessingOnItem(transported,
                                        TransportedItemStackHandlerBehaviour.TransportedResult
                                                .convertToAndLeaveHeld(outList, leftover));
                            }

                            // 发送粒子效果
                            if (processor instanceof com.adonis.fluid.block.Pipette.PipetteBlockEntity pipette) {
                                pipette.sendBeltProcessingEffects(beltSegmentPos, fluid);
                            }

                            // 如果还有剩余物品且有足够流体，继续处理
                            if (!isSingle && !transported.stack.isEmpty()) {
                                int nextRequired = FillingBySpout.getRequiredAmountForItem(
                                        level, ItemHandlerHelper.copyStackWithSize(transported.stack, 1), fluid);

                                if (nextRequired > 0 && nextRequired <= fluid.getAmount()) {
                                    // 重置计时器继续处理下一个
                                    localProcessingTicks = FILLING_TIME;
                                    return BeltProcessingBehaviour.ProcessingResult.HOLD;
                                }
                            }
                        }
                    }
                }

                if (localProcessingTicks > 0) {
                    return BeltProcessingBehaviour.ProcessingResult.HOLD;
                }
            }

            // 处理完成
            currentlyProcessing = null;
            localProcessingTicks = -1;

            // 通知移液器
            processor.notifyProcessingCompleted(beltSegmentPos);

            return BeltProcessingBehaviour.ProcessingResult.PASS;
        }

        private void applyToItems(float maxDistance,
                                  java.util.function.Function<TransportedItemStack,
                                          TransportedItemStackHandlerBehaviour.TransportedResult> processFunction) {
            // 虚拟中继器无需实际应用到所有物品
        }

        public BeltProcessingBehaviour getProcessingBehaviour() {
            return processingBehaviour;
        }

        public TransportedItemStackHandlerBehaviour getItemHandler() {
            return itemHandler;
        }

        public boolean isValid() {
            return workstationRef.get() != null;
        }
    }

    // 以下方法保持不变
    public static void registerWorkstation(BlockPos workstationPos, Level level, int range) {
        if (!(level.getBlockEntity(workstationPos) instanceof IRemoteFluidProcessor processor)) {
            return;
        }

        if (processor instanceof com.adonis.fluid.block.Pipette.PipetteBlockEntity pipette) {
            Set<BlockPos> relayPositions = new HashSet<>();

            for (FluidInteractionPoint output : pipette.outputs) {
                BlockPos outputPos = output.getPos();
                BlockState state = level.getBlockState(outputPos);

                if (AllBlocks.BELT.has(state)) {
                    BlockPos relayPos = outputPos.above(2);

                    if (canPlaceRelayAt(level, relayPos)) {
                        VirtualRelay relay = new VirtualRelay(outputPos, workstationPos, level);
                        activeRelays.put(relayPos, relay);
                        relayPositions.add(relayPos);
                    }
                }
            }

            workstationToRelays.put(workstationPos, relayPositions);
        }
    }

    public static void unregisterWorkstation(BlockPos workstationPos) {
        Set<BlockPos> relayPositions = workstationToRelays.remove(workstationPos);
        if (relayPositions != null) {
            relayPositions.forEach(activeRelays::remove);
        }
    }

    private static boolean canPlaceRelayAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            if (com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(
                    level, pos, BeltProcessingBehaviour.TYPE) != null) {
                return false;
            }
        }

        return activeRelays.containsKey(pos);
    }

    public static VirtualRelay getRelayAt(BlockPos pos) {
        return activeRelays.get(pos);
    }

    public static void updateWorkstationRelays(BlockPos workstationPos, Level level) {
        unregisterWorkstation(workstationPos);
        registerWorkstation(workstationPos, level, 5);
    }
}