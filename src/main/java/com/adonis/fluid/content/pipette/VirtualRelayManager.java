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
        private boolean pipetteReady = false;

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

            // 如果流体不足或为空，请求取液
            if (fluid.isEmpty() || (required > 0 && required > fluid.getAmount())) {
                if (processor.requestFluidForItem(transported.stack, beltSegmentPos)) {
                    currentlyProcessing = transported;
                    waitingForFluid = true;
                    // 立即返回HOLD让物品停下
                    return BeltProcessingBehaviour.ProcessingResult.HOLD;
                }
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 流体充足，直接处理
            if (required > 0 && required <= fluid.getAmount()) {
                currentlyProcessing = transported;
                localProcessingTicks = FILLING_TIME;
                waitingForFluid = false;
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
                currentlyProcessing = null;
                localProcessingTicks = -1;
                waitingForFluid = false;
                pipetteReady = false;
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            BlockEntity workstation = workstationRef.get();
            if (!(workstation instanceof IRemoteFluidProcessor processor)) {
                currentlyProcessing = null;
                waitingForFluid = false;
                localProcessingTicks = -1;
                pipetteReady = false;
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
                    pipetteReady = false;  // 重置移液器就绪标志
                    processor.notifyProcessingStarted(beltSegmentPos);
                }
                return BeltProcessingBehaviour.ProcessingResult.HOLD;
            }

            // 等待移液器到达传送带位置
            if (!pipetteReady) {
                if (processor instanceof com.adonis.fluid.block.Pipette.PipetteBlockEntity pipette) {
                    // 使用公开的方法检查移液器状态
                    // isInjectMode() 表示正在输出模式
                    // getWorkProgress() >= 1.0F 表示已经到达目标
                    if (pipette.isInjectMode() && pipette.getWorkProgress() >= 1.0F) {
                        // 移液器已经到达，可以开始处理
                        pipetteReady = true;
                        localProcessingTicks = 10;  // 短暂的注液动画时间
                    }
                }
                return BeltProcessingBehaviour.ProcessingResult.HOLD;
            }

            // 执行加工阶段
            if (localProcessingTicks > 0) {
                localProcessingTicks--;

                // 发送粒子效果
                if (localProcessingTicks == 8) {
                    BlockEntity ws = workstationRef.get();
                    if (ws instanceof com.adonis.fluid.block.Pipette.PipetteBlockEntity pipette) {
                        FluidStack fluidForParticles = pipette.getHeldFluid().copy();
                        pipette.sendBeltProcessingEffects(beltSegmentPos, fluidForParticles);
                    }
                }

                // 执行实际填充
                if (localProcessingTicks == 3) {
                    FluidStack fluid = processor.getHeldFluid();
                    Level level = workstation.getLevel();

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
                                    // 移液器已经在位置上，可以直接开始下一个
                                    pipetteReady = true;
                                    localProcessingTicks = 10;
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

            // 处理完成，清理状态
            currentlyProcessing = null;
            localProcessingTicks = -1;
            waitingForFluid = false;
            pipetteReady = false;

            // 通知移液器立即开始下一个工作循环
            if (processor instanceof com.adonis.fluid.block.Pipette.PipetteBlockEntity pipette) {
                pipette.onBeltProcessingFinished(beltSegmentPos);
            }

            return BeltProcessingBehaviour.ProcessingResult.PASS;
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
}