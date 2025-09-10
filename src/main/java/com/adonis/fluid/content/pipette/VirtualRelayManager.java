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
            System.out.println("[VirtualRelay] onItemReceived: " + transported.stack);

            BlockEntity workstation = workstationRef.get();
            if (!(workstation instanceof IRemoteFluidProcessor processor)) {
                System.out.println("[VirtualRelay] Workstation is not IRemoteFluidProcessor");
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 检查物品是否可以被填充
            if (!FillingBySpout.canItemBeFilled(workstation.getLevel(), transported.stack)) {
                System.out.println("[VirtualRelay] Item cannot be filled: " + transported.stack);
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 检查是否有流体
            FluidStack fluid = processor.getHeldFluid();
            System.out.println("[VirtualRelay] Current fluid: " + fluid);

            if (fluid.isEmpty()) {
                System.out.println("[VirtualRelay] No fluid, requesting from pipette");
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

            System.out.println("[VirtualRelay] Required fluid: " + required + "mB, Available: " + fluid.getAmount() + "mB");

            if (required == -1 || required > fluid.getAmount()) {
                System.out.println("[VirtualRelay] Not enough fluid");
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 开始处理
            currentlyProcessing = transported;
            localProcessingTicks = FILLING_TIME;
            waitingForFluid = false;

            // 触发移液器动画
            processor.notifyProcessingStarted(beltSegmentPos);

            System.out.println("[VirtualRelay] Starting processing, timer set to " + FILLING_TIME);
            return BeltProcessingBehaviour.ProcessingResult.HOLD;
        }

        private BeltProcessingBehaviour.ProcessingResult whenItemHeld(TransportedItemStack transported,
                                                                      TransportedItemStackHandlerBehaviour handler) {
            if (currentlyProcessing != transported) {
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 检查handler是否有效
            if (handler == null) {
                System.out.println("[VirtualRelay] ERROR: Handler is null!");
                currentlyProcessing = null;
                localProcessingTicks = -1;
                waitingForFluid = false;
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            BlockEntity workstation = workstationRef.get();
            if (!(workstation instanceof IRemoteFluidProcessor processor)) {
                System.out.println("[VirtualRelay] ERROR: Workstation lost!");
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
                System.out.println("[VirtualRelay] Fluid arrived, starting processing");
            }

            // 处理计时
            if (localProcessingTicks > 0) {
                localProcessingTicks--;

                // 在倒计时第5个tick执行填充
                if (localProcessingTicks == 5) {
                    FluidStack fluid = processor.getHeldFluid();
                    Level level = workstation.getLevel();

                    System.out.println("[VirtualRelay] === FILL PROCESS AT TICK 5 ===");
                    System.out.println("[VirtualRelay] Current fluid: " + fluid);
                    System.out.println("[VirtualRelay] Current item stack: " + transported.stack);
                    System.out.println("[VirtualRelay] Item count: " + transported.stack.getCount());

                    // 判断是否批量处理
                    boolean bulk = canProcessInBulk() || transported.stack.getCount() == 1;
                    System.out.println("[VirtualRelay] Processing mode: " + (bulk ? "BULK" : "SINGLE"));

                    // 准备要处理的物品
                    ItemStack toProcess;
                    if (bulk) {
                        toProcess = transported.stack.copy();
                    } else {
                        toProcess = ItemHandlerHelper.copyStackWithSize(transported.stack, 1);
                    }

                    System.out.println("[VirtualRelay] Item to process: " + toProcess + " (count: " + toProcess.getCount() + ")");

                    // 获取流体需求
                    int required = FillingBySpout.getRequiredAmountForItem(level, toProcess, fluid);
                    System.out.println("[VirtualRelay] Required fluid: " + required + "mB, Available: " + fluid.getAmount() + "mB");

                    if (required > 0 && required <= fluid.getAmount()) {
                        // 创建流体副本用于填充
                        FluidStack fluidForFilling = fluid.copy();
                        fluidForFilling.setAmount(required);

                        System.out.println("[VirtualRelay] Calling FillingBySpout.fillItem with:");
                        System.out.println("  - Item: " + toProcess);
                        System.out.println("  - Fluid: " + fluidForFilling);
                        System.out.println("  - Required: " + required);

                        // 执行填充（FillingBySpout.fillItem会消耗传入的ItemStack）
                        ItemStack filledResult = FillingBySpout.fillItem(
                                level, required, toProcess, fluidForFilling);

                        System.out.println("[VirtualRelay] Fill result: " + filledResult);

                        if (!filledResult.isEmpty()) {
                            System.out.println("[VirtualRelay] Fill successful, result: " + filledResult);

                            // 清除风扇处理数据
                            transported.clearFanProcessingData();

                            // 准备输出
                            List<TransportedItemStack> outList = new ArrayList<>();
                            TransportedItemStack resultTransported = transported.copy();
                            resultTransported.stack = filledResult;
                            outList.add(resultTransported);

                            System.out.println("[VirtualRelay] Output list prepared with: " + filledResult);

                            // 从移液器消耗流体
                            fluid.shrink(required);
                            processor.syncFluid(fluid);
                            System.out.println("[VirtualRelay] Fluid consumed, remaining: " + fluid.getAmount() + "mB");

                            System.out.println("[VirtualRelay] Handler type: " + handler.getClass().getName());
                            System.out.println("[VirtualRelay] Handler BE: " + handler.blockEntity);

                            if (bulk) {
                                // 批量处理：完全替换
                                System.out.println("[VirtualRelay] BULK: Replacing entire stack");
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
                                    System.out.println("[VirtualRelay] SINGLE: Creating leftover with " + leftover.stack.getCount() + " items");
                                }

                                System.out.println("[VirtualRelay] SINGLE: Replacing with result and leftover");
                                TransportedItemStackHandlerBehaviour.TransportedResult result =
                                        TransportedItemStackHandlerBehaviour.TransportedResult
                                                .convertToAndLeaveHeld(outList, leftover);
                                handler.handleProcessingOnItem(transported, result);
                            }

                            System.out.println("[VirtualRelay] Handler.handleProcessingOnItem called");

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
                                    System.out.println("[VirtualRelay] Continuing to process next item");
                                    return BeltProcessingBehaviour.ProcessingResult.HOLD;
                                }
                            }
                        } else {
                            System.out.println("[VirtualRelay] ERROR: Fill result was empty!");
                        }
                    } else {
                        System.out.println("[VirtualRelay] ERROR: Cannot process - not enough fluid or invalid requirement");
                    }

                    System.out.println("[VirtualRelay] === END FILL PROCESS ===");
                }

                if (localProcessingTicks > 0) {
                    return BeltProcessingBehaviour.ProcessingResult.HOLD;
                }
            }

            // 处理完成
            System.out.println("[VirtualRelay] Processing completed");
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

                    if (canPlaceRelayAt(level, relayPos)) {
                        VirtualRelay relay = new VirtualRelay(outputPos, workstationPos, level);
                        activeRelays.put(relayPos, relay);
                        relayPositions.add(relayPos);
                        System.out.println("[VirtualRelayManager] Registered relay at " + relayPos + " for belt at " + outputPos);
                    }
                }
            }

            workstationToRelays.put(workstationPos, relayPositions);
        }
    }

    // 注销工作站
    public static void unregisterWorkstation(BlockPos workstationPos) {
        Set<BlockPos> relayPositions = workstationToRelays.remove(workstationPos);
        if (relayPositions != null) {
            relayPositions.forEach(pos -> {
                activeRelays.remove(pos);
                System.out.println("[VirtualRelayManager] Unregistered relay at " + pos);
            });
        }
    }

    // 检查是否可以在指定位置放置中继器
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