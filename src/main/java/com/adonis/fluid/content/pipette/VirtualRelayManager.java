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

            // 检查handler是否有效
            if (handler == null) {
                currentlyProcessing = null;
                localProcessingTicks = -1;
                waitingForFluid = false;
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

                // 在倒计时第5个tick执行填充
                if (localProcessingTicks == 5) {
                    FluidStack fluid = processor.getHeldFluid();
                    Level level = workstation.getLevel();

                    // 判断是否批量处理
                    boolean bulk = canProcessInBulk() || transported.stack.getCount() == 1;

                    // 准备要处理的物品
                    ItemStack toProcess;
                    if (bulk) {
                        toProcess = transported.stack.copy();
                    } else {
                        toProcess = ItemHandlerHelper.copyStackWithSize(transported.stack, 1);
                    }

                    // 获取流体需求
                    int required = FillingBySpout.getRequiredAmountForItem(level, toProcess, fluid);

                    if (required > 0 && required <= fluid.getAmount()) {
                        // 创建流体副本用于填充
                        FluidStack fluidForFilling = fluid.copy();
                        fluidForFilling.setAmount(required);

                        // 执行填充（FillingBySpout.fillItem会消耗传入的ItemStack）
                        ItemStack filledResult = FillingBySpout.fillItem(
                                level, required, toProcess, fluidForFilling);

                        if (!filledResult.isEmpty()) {
                            // 清除风扇处理数据
                            transported.clearFanProcessingData();

                            // 准备输出
                            List<TransportedItemStack> outList = new ArrayList<>();
                            TransportedItemStack resultTransported = transported.copy();
                            resultTransported.stack = filledResult;
                            outList.add(resultTransported);

                            // 从移液器消耗流体
                            fluid.shrink(required);
                            processor.syncFluid(fluid);

                            if (bulk) {
                                // 批量处理：完全替换
                                TransportedItemStackHandlerBehaviour.TransportedResult result =
                                        TransportedItemStackHandlerBehaviour.TransportedResult.convertTo(outList);
                                handler.handleProcessingOnItem(transported, result);
                            } else {
                                // 逐个处理：保留剩余
                                TransportedItemStack leftover = null;

                                // 创建剩余物品（原堆栈减1）
                                if (transported.stack.getCount() > 1) {
                                    leftover = transported.copy();
                                    leftover.stack = transported.stack.copy();
                                    leftover.stack.shrink(1);
                                }

                                TransportedItemStackHandlerBehaviour.TransportedResult result =
                                        TransportedItemStackHandlerBehaviour.TransportedResult
                                                .convertToAndLeaveHeld(outList, leftover);
                                handler.handleProcessingOnItem(transported, result);
                            }

                            // 发送粒子效果
                            if (processor instanceof com.adonis.fluid.block.Pipette.PipetteBlockEntity pipette) {
                                pipette.sendBeltProcessingEffects(beltSegmentPos, fluid);
                            }

                            // 如果不是批量处理且还有剩余物品和流体，准备处理下一个
                            if (!bulk && transported.stack.getCount() > 1) {
                                ItemStack nextSingle = ItemHandlerHelper.copyStackWithSize(transported.stack, 1);
                                int nextRequired = FillingBySpout.getRequiredAmountForItem(level, nextSingle, fluid);

                                if (nextRequired > 0 && nextRequired <= fluid.getAmount()) {
                                    // 重置计时器继续处理
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

        private boolean canProcessInBulk() {
            // 对于蜂蜜瓶这类物品，应该返回false以逐个处理
            return false;
        }

        public BeltProcessingBehaviour getProcessingBehaviour() {
            return processingBehaviour;
        }

        public boolean isValid() {
            return workstationRef.get() != null;
        }
    }

    // 注册工作站
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

                    // 强制创建虚拟中继器，无视任何阻碍
                    VirtualRelay relay = new VirtualRelay(outputPos, workstationPos, level);
                    activeRelays.put(relayPos, relay);
                    relayPositions.add(relayPos);
                }
            }

            workstationToRelays.put(workstationPos, relayPositions);
        }
    }

    // 注销工作站
    public static void unregisterWorkstation(BlockPos workstationPos) {
        Set<BlockPos> relayPositions = workstationToRelays.remove(workstationPos);
        if (relayPositions != null) {
            relayPositions.forEach(activeRelays::remove);
        }
    }

    // 检查是否可以在指定位置放置中继器（现在总是返回true）
    private static boolean canPlaceRelayAt(Level level, BlockPos pos) {
        return true;
    }

    // 获取指定位置的中继器
    public static VirtualRelay getRelayAt(BlockPos pos) {
        return activeRelays.get(pos);
    }

    // 更新工作站的中继器
    public static void updateWorkstationRelays(BlockPos workstationPos, Level level) {
        unregisterWorkstation(workstationPos);
        registerWorkstation(workstationPos, level, 5);
    }
}