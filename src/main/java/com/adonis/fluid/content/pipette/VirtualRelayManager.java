package com.adonis.fluid.content.pipette;

import com.adonis.fluid.block.Pipette.PipetteBlockEntity;
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
        private boolean waitingForPipetteAnimation = false;
        private static final int FILLING_TIME = 20;
        private boolean pipetteReady = false;
        private boolean particlesSent = false;
        private boolean injectionReadySignalReceived = false;

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

        public BeltProcessingBehaviour getProcessingBehaviour() {
            return this.processingBehaviour;
        }

        public void notifyInjectionReady() {
            this.injectionReadySignalReceived = true;
        }

        private BeltProcessingBehaviour.ProcessingResult onItemReceived(TransportedItemStack transported,
                                                                        TransportedItemStackHandlerBehaviour handler) {
            BlockEntity workstation = workstationRef.get();
            if (!(workstation instanceof IRemoteFluidProcessor processor)) {
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            if (!FillingBySpout.canItemBeFilled(workstation.getLevel(), transported.stack)) {
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            FluidStack fluid = processor.getHeldFluid();
            ItemStack singleItem = ItemHandlerHelper.copyStackWithSize(transported.stack, 1);
            int required = FillingBySpout.getRequiredAmountForItem(
                    workstation.getLevel(), singleItem, fluid);

            // 情况1：没有流体或流体不足
            if (fluid.isEmpty() || required <= 0 || required > fluid.getAmount()) {
                // 请求取液（移液器会自动处理流体不匹配的情况）
                if (processor.requestFluidForItem(transported.stack, beltSegmentPos)) {
                    currentlyProcessing = transported;
                    waitingForFluid = true;
                    waitingForPipetteAnimation = false;
                    injectionReadySignalReceived = false;
                    particlesSent = false;
                    return BeltProcessingBehaviour.ProcessingResult.HOLD;
                }
                // 无法获取流体，让物品通过
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 情况2：流体充足且匹配，等待移液器就位
            if (required > 0 && required <= fluid.getAmount()) {
                currentlyProcessing = transported;
                waitingForFluid = false;
                waitingForPipetteAnimation = true;
                injectionReadySignalReceived = false;
                particlesSent = false;
                localProcessingTicks = -1;
                processor.notifyProcessingStarted(beltSegmentPos);
                return BeltProcessingBehaviour.ProcessingResult.HOLD;
            }

            return BeltProcessingBehaviour.ProcessingResult.PASS;
        }

        private BeltProcessingBehaviour.ProcessingResult whenItemHeld(TransportedItemStack transported,
                                                                      TransportedItemStackHandlerBehaviour handler) {
            if (currentlyProcessing != transported) {
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            if (handler == null) {
                resetState();
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            BlockEntity workstation = workstationRef.get();
            if (!(workstation instanceof IRemoteFluidProcessor processor)) {
                resetState();
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 等待流体阶段
            if (waitingForFluid) {
                FluidStack fluid = processor.getHeldFluid();
                ItemStack singleItem = ItemHandlerHelper.copyStackWithSize(transported.stack, 1);
                int required = FillingBySpout.getRequiredAmountForItem(
                        workstation.getLevel(), singleItem, fluid);

                if (!fluid.isEmpty() && required > 0 && required <= fluid.getAmount()) {
                    waitingForFluid = false;
                    waitingForPipetteAnimation = true;
                    injectionReadySignalReceived = false;
                    processor.notifyProcessingStarted(beltSegmentPos);
                }
                return BeltProcessingBehaviour.ProcessingResult.HOLD;
            }

            // 等待移液器动画
            if (waitingForPipetteAnimation) {
                if (processor instanceof com.adonis.fluid.block.Pipette.PipetteBlockEntity pipette) {
                    com.adonis.fluid.block.Pipette.PipetteBlockEntity.SpeedMode mode = pipette.getSpeedMode();

                    switch (mode) {
                        case HIGH:
                            boolean willReachSoon = pipette.willReachInjectionPoint();
                            if (injectionReadySignalReceived || pipette.isReadyToInject() || willReachSoon) {
                                waitingForPipetteAnimation = false;
                                localProcessingTicks = willReachSoon ? 6 : 5;
                            }
                            break;

                        case LOW:
                            if (pipette.isContinuousProcessing() && pipette.getWorkProgress() >= 0.95F) {
                                waitingForPipetteAnimation = false;
                                localProcessingTicks = 8;
                            } else if (injectionReadySignalReceived || pipette.getWorkProgress() >= 0.6F) {
                                waitingForPipetteAnimation = false;
                                localProcessingTicks = 10;
                            }
                            break;

                        case ULTRA_LOW:
                            if (injectionReadySignalReceived) {
                                float progress = pipette.getWorkProgress();
                                if (progress >= 0.8F) {
                                    waitingForPipetteAnimation = false;
                                    float speed = Math.abs(pipette.getSpeed());
                                    int remainingTicks = (int)((1.0F - progress) * 1024.0F / Math.max(speed, 1.0F));
                                    localProcessingTicks = Math.min(remainingTicks + 5, 15);
                                }
                            }
                            break;
                    }
                }
                return BeltProcessingBehaviour.ProcessingResult.HOLD;
            }

            // 处理阶段
            if (localProcessingTicks > 0) {
                localProcessingTicks--;

                if (localProcessingTicks == 4 && !particlesSent) {
                    BlockEntity ws = workstationRef.get();
                    if (ws instanceof com.adonis.fluid.block.Pipette.PipetteBlockEntity pipette) {
                        FluidStack fluidForParticles = pipette.getHeldFluid().copy();
                        pipette.sendBeltProcessingEffects(beltSegmentPos, fluidForParticles);
                        particlesSent = true;
                    }
                }

                if (localProcessingTicks == 2) {
                    performActualFilling(transported, handler, processor);
                }

                if (localProcessingTicks > 0) {
                    return BeltProcessingBehaviour.ProcessingResult.HOLD;
                }
            }

            resetState();
            return BeltProcessingBehaviour.ProcessingResult.PASS;
        }

        private void performActualFilling(TransportedItemStack transported,
                                          TransportedItemStackHandlerBehaviour handler,
                                          IRemoteFluidProcessor processor) {
            FluidStack fluid = processor.getHeldFluid();
            Level level = workstationRef.get().getLevel();

            boolean bulk = canProcessInBulk() || transported.stack.getCount() == 1;

            ItemStack toProcess;
            if (bulk) {
                toProcess = transported.stack.copy();
            } else {
                toProcess = ItemHandlerHelper.copyStackWithSize(transported.stack, 1);
            }

            int required = FillingBySpout.getRequiredAmountForItem(level, toProcess, fluid);

            if (required > 0 && required <= fluid.getAmount()) {
                FluidStack fluidForFilling = fluid.copy();
                fluidForFilling.setAmount(required);

                ItemStack filledResult = FillingBySpout.fillItem(
                        level, required, toProcess, fluidForFilling);

                if (!filledResult.isEmpty()) {
                    transported.clearFanProcessingData();

                    List<TransportedItemStack> outList = new ArrayList<>();
                    TransportedItemStack resultTransported = transported.copy();
                    resultTransported.stack = filledResult;
                    outList.add(resultTransported);

                    // 消耗流体
                    fluid.shrink(required);
                    processor.syncFluid(fluid);

                    if (bulk) {
                        TransportedItemStackHandlerBehaviour.TransportedResult result =
                                TransportedItemStackHandlerBehaviour.TransportedResult.convertTo(outList);
                        handler.handleProcessingOnItem(transported, result);
                    } else {
                        TransportedItemStack leftover = null;

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

                    // 检查是否还有下一个物品需要处理
                    if (!bulk && transported.stack.getCount() > 1) {
                        ItemStack nextSingle = ItemHandlerHelper.copyStackWithSize(transported.stack, 1);
                        int nextRequired = FillingBySpout.getRequiredAmountForItem(level, nextSingle, fluid);

                        if (nextRequired > 0 && nextRequired <= fluid.getAmount()) {
                            if (processor instanceof com.adonis.fluid.block.Pipette.PipetteBlockEntity pipette) {
                                com.adonis.fluid.block.Pipette.PipetteBlockEntity.SpeedMode mode = pipette.getSpeedMode();

                                switch (mode) {
                                    case HIGH:
                                        if (!pipette.isContinuousProcessing()) {
                                            pipette.startContinuousProcessing();
                                        }
                                        pipette.incrementContinuousProcessing();
                                        waitingForPipetteAnimation = false;
                                        injectionReadySignalReceived = true;
                                        particlesSent = false;
                                        localProcessingTicks = 3;
                                        return;  // 继续处理，不结束

                                    case LOW:
                                        if (!pipette.isContinuousProcessing()) {
                                            pipette.startContinuousProcessing();
                                        }
                                        pipette.incrementContinuousProcessing();
                                        waitingForPipetteAnimation = false;
                                        injectionReadySignalReceived = false;
                                        particlesSent = false;
                                        localProcessingTicks = 8;
                                        return;  // 继续处理，不结束

                                    case ULTRA_LOW:
                                        if (!pipette.isContinuousProcessing()) {
                                            pipette.startContinuousProcessing();
                                        }
                                        pipette.incrementContinuousProcessing();
                                        waitingForPipetteAnimation = true;
                                        injectionReadySignalReceived = false;
                                        particlesSent = false;
                                        localProcessingTicks = -1;
                                        return;  // 继续处理，不结束
                                }
                            }
                        }
                    }

// 走到这里说明：这是最后一个物品！必须结束服务
                    if (processor instanceof com.adonis.fluid.block.Pipette.PipetteBlockEntity pipette) {
                        if (pipette.isContinuousProcessing()) {
                            pipette.endContinuousProcessing();
                        }
                        // 关键！在这里调用结束服务
                        pipette.onBeltProcessingFinished(beltSegmentPos);
                    }
                }
            }
        }

        private void resetState() {
            currentlyProcessing = null;
            localProcessingTicks = -1;
            waitingForFluid = false;
            waitingForPipetteAnimation = false;
            pipetteReady = false;
            particlesSent = false;
            injectionReadySignalReceived = false;
        }

        private boolean canProcessInBulk() {
            // 对于蜂蜜瓶这类物品，应该返回false以逐个处理
            return false;
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

    // 获取指定位置的中继器
    public static VirtualRelay getRelayAt(BlockPos pos) {
        return activeRelays.get(pos);
    }

    // 更新工作站的中继器
    public static void updateWorkstationRelays(BlockPos workstationPos, Level level) {
        unregisterWorkstation(workstationPos);
        registerWorkstation(workstationPos, level, 5);
    }

    // 新增：通知虚拟中继器注液准备就绪
    public static void notifyInjectionReady(BlockPos beltPos) {
        // 查找对应的虚拟中继器
        BlockPos relayPos = beltPos.above(2);
        VirtualRelay relay = activeRelays.get(relayPos);
        if (relay != null) {
            relay.notifyInjectionReady();
        }
    }
}