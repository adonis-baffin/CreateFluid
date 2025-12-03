package com.adonis.fluid.content.tap;

import com.adonis.fluid.block.CopperTap.CopperTapBlockEntity;
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
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.ItemHandlerHelper;

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

            System.out.println("[TapRelay] Creating TapVirtualRelay");
            System.out.println("[TapRelay]   beltPos: " + beltPos);
            System.out.println("[TapRelay]   relayPos: " + relayPos);
            System.out.println("[TapRelay]   tapPos: " + tapPos);

            BlockEntity be = level.getBlockEntity(tapPos);
            if (be instanceof CopperTapBlockEntity tap) {
                this.tapRef = new WeakReference<>(tap);
                System.out.println("[TapRelay]   tapRef: OK");
            } else {
                this.tapRef = new WeakReference<>(null);
                System.out.println("[TapRelay]   tapRef: FAILED (be=" + be + ")");
            }

            this.processingBehaviour = new BeltProcessingBehaviour(null) {
                @Override
                public ProcessingResult handleReceivedItem(TransportedItemStack transported,
                                                           TransportedItemStackHandlerBehaviour handler) {
                    System.out.println("[TapRelay] >>> handleReceivedItem called! <<<");
                    return onItemReceived(transported, handler);
                }

                @Override
                public ProcessingResult handleHeldItem(TransportedItemStack transported,
                                                       TransportedItemStackHandlerBehaviour handler) {
                    System.out.println("[TapRelay] >>> handleHeldItem called! <<<");
                    return whenItemHeld(transported, handler);
                }
            };

            System.out.println("[TapRelay]   processingBehaviour created: " + this.processingBehaviour);
        }

        public BeltProcessingBehaviour getProcessingBehaviour() {
            return this.processingBehaviour;
        }

        private BeltProcessingBehaviour.ProcessingResult onItemReceived(TransportedItemStack transported,
                                                                        TransportedItemStackHandlerBehaviour handler) {
            System.out.println("[TapRelay] ===== onItemReceived =====");
            System.out.println("[TapRelay] Item: " + transported.stack.getItem().toString());

            CopperTapBlockEntity tap = tapRef.get();
            if (tap == null || tap.isRemoved()) {
                System.out.println("[TapRelay] FAIL: Tap is null or removed");
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 检查龙头是否打开
            if (!isTapOpen(tap)) {
                System.out.println("[TapRelay] FAIL: Tap is closed");
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }
            System.out.println("[TapRelay] Tap is open: OK");

            // 检查物品是否可以被填充
            Level level = tap.getLevel();
            if (level == null) {
                System.out.println("[TapRelay] FAIL: Level is null");
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            boolean canBeFilled = FillingBySpout.canItemBeFilled(level, transported.stack);
            System.out.println("[TapRelay] Can item be filled: " + canBeFilled);
            if (!canBeFilled) {
                System.out.println("[TapRelay] FAIL: Item cannot be filled");
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 获取可用流体
            FluidStack availableFluid = getAvailableFluid(tap);
            System.out.println("[TapRelay] Available fluid: " + (availableFluid.isEmpty() ? "EMPTY" : availableFluid.getFluid().toString() + " x" + availableFluid.getAmount()));
            if (availableFluid.isEmpty()) {
                System.out.println("[TapRelay] FAIL: No fluid available");
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 检查是否有足够的流体
            ItemStack singleItem = ItemHandlerHelper.copyStackWithSize(transported.stack, 1);
            int required = FillingBySpout.getRequiredAmountForItem(level, singleItem, availableFluid);
            System.out.println("[TapRelay] Required amount: " + required);

            if (required <= 0) {
                System.out.println("[TapRelay] FAIL: Required amount <= 0 (no recipe?)");
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            if (required > availableFluid.getAmount()) {
                System.out.println("[TapRelay] FAIL: Not enough fluid (need " + required + ", have " + availableFluid.getAmount() + ")");
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 开始处理
            currentlyProcessing = transported;
            processingTicks = FILLING_TIME;
            particlesSent = false;

            System.out.println("[TapRelay] SUCCESS: Starting processing, will take " + FILLING_TIME + " ticks");
            return BeltProcessingBehaviour.ProcessingResult.HOLD;
        }

        private BeltProcessingBehaviour.ProcessingResult whenItemHeld(TransportedItemStack transported,
                                                                      TransportedItemStackHandlerBehaviour handler) {
            System.out.println("[TapRelay] ===== whenItemHeld =====");
            System.out.println("[TapRelay] Processing ticks remaining: " + processingTicks);

            if (currentlyProcessing != transported) {
                System.out.println("[TapRelay] PASS: Not the item we're processing");
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            CopperTapBlockEntity tap = tapRef.get();
            if (tap == null || tap.isRemoved()) {
                System.out.println("[TapRelay] RESET: Tap is null or removed");
                resetState();
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            // 检查龙头是否关闭了
            if (!isTapOpen(tap)) {
                System.out.println("[TapRelay] RESET: Tap was closed");
                resetState();
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            Level level = tap.getLevel();
            if (level == null) {
                System.out.println("[TapRelay] RESET: Level is null");
                resetState();
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            if (processingTicks > 0) {
                processingTicks--;

                // 在中间时刻发送粒子和播放声音
                if (processingTicks == FILLING_TIME / 2 && !particlesSent) {
                    FluidStack fluid = getAvailableFluid(tap);
                    if (!fluid.isEmpty()) {
                        System.out.println("[TapRelay] Sending filling effects");
                        sendFillingEffects(tap, fluid);
                        particlesSent = true;
                    }
                }

                System.out.println("[TapRelay] HOLD: Still processing (" + processingTicks + " ticks left)");
                return BeltProcessingBehaviour.ProcessingResult.HOLD;
            }

            // 处理完成，执行实际填充
            System.out.println("[TapRelay] Processing complete, performing filling");
            performFilling(transported, handler, tap);
            resetState();

            return BeltProcessingBehaviour.ProcessingResult.PASS;
        }

        private void performFilling(TransportedItemStack transported,
                                    TransportedItemStackHandlerBehaviour handler,
                                    CopperTapBlockEntity tap) {
            Level level = tap.getLevel();
            if (level == null) return;

            FluidStack availableFluid = getAvailableFluid(tap);
            if (availableFluid.isEmpty()) return;

            ItemStack toProcess = ItemHandlerHelper.copyStackWithSize(transported.stack, 1);
            int required = FillingBySpout.getRequiredAmountForItem(level, toProcess, availableFluid);

            if (required <= 0 || required > availableFluid.getAmount()) return;

            // 实际消耗流体
            FluidStack consumed = consumeFluid(tap, required);
            if (consumed.isEmpty()) return;

            // 执行填充
            FluidStack fluidForFilling = consumed.copy();
            ItemStack filledResult = FillingBySpout.fillItem(level, required, toProcess, fluidForFilling);

            if (!filledResult.isEmpty()) {
                transported.clearFanProcessingData();

                List<TransportedItemStack> outList = new ArrayList<>();
                TransportedItemStack resultTransported = transported.copy();
                resultTransported.stack = filledResult;
                outList.add(resultTransported);

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

                // 播放完成音效
                level.playSound(null, beltPos,
                        net.minecraft.sounds.SoundEvents.BOTTLE_FILL,
                        net.minecraft.sounds.SoundSource.BLOCKS,
                        0.5f, 1.0f + level.random.nextFloat() * 0.2f);
            }
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

            IFluidHandler handler = sourceEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, facing)
                    .orElse(sourceEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, null)
                            .orElse(null));

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

            IFluidHandler handler = sourceEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, facing)
                    .orElse(sourceEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, null)
                            .orElse(null));

            if (handler == null) return FluidStack.EMPTY;

            return handler.drain(amount, IFluidHandler.FluidAction.EXECUTE);
        }

        private void sendFillingEffects(CopperTapBlockEntity tap, FluidStack fluid) {
            Level level = tap.getLevel();
            if (level == null || level.isClientSide || fluid.isEmpty()) return;

            Vec3 startPos = Vec3.atCenterOf(tapPos).add(0, -0.25, 0);
            Vec3 endPos = Vec3.atCenterOf(beltPos).add(0, 0.5, 0);

            com.simibubi.create.AllPackets.getChannel().send(
                    net.minecraftforge.network.PacketDistributor.TRACKING_CHUNK.with(
                            () -> level.getChunkAt(beltPos)
                    ),
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
        // 虚拟中继器在传送带上方2格
        BlockPos relayPos = beltPos.above(2);

        // ===== 调试输出 =====
        System.out.println("[TapRelay] ========================================");
        System.out.println("[TapRelay] Registering tap at: " + tapPos.toShortString());
        System.out.println("[TapRelay] Belt pos (tap-1): " + beltPos.toShortString());
        System.out.println("[TapRelay] Relay pos (belt+2): " + relayPos.toShortString());

        // 检查是否已经注册
        if (activeRelays.containsKey(relayPos)) {
            System.out.println("[TapRelay] Already registered at " + relayPos.toShortString());
            return;
        }

        TapVirtualRelay relay = new TapVirtualRelay(beltPos, tapPos, level);
        activeRelays.put(relayPos, relay);
        tapToRelay.put(tapPos, relayPos);

        System.out.println("[TapRelay] Successfully registered!");
        System.out.println("[TapRelay] Total active relays: " + activeRelays.size());
        System.out.println("[TapRelay] All relay positions: " + getAllRelayPositions());
    }

    /**
     * 注销铜龙头的虚拟中继器
     */
    public static void unregisterTap(BlockPos tapPos) {
        System.out.println("[TapRelay] Unregistering tap at: " + tapPos.toShortString());

        BlockPos relayPos = tapToRelay.remove(tapPos);
        if (relayPos != null) {
            activeRelays.remove(relayPos);
            System.out.println("[TapRelay] Removed relay at: " + relayPos.toShortString());
        } else {
            System.out.println("[TapRelay] No relay found for tap at: " + tapPos.toShortString());
        }

        System.out.println("[TapRelay] Remaining relays: " + activeRelays.size());
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

    /**
     * 调试用：获取所有已注册的中继器位置
     */
    public static String getAllRelayPositions() {
        if (activeRelays.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<BlockPos, TapVirtualRelay> entry : activeRelays.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(entry.getKey().toShortString());
            sb.append(" (tap at ");
            sb.append(entry.getValue().getTapPos().toShortString());
            sb.append(")");
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }
}