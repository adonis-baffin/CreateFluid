package com.adonis.fluid.content.pipette;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPackets;
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
import net.minecraftforge.network.PacketDistributor;

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

            // 检查流体是否足够
            int required = FillingBySpout.getRequiredAmountForItem(
                    workstation.getLevel(), transported.stack, fluid);

            if (required == -1 || required > fluid.getAmount()) {
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 开始处理
            currentlyProcessing = transported;
            localProcessingTicks = FILLING_TIME;
            waitingForFluid = false;

            // 触发移液器动画
            if (processor instanceof com.adonis.fluid.block.Pipette.PipetteBlockEntity pipette) {
                pipette.startBeltProcessing(beltSegmentPos);
            }

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

                int required = FillingBySpout.getRequiredAmountForItem(
                        workstation.getLevel(), transported.stack, fluid);

                if (required == -1 || required > fluid.getAmount()) {
                    currentlyProcessing = null;
                    waitingForFluid = false;
                    return BeltProcessingBehaviour.ProcessingResult.PASS;
                }

                // 开始处理
                waitingForFluid = false;
                localProcessingTicks = FILLING_TIME;

                // 触发移液器动画
                if (processor instanceof com.adonis.fluid.block.Pipette.PipetteBlockEntity pipette) {
                    pipette.startBeltProcessing(beltSegmentPos);
                }
            }

            // 处理计时
            if (localProcessingTicks > 0) {
                localProcessingTicks--;

                // 在倒计时第5个tick执行填充（参考SpoutBlockEntity）
                if (localProcessingTicks == 5) {
                    FluidStack fluid = processor.getHeldFluid();

                    // 检查是否批量处理
                    boolean bulk = canProcessInBulk() || transported.stack.getCount() == 1;

                    // 准备要处理的物品
                    ItemStack toProcess = bulk ? transported.stack :
                            ItemHandlerHelper.copyStackWithSize(transported.stack, 1);

                    int required = FillingBySpout.getRequiredAmountForItem(
                            workstation.getLevel(), toProcess, fluid);

                    if (required > 0 && required <= fluid.getAmount()) {
                        // 执行填充
                        ItemStack out = FillingBySpout.fillItem(
                                workstation.getLevel(), required, toProcess, fluid);

                        if (!out.isEmpty()) {
                            // 清除风扇处理数据
                            transported.clearFanProcessingData();

                            List<TransportedItemStack> outList = new ArrayList<>();

                            // 创建输出物品
                            TransportedItemStack result = transported.copy();
                            result.stack = out;
                            outList.add(result);

                            if (bulk) {
                                // 批量处理 - 替换整个堆
                                handler.handleProcessingOnItem(transported,
                                        TransportedItemStackHandlerBehaviour.TransportedResult.convertTo(outList));
                            } else {
                                // 逐个处理 - 保留剩余物品
                                TransportedItemStack left = transported.copy();
                                left.stack.shrink(1);

                                if (left.stack.isEmpty()) {
                                    handler.handleProcessingOnItem(transported,
                                            TransportedItemStackHandlerBehaviour.TransportedResult.convertTo(outList));
                                } else {
                                    handler.handleProcessingOnItem(transported,
                                            TransportedItemStackHandlerBehaviour.TransportedResult.convertToAndLeaveHeld(outList, left));
                                }
                            }

                            // 同步流体
                            processor.syncFluid(fluid);

                            // 发送粒子效果
                            if (processor instanceof com.adonis.fluid.block.Pipette.PipetteBlockEntity pipette) {
                                pipette.sendBeltProcessingEffects(beltSegmentPos, fluid);
                            }
                        }
                    }
                }

                return BeltProcessingBehaviour.ProcessingResult.HOLD;
            }

            // 处理完成
            currentlyProcessing = null;
            localProcessingTicks = -1;

            // 通知移液器
            if (processor instanceof com.adonis.fluid.block.Pipette.PipetteBlockEntity pipette) {
                pipette.finishBeltProcessing(beltSegmentPos);
            }

            return BeltProcessingBehaviour.ProcessingResult.PASS;
        }

        private boolean canProcessInBulk() {
            // 可以配置是否批量处理
            return false; // 默认逐个处理
        }

        private void applyToItems(float maxDistance,
                                  java.util.function.Function<TransportedItemStack,
                                          TransportedItemStackHandlerBehaviour.TransportedResult> processFunction) {
            // 处理物品的回调 - 虚拟中继器无需实际应用到所有物品，因此为空实现
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

    public static void registerWorkstation(BlockPos workstationPos, Level level, int range) {
        if (!(level.getBlockEntity(workstationPos) instanceof IRemoteFluidProcessor processor)) {
            return;
        }

        // 获取移液器的交互点
        if (processor instanceof com.adonis.fluid.block.Pipette.PipetteBlockEntity pipette) {
            Set<BlockPos> relayPositions = new HashSet<>();

            // 只为配置为输出端的传送带创建中继器
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