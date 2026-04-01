package com.adonis.fluid.content.tap;

import com.adonis.fluid.block.CopperTap.CopperTapBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TapVirtualRelayManager {

    private static final Map<BlockPos, TapVirtualRelay> activeRelays = new ConcurrentHashMap<>();
    private static final Map<BlockPos, BlockPos> tapToRelay = new ConcurrentHashMap<>();

    public static class TapVirtualRelay {
        private final BlockPos beltPos;
        private final BlockPos relayPos;
        private final BlockPos tapPos;
        private WeakReference<CopperTapBlockEntity> tapRef;
        private final BeltProcessingBehaviour processingBehaviour;
        private TransportedItemStack currentlyProcessing;
        private int processingTicks = -1;
        private boolean particlesSent = false;
        private static final int FILLING_TIME = 20;

        public TapVirtualRelay(BlockPos beltPos, BlockPos tapPos, Level level) {
            this.beltPos = beltPos;
            this.relayPos = beltPos.above(2);
            this.tapPos = tapPos;

            BlockEntity be = level.getBlockEntity(tapPos);
            if (be instanceof CopperTapBlockEntity tap) {
                this.tapRef = new WeakReference<>(tap);
            } else {
                this.tapRef = new WeakReference<>(null);
            }

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
            CopperTapBlockEntity tap = tapRef.get();
            if (tap == null || tap.isRemoved()) {
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 检查龙头是否打开
            if (!isTapOpen(tap)) {
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 检查物品是否可以被填充
            Level level = tap.getLevel();
            if (level == null) {
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            if (!FillingBySpout.canItemBeFilled(level, transported.stack)) {
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 获取可用流体
            FluidStack availableFluid = getAvailableFluid(tap);
            if (availableFluid.isEmpty()) {
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 检查是否有足够的流体
            ItemStack singleItem = transported.stack.copy();
            singleItem.setCount(1);
            int required = FillingBySpout.getRequiredAmountForItem(level, singleItem, availableFluid);

            if (required <= 0 || required > availableFluid.getAmount()) {
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 开始处理
            currentlyProcessing = transported;
            processingTicks = FILLING_TIME;
            particlesSent = false;

            // 通知铜龙头开始传送带加工，用于渲染
            tap.startBeltProcessing(beltPos, availableFluid);

            return BeltProcessingBehaviour.ProcessingResult.HOLD;
        }

        private BeltProcessingBehaviour.ProcessingResult whenItemHeld(TransportedItemStack transported,
                                                                      TransportedItemStackHandlerBehaviour handler) {
            if (currentlyProcessing != transported) {
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            CopperTapBlockEntity tap = tapRef.get();
            if (tap == null || tap.isRemoved()) {
                resetState();
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 检查龙头是否关闭了
            if (!isTapOpen(tap)) {
                tap.stopBeltProcessing();
                resetState();
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            Level level = tap.getLevel();
            if (level == null) {
                tap.stopBeltProcessing();
                resetState();
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            if (processingTicks > 0) {
                processingTicks--;

                // 更新铜龙头的处理进度
                tap.updateBeltProcessingTicks(processingTicks);

                // 在中间时刻发送粒子和播放声音
                if (processingTicks == FILLING_TIME / 2 && !particlesSent) {
                    FluidStack fluid = getAvailableFluid(tap);
                    if (!fluid.isEmpty()) {
                        sendFillingEffects(tap, fluid);
                        particlesSent = true;
                    }
                }

                return BeltProcessingBehaviour.ProcessingResult.HOLD;
            }

            // 处理完成，执行实际填充并获取剩余物品（held 是剩余的原物品）
            TransportedItemStack held = performFillingAndGetLeftover(transported, handler, tap);

            // 如果有剩余物品且可以继续处理，立即开始下一个
            if (held != null && !held.stack.isEmpty()) {
                FluidStack availableFluid = getAvailableFluid(tap);
                if (!availableFluid.isEmpty() && FillingBySpout.canItemBeFilled(level, held.stack)) {
                    int required = FillingBySpout.getRequiredAmountForItem(level, held.stack, availableFluid);

                    if (required > 0 && required <= availableFluid.getAmount()) {
                        // 继续处理剩余物品
                        currentlyProcessing = held;
                        processingTicks = FILLING_TIME;
                        particlesSent = false;
                        tap.updateBeltProcessingTicks(processingTicks);
                        return BeltProcessingBehaviour.ProcessingResult.HOLD;
                    }
                }
            }

            tap.stopBeltProcessing();
            resetState();
            return BeltProcessingBehaviour.ProcessingResult.PASS;
        }

        /**
         * 执行填充并返回剩余的 TransportedItemStack
         */
        private TransportedItemStack performFillingAndGetLeftover(TransportedItemStack transported,
                                                                  TransportedItemStackHandlerBehaviour handler,
                                                                  CopperTapBlockEntity tap) {
            Level level = tap.getLevel();
            if (level == null) return null;

            FluidStack availableFluid = getAvailableFluid(tap);
            if (availableFluid.isEmpty()) return null;

            // 与原版注液器逻辑一致：直接传入 transported.stack，让 fillItem 处理数量减少
            int required = FillingBySpout.getRequiredAmountForItem(level, transported.stack, availableFluid);

            if (required <= 0 || required > availableFluid.getAmount()) return null;

            // 实际消耗流体
            FluidStack consumed = consumeFluid(tap, required);
            if (consumed.isEmpty()) return null;

            // 执行填充：直接传入 transported.stack，fillItem 会自动 shrink(1)
            FluidStack fluidForFilling = consumed.copy();
            ItemStack filledResult = FillingBySpout.fillItem(level, required, transported.stack, fluidForFilling);

            if (filledResult.isEmpty()) return null;

            transported.clearFanProcessingData();

            List<TransportedItemStack> outList = new ArrayList<>();
            TransportedItemStack resultTransported = transported.copy();
            resultTransported.stack = filledResult;
            outList.add(resultTransported);

            // 与原版注液器逻辑一致：检查 transported.stack 是否还有剩余
            TransportedItemStack held = null;
            if (!transported.stack.isEmpty()) {
                held = transported.copy();
            }

            TransportedItemStackHandlerBehaviour.TransportedResult result =
                    TransportedItemStackHandlerBehaviour.TransportedResult
                            .convertToAndLeaveHeld(outList, held);
            handler.handleProcessingOnItem(transported, result);

            // 播放完成音效
            level.playSound(null, beltPos,
                    net.minecraft.sounds.SoundEvents.BOTTLE_FILL,
                    net.minecraft.sounds.SoundSource.BLOCKS,
                    0.5f, 1.0f + level.random.nextFloat() * 0.2f);

            return held;
        }

        private boolean isTapOpen(CopperTapBlockEntity tap) {
            if (tap.getLevel() == null) return false;
            BlockState state = tap.getBlockState();
            return state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
        }

        private FluidStack getAvailableFluid(CopperTapBlockEntity tap) {
            Level level = tap.getLevel();
            if (level == null) return FluidStack.EMPTY;

            BlockState tapState = tap.getBlockState();
            if (!tapState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                return FluidStack.EMPTY;
            }

            Direction facing = tapState.getValue(BlockStateProperties.HORIZONTAL_FACING);
            BlockPos sourcePos = tapPos.relative(facing.getOpposite());
            BlockState sourceState = level.getBlockState(sourcePos);

            // 检查含水树叶
            if (sourceState.is(BlockTags.LEAVES)) {
                if (sourceState.hasProperty(BlockStateProperties.WATERLOGGED) &&
                        sourceState.getValue(BlockStateProperties.WATERLOGGED)) {
                    return new FluidStack(Fluids.WATER, 1000);
                }
                return FluidStack.EMPTY;
            }

            // 检查流体容器
            BlockEntity sourceEntity = level.getBlockEntity(sourcePos);
            if (sourceEntity == null) return FluidStack.EMPTY;

            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, sourcePos, facing);
            if (handler == null) {
                handler = level.getCapability(Capabilities.FluidHandler.BLOCK, sourcePos, null);
            }

            if (handler == null) return FluidStack.EMPTY;

            return handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        }

        private FluidStack consumeFluid(CopperTapBlockEntity tap, int amount) {
            Level level = tap.getLevel();
            if (level == null) return FluidStack.EMPTY;

            BlockState tapState = tap.getBlockState();
            if (!tapState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                return FluidStack.EMPTY;
            }

            Direction facing = tapState.getValue(BlockStateProperties.HORIZONTAL_FACING);
            BlockPos sourcePos = tapPos.relative(facing.getOpposite());
            BlockState sourceState = level.getBlockState(sourcePos);

            // 含水树叶是无限水源
            if (sourceState.is(BlockTags.LEAVES)) {
                if (sourceState.hasProperty(BlockStateProperties.WATERLOGGED) &&
                        sourceState.getValue(BlockStateProperties.WATERLOGGED)) {
                    return new FluidStack(Fluids.WATER, amount);
                }
                return FluidStack.EMPTY;
            }

            // 从流体容器消耗
            BlockEntity sourceEntity = level.getBlockEntity(sourcePos);
            if (sourceEntity == null) return FluidStack.EMPTY;

            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, sourcePos, facing);
            if (handler == null) {
                handler = level.getCapability(Capabilities.FluidHandler.BLOCK, sourcePos, null);
            }

            if (handler == null) return FluidStack.EMPTY;

            return handler.drain(amount, IFluidHandler.FluidAction.EXECUTE);
        }

        private void sendFillingEffects(CopperTapBlockEntity tap, FluidStack fluid) {
            Level level = tap.getLevel();
            if (level == null || level.isClientSide || fluid.isEmpty()) return;

            Vec3 startPos = Vec3.atCenterOf(tapPos).add(0, -0.25, 0);
            Vec3 endPos = Vec3.atCenterOf(beltPos).add(0, 0.5, 0);

            com.adonis.fluid.registry.CFNetworking.sendToPlayersTrackingChunk(
                    (net.minecraft.server.level.ServerLevel) level,
                    level.getChunkAt(beltPos).getPos(),
                    new com.adonis.fluid.packet.CopperTapParticlePacket(
                            com.adonis.fluid.packet.CopperTapParticlePacket.ParticleType.STREAM,
                            startPos, endPos, fluid
                    )
            );

            // 播放注液声音
            com.simibubi.create.AllSoundEvents.SPOUTING.playOnServer(
                    level, tapPos, 0.75f, 0.9f + 0.2f * level.random.nextFloat());
        }

        private void resetState() {
            currentlyProcessing = null;
            processingTicks = -1;
            particlesSent = false;
        }

        /**
         * 检查是否正在处理物品，同时验证物品是否还存在
         */
        public boolean isProcessing() {
            if (currentlyProcessing == null || processingTicks < 0) {
                return false;
            }

            // 验证物品是否还在传送带上
            CopperTapBlockEntity tap = tapRef.get();
            if (tap == null || tap.isRemoved() || tap.getLevel() == null) {
                resetState();
                return false;
            }

            Level level = tap.getLevel();
            BlockEntity beltEntity = level.getBlockEntity(beltPos);
            if (!(beltEntity instanceof BeltBlockEntity beltBE)) {
                resetState();
                return false;
            }

            var inventory = beltBE.getInventory();
            if (inventory == null) {
                resetState();
                return false;
            }

            // 直接检查引用是否还存在于传送带物品列表中
            for (TransportedItemStack item : inventory.getTransportedItems()) {
                if (item == currentlyProcessing) {
                    return true;
                }
            }

            // 物品不在了，停止处理
            tap.stopBeltProcessing();
            resetState();
            return false;
        }

        public boolean isValid() {
            CopperTapBlockEntity tap = tapRef.get();
            return tap != null && !tap.isRemoved();
        }

        public BlockPos getBeltPos() {
            return beltPos;
        }

        public BlockPos getTapPos() {
            return tapPos;
        }
    }

    /**
     * 注册铜龙头的虚拟中继器
     */
    public static void registerTap(BlockPos tapPos, Level level) {
        if (level.isClientSide) return;

        // 铜龙头下方是传送带位置
        BlockPos beltPos = tapPos.below();
        // 虚拟中继器在传送带上方2格（与注液器位置对应）
        BlockPos relayPos = beltPos.above(2);

        // 检查是否已经注册
        if (activeRelays.containsKey(relayPos)) {
            return;
        }

        TapVirtualRelay relay = new TapVirtualRelay(beltPos, tapPos, level);
        activeRelays.put(relayPos, relay);
        tapToRelay.put(tapPos, relayPos);
    }

    /**
     * 注销铜龙头的虚拟中继器
     */
    public static void unregisterTap(BlockPos tapPos) {
        BlockPos relayPos = tapToRelay.remove(tapPos);
        if (relayPos != null) {
            activeRelays.remove(relayPos);
        }
    }

    /**
     * 获取指定位置的虚拟中继器
     */
    public static TapVirtualRelay getRelayAt(BlockPos pos) {
        return activeRelays.get(pos);
    }

    /**
     * 检查指定位置是否有虚拟中继器
     */
    public static boolean hasRelayAt(BlockPos pos) {
        return activeRelays.containsKey(pos);
    }

    /**
     * 清理无效的中继器
     */
    public static void cleanup() {
        activeRelays.entrySet().removeIf(entry -> !entry.getValue().isValid());
        tapToRelay.entrySet().removeIf(entry -> !activeRelays.containsKey(entry.getValue()));
    }
}
