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
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            BlockEntity workstation = workstationRef.get();
            if (!(workstation instanceof IRemoteFluidProcessor processor)) {
                currentlyProcessing = null;
                waitingForFluid = false;
                localProcessingTicks = -1;
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 获取移液器的转速来计算延迟
            float speed = 64f; // 默认速度
            if (workstation instanceof com.adonis.fluid.block.Pipette.PipetteBlockEntity pipette) {
                speed = Math.abs(pipette.getSpeed());
            }

            // 根据转速计算处理时间
            // 基础时间20tick，根据转速调整：转速越高，处理越快
            float speedMultiplier = 64f / Math.max(1f, speed);
            int baseTime = 20;
            int totalProcessingTime = Math.max(10, Math.round(baseTime * speedMultiplier));

            // 等待流体阶段
            if (waitingForFluid) {
                FluidStack fluid = processor.getHeldFluid();

                ItemStack singleItem = ItemHandlerHelper.copyStackWithSize(transported.stack, 1);
                int required = FillingBySpout.getRequiredAmountForItem(
                        workstation.getLevel(), singleItem, fluid);

                if (!fluid.isEmpty() && required > 0 && required <= fluid.getAmount()) {
                    waitingForFluid = false;
                    localProcessingTicks = totalProcessingTime;
                    processor.notifyProcessingStarted(beltSegmentPos);
                }
                return BeltProcessingBehaviour.ProcessingResult.HOLD;
            }

            // 处理阶段
            if (localProcessingTicks > 0) {
                localProcessingTicks--;

                // 动态计算粒子和加工的时机，保持相对位置
                int particleTick = Math.max(totalProcessingTime / 2, 5);
                int processTick = Math.max(totalProcessingTime / 4, 2);

                // 发送粒子效果
                if (localProcessingTicks == particleTick) {
                    BlockEntity ws = workstationRef.get();
                    if (ws instanceof com.adonis.fluid.block.Pipette.PipetteBlockEntity pipette) {
                        // 使用流体副本，确保即使是最后一次加工也有粒子
                        FluidStack fluidForParticles = pipette.getHeldFluid().copy();
                        pipette.sendBeltProcessingEffects(beltSegmentPos, fluidForParticles);
                    }
                }

                // 执行实际填充
                if (localProcessingTicks == processTick) {
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

                            // 处理下一个物品时也使用动态计算的时间
                            if (!bulk && transported.stack.getCount() > 1) {
                                ItemStack nextSingle = ItemHandlerHelper.copyStackWithSize(transported.stack, 1);
                                int nextRequired = FillingBySpout.getRequiredAmountForItem(level, nextSingle, fluid);

                                if (nextRequired > 0 && nextRequired <= fluid.getAmount()) {
                                    // 重新计算速度（可能已经改变）
                                    if (workstation instanceof com.adonis.fluid.block.Pipette.PipetteBlockEntity pipette) {
                                        speed = Math.abs(pipette.getSpeed());
                                    }
                                    speedMultiplier = 64f / Math.max(1f, speed);
                                    localProcessingTicks = Math.max(10, Math.round(baseTime * speedMultiplier));
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

            processor.notifyProcessingCompleted(beltSegmentPos);

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