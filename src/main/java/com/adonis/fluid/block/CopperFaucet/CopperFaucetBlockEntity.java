package com.adonis.fluid.block.CopperFaucet;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public class CopperFaucetBlockEntity extends SmartBlockEntity {

    // 流体缓存，用于渲染（只在实际传输时才有值）
    private FluidStack renderingFluid = FluidStack.EMPTY;

    // 注液进度
    private int processingTicks = 0;
    private static final int FILLING_TIME = 20;

    // 填充参数
    private static final int TRANSFER_RATE = 250; // 每次最多传输250mb
    private static final int TRANSFER_INTERVAL = 10; // 每10tick传输一次
    private int transferCooldown = 0;

    // 正在处理的物品
    private ItemStack processingItem = ItemStack.EMPTY;
    private boolean isFillingItem = false; // 标记是否正在注液

    // 待消耗的流体信息（延迟消耗）
    private FluidStack pendingFluid = FluidStack.EMPTY;
    private Direction sourceDirection = null;
    private BlockPos sourceBlockPos = null;

    // 标签
    private static final TagKey<Block> FAUCET_FILLABLE = TagKey.create(
            ForgeRegistries.BLOCKS.getRegistryKey(),
            new ResourceLocation("fluid", "faucet_fillable")
    );

    public CopperFaucetBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // 可以添加额外的行为
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide)
            return;

        BlockState state = getBlockState();
        boolean isOpen = state.getValue(BlockStateProperties.OPEN);

        if (!isOpen) {
            // 关闭状态，清空渲染缓存和待处理的流体
            if (!renderingFluid.isEmpty() || !pendingFluid.isEmpty()) {
                renderingFluid = FluidStack.EMPTY;
                pendingFluid = FluidStack.EMPTY;
                isFillingItem = false;
                processingTicks = 0;
                processingItem = ItemStack.EMPTY;
                sourceDirection = null;
                sourceBlockPos = null;
                notifyUpdate();
            }
            transferCooldown = 0;
            return;
        }

        // 开启状态，执行填充逻辑

        // 处理冷却时间
        if (transferCooldown > 0) {
            transferCooldown--;
        }

        // 处理注液进度
        if (isFillingItem && processingTicks > 0) {
            processingTicks--;

            // 每tick检查物品是否还在
            if (!validateItemStillPresent()) {
                // 物品不在了，取消注液
                cancelItemFilling();
                return;
            }

            if (processingTicks == 0) {
                // 注液完成，消耗流体并生成结果
                finishItemFilling();
            }
            return; // 注液期间不做其他事
        }

        // 只在冷却完成后尝试新的传输
        if (transferCooldown == 0) {
            tryTransferFluid();
        }
    }

    private boolean validateItemStillPresent() {
        if (processingItem.isEmpty())
            return false;

        BlockPos targetPos = worldPosition.below();
        BlockEntity targetEntity = level.getBlockEntity(targetPos);

        if (targetEntity == null || !isDepot(targetEntity))
            return false;

        ItemStack currentItem = getItemOnDepot(targetEntity);

        // 检查物品是否还是同一个（类型和数量）
        return ItemStack.isSameItemSameTags(currentItem, processingItem) &&
                currentItem.getCount() >= processingItem.getCount();
    }

    private void cancelItemFilling() {
        // 取消注液，不消耗流体
        isFillingItem = false;
        processingTicks = 0;
        processingItem = ItemStack.EMPTY;
        renderingFluid = FluidStack.EMPTY;
        pendingFluid = FluidStack.EMPTY;
        sourceDirection = null;
        sourceBlockPos = null;

        // 播放取消的声音效果
        level.playSound(null, worldPosition,
                net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
                net.minecraft.sounds.SoundSource.BLOCKS,
                0.25f, 2.0f);

        notifyUpdate();
    }

    private void tryTransferFluid() {
        // 获取源容器
        Direction attached = getBlockState().getValue(CopperFaucetBlock.FACING);
        BlockPos sourcePos = worldPosition.relative(attached.getOpposite());
        BlockEntity sourceEntity = level.getBlockEntity(sourcePos);

        if (sourceEntity == null) {
            closeFaucet();
            return;
        }

        // 获取源流体处理器
        IFluidHandler sourceHandler = sourceEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, attached)
                .orElse(sourceEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null));

        if (sourceHandler == null) {
            closeFaucet();
            return;
        }

        // 检查下方
        BlockPos targetPos = worldPosition.below();

        // 尝试处理
        boolean success = tryProcess(sourceHandler, targetPos, attached, sourcePos);

        if (success) {
            transferCooldown = TRANSFER_INTERVAL;
        } else {
            // 没有成功传输，清空渲染
            if (!renderingFluid.isEmpty()) {
                renderingFluid = FluidStack.EMPTY;
                notifyUpdate();
            }
        }
    }

    private boolean tryProcess(IFluidHandler sourceHandler, BlockPos targetPos, Direction sourceDir, BlockPos sourcePos) {
        BlockEntity targetEntity = level.getBlockEntity(targetPos);
        BlockState targetState = level.getBlockState(targetPos);

        // 优先检查置物台上的物品
        if (targetEntity != null && isDepot(targetEntity)) {
            ItemStack itemOnDepot = getItemOnDepot(targetEntity);
            if (!itemOnDepot.isEmpty() && FillingBySpout.canItemBeFilled(level, itemOnDepot)) {
                return startItemFilling(sourceHandler, targetPos, itemOnDepot, sourceDir, sourcePos);
            }
            return false;
        }

        // 检查炼药锅
        if (targetState.is(Blocks.CAULDRON)) {
            return tryFillCauldron(sourceHandler, targetPos, targetState);
        }

        // 检查其他容器
        if (targetState.is(FAUCET_FILLABLE) && targetEntity != null) {
            return tryFillContainer(sourceHandler, targetEntity);
        }

        return false;
    }

    private boolean tryFillCauldron(IFluidHandler sourceHandler, BlockPos targetPos, BlockState targetState) {
        // 只处理空炼药锅
        if (!targetState.is(Blocks.CAULDRON)) {
            return false;
        }

        // 获取可用流体
        FluidStack availableFluid = sourceHandler.drain(1000, IFluidHandler.FluidAction.SIMULATE);
        if (availableFluid.isEmpty()) {
            return false;
        }

        // 获取对应的炼药锅信息
        var cauldronInfo = com.simibubi.create.api.behaviour.spouting.CauldronSpoutingBehavior
                .CAULDRON_INFO.get(availableFluid.getFluid());

        if (cauldronInfo == null) {
            return false;
        }

        // 检查流体量是否足够
        if (availableFluid.getAmount() < cauldronInfo.amount()) {
            return false;
        }

        // 执行实际抽取（炼药锅立即消耗）
        FluidStack drained = sourceHandler.drain(cauldronInfo.amount(), IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty() || drained.getAmount() < cauldronInfo.amount()) {
            return false;
        }

        // 设置炼药锅状态
        level.setBlockAndUpdate(targetPos, cauldronInfo.cauldron());

        // 设置渲染流体
        renderingFluid = drained.copy();

        // 播放声音
        level.playSound(null, targetPos,
                net.minecraft.sounds.SoundEvents.BUCKET_EMPTY,
                net.minecraft.sounds.SoundSource.BLOCKS,
                0.5f, 1.0f);

        // 发送粒子效果
        sendFillingParticles(targetPos, drained);

        notifyUpdate();
        return true;
    }

    private boolean startItemFilling(IFluidHandler sourceHandler, BlockPos targetPos, ItemStack item,
                                     Direction sourceDir, BlockPos sourcePos) {
        // 获取可用流体
        FluidStack availableFluid = sourceHandler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (availableFluid.isEmpty())
            return false;

        // 检查需要的量
        int requiredAmount = FillingBySpout.getRequiredAmountForItem(level, item, availableFluid);
        if (requiredAmount <= 0 || requiredAmount > availableFluid.getAmount())
            return false;

        // 只是模拟抽取，不实际消耗
        FluidStack simulatedDrain = sourceHandler.drain(requiredAmount, IFluidHandler.FluidAction.SIMULATE);
        if (simulatedDrain.isEmpty() || simulatedDrain.getAmount() < requiredAmount)
            return false;

        // 设置注液状态，但不消耗流体
        isFillingItem = true;
        processingTicks = FILLING_TIME;
        processingItem = item.copy();
        pendingFluid = simulatedDrain.copy(); // 保存待消耗的流体信息
        renderingFluid = simulatedDrain.copy(); // 用于渲染
        sourceDirection = sourceDir; // 保存源方向
        sourceBlockPos = sourcePos.immutable(); // 保存源位置

        // 播放声音
        AllSoundEvents.SPOUTING.playOnServer(level, worldPosition, 0.75f, 0.9f + 0.2f * level.random.nextFloat());

        // 发送粒子效果
        sendFillingParticles(targetPos, simulatedDrain);

        notifyUpdate();
        return true;
    }

    private void sendFillingParticles(BlockPos targetPos, FluidStack fluid) {
        if (level.isClientSide || fluid.isEmpty())
            return;

        // 计算粒子位置：从龙头底部到目标顶部
        Vec3 startPos = Vec3.atCenterOf(worldPosition).add(0, -0.25, 0);
        Vec3 endPos = Vec3.atCenterOf(targetPos).add(0, 0.5, 0);

        // 发送粒子包
        com.simibubi.create.AllPackets.getChannel().send(
                net.minecraftforge.network.PacketDistributor.TRACKING_CHUNK.with(
                        () -> level.getChunkAt(targetPos)
                ),
                new com.adonis.fluid.packet.CopperFaucetParticlePacket(startPos, endPos, fluid)
        );
    }

    private void finishItemFilling() {
        if (!isFillingItem || processingItem.isEmpty() || pendingFluid.isEmpty())
            return;

        // 找到置物台
        BlockPos targetPos = worldPosition.below();
        BlockEntity targetEntity = level.getBlockEntity(targetPos);

        if (targetEntity != null && isDepot(targetEntity)) {
            // 再次验证物品
            ItemStack currentItem = getItemOnDepot(targetEntity);
            if (!ItemStack.isSameItemSameTags(currentItem, processingItem) ||
                    currentItem.getCount() < processingItem.getCount()) {
                // 物品已改变，取消
                cancelItemFilling();
                return;
            }

            // 现在尝试实际消耗流体
            boolean fluidConsumed = false;
            if (sourceBlockPos != null && sourceDirection != null) {
                BlockEntity sourceEntity = level.getBlockEntity(sourceBlockPos);
                if (sourceEntity != null) {
                    IFluidHandler sourceHandler = sourceEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, sourceDirection)
                            .orElse(sourceEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null));

                    if (sourceHandler != null) {
                        // 实际消耗流体
                        FluidStack drained = sourceHandler.drain(pendingFluid, IFluidHandler.FluidAction.EXECUTE);
                        if (!drained.isEmpty() && drained.getAmount() >= pendingFluid.getAmount()) {
                            fluidConsumed = true;
                        }
                    }
                }
            }

            if (!fluidConsumed) {
                // 无法消耗流体，取消注液
                cancelItemFilling();
                return;
            }

            // 获取 DepotBehaviour
            var behaviour = com.simibubi.create.content.logistics.depot.DepotBehaviour.get(
                    targetEntity, com.simibubi.create.content.logistics.depot.DepotBehaviour.TYPE);

            if (behaviour != null) {
                ItemStack itemOnDepot = behaviour.getHeldItemStack();

                // 确保物品还在
                if (!itemOnDepot.isEmpty()) {
                    // 执行填充
                    ItemStack result = FillingBySpout.fillItem(level, pendingFluid.getAmount(),
                            itemOnDepot.copy(), pendingFluid);

                    if (!result.isEmpty()) {
                        // 消耗原物品
                        itemOnDepot.shrink(1);

                        if (itemOnDepot.isEmpty()) {
                            behaviour.removeHeldItem();
                        } else {
                            var updatedStack = new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(itemOnDepot);
                            updatedStack.beltPosition = 0.5f;
                            updatedStack.prevBeltPosition = 0.5f;
                            behaviour.setCenteredHeldItem(updatedStack);
                        }

                        // 使用反射访问内部缓冲区
                        try {
                            java.lang.reflect.Field bufferField = com.simibubi.create.content.logistics.depot.DepotBehaviour.class.getDeclaredField("processingOutputBuffer");
                            bufferField.setAccessible(true);
                            net.minecraftforge.items.ItemStackHandler outputBuffer =
                                    (net.minecraftforge.items.ItemStackHandler) bufferField.get(behaviour);

                            ItemStack remainder = result.copy();
                            for (int slot = 0; slot < outputBuffer.getSlots() && !remainder.isEmpty(); slot++) {
                                remainder = outputBuffer.insertItem(slot, remainder, false);
                            }

                            // 如果缓冲区满了，掉落多余的物品
                            if (!remainder.isEmpty()) {
                                Vec3 dropPos = Vec3.atCenterOf(targetPos);
                                net.minecraft.world.Containers.dropItemStack(
                                        level,
                                        dropPos.x,
                                        dropPos.y + 0.5,
                                        dropPos.z,
                                        remainder
                                );
                            }
                        } catch (Exception e) {
                            // 反射失败，直接设置结果
                            var newTIS = new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(result);
                            newTIS.beltPosition = 0.5f;
                            newTIS.prevBeltPosition = 0.5f;
                            behaviour.setCenteredHeldItem(newTIS);
                        }

                        targetEntity.setChanged();

                        // 播放完成声音
                        level.playSound(null, targetPos,
                                net.minecraft.sounds.SoundEvents.BOTTLE_FILL,
                                net.minecraft.sounds.SoundSource.BLOCKS,
                                0.5f, 1.0f + level.random.nextFloat() * 0.2f);

                        // 发送粒子效果
                        sendFillingParticles(targetPos, renderingFluid);
                    }
                }
            }
        }

        // 清理状态
        isFillingItem = false;
        processingTicks = 0;
        processingItem = ItemStack.EMPTY;
        renderingFluid = FluidStack.EMPTY;
        pendingFluid = FluidStack.EMPTY;
        sourceDirection = null;
        sourceBlockPos = null;
        notifyUpdate();
    }

    private boolean tryFillContainer(IFluidHandler sourceHandler, BlockEntity targetEntity) {
        // 获取目标流体处理器
        IFluidHandler targetHandler = targetEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP)
                .orElse(targetEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null));

        if (targetHandler == null)
            return false;

        // 模拟抽取
        FluidStack drain = sourceHandler.drain(TRANSFER_RATE, IFluidHandler.FluidAction.SIMULATE);
        if (drain.isEmpty())
            return false;

        // 模拟填充
        int filled = targetHandler.fill(drain, IFluidHandler.FluidAction.SIMULATE);
        if (filled <= 0)
            return false;

        // 执行实际传输（容器直接传输）
        FluidStack actualDrain = sourceHandler.drain(filled, IFluidHandler.FluidAction.EXECUTE);
        if (actualDrain.isEmpty())
            return false;

        targetHandler.fill(actualDrain, IFluidHandler.FluidAction.EXECUTE);

        // 设置渲染流体（只在实际传输时）
        renderingFluid = actualDrain.copy();

        // 播放声音
        if (level.random.nextFloat() < 0.1f) {
            AllSoundEvents.SPOUTING.playOnServer(level, worldPosition, 0.3f, 0.9f + 0.2f * level.random.nextFloat());
        }

        // 发送粒子效果
        sendFillingParticles(targetEntity.getBlockPos(), actualDrain);

        notifyUpdate();
        return true;
    }

    private boolean isDepot(BlockEntity entity) {
        // 检查是否是置物台或类似的方块
        return entity.getClass().getSimpleName().toLowerCase().contains("depot");
    }

    private ItemStack getItemOnDepot(BlockEntity depot) {
        // 使用 DepotBehaviour 获取物品
        if (depot instanceof com.simibubi.create.content.logistics.depot.DepotBlockEntity depotEntity) {
            return depotEntity.getHeldItem();
        }

        // 通过 DepotBehaviour.get 获取
        var behaviour = com.simibubi.create.content.logistics.depot.DepotBehaviour.get(
                depot, com.simibubi.create.content.logistics.depot.DepotBehaviour.TYPE);
        if (behaviour != null) {
            return behaviour.getHeldItemStack();
        }

        // 备用方案：通过物品处理器
        if (depot.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).isPresent()) {
            var handler = depot.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).resolve().get();
            if (handler.getSlots() > 0) {
                return handler.getStackInSlot(0);
            }
        }

        return ItemStack.EMPTY;
    }

    private void setItemOnDepot(BlockEntity depot, ItemStack stack) {
        // 使用 DepotBlockEntity 的方法
        if (depot instanceof com.simibubi.create.content.logistics.depot.DepotBlockEntity depotEntity) {
            depotEntity.setHeldItem(stack);
            depot.setChanged();
            return;
        }

        // 通过 DepotBehaviour.get 设置
        var behaviour = com.simibubi.create.content.logistics.depot.DepotBehaviour.get(
                depot, com.simibubi.create.content.logistics.depot.DepotBehaviour.TYPE);
        if (behaviour != null) {
            if (stack.isEmpty()) {
                behaviour.removeHeldItem();
            } else {
                var transportedStack = new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(stack);
                transportedStack.beltPosition = 0.5f;
                transportedStack.prevBeltPosition = 0.5f;
                behaviour.setCenteredHeldItem(transportedStack);
            }
            depot.setChanged();
            return;
        }

        // 备用方案
        if (depot.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).isPresent()) {
            var handler = depot.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).resolve().get();
            if (handler.getSlots() > 0) {
                handler.extractItem(0, Integer.MAX_VALUE, false);
                if (!stack.isEmpty()) {
                    handler.insertItem(0, stack, false);
                }
            }
        }
    }

    private void clearDepot(BlockEntity depot) {
        setItemOnDepot(depot, ItemStack.EMPTY);
    }

    private void closeFaucet() {
        // 关闭龙头
        level.setBlockAndUpdate(worldPosition, getBlockState().setValue(BlockStateProperties.OPEN, false));
        renderingFluid = FluidStack.EMPTY;
        pendingFluid = FluidStack.EMPTY;
        isFillingItem = false;
        processingTicks = 0;
        processingItem = ItemStack.EMPTY;
        sourceDirection = null;
        sourceBlockPos = null;
        notifyUpdate();
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.put("RenderingFluid", renderingFluid.writeToNBT(new CompoundTag()));
        tag.put("PendingFluid", pendingFluid.writeToNBT(new CompoundTag()));
        tag.putBoolean("IsFillingItem", isFillingItem);
        tag.putInt("ProcessingTicks", processingTicks);
        tag.putInt("TransferCooldown", transferCooldown);
        if (!processingItem.isEmpty()) {
            tag.put("ProcessingItem", processingItem.save(new CompoundTag()));
        }
        if (sourceDirection != null) {
            tag.putInt("SourceDirection", sourceDirection.get3DDataValue());
        }
        if (sourceBlockPos != null) {
            tag.putLong("SourcePos", sourceBlockPos.asLong());
        }
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        renderingFluid = FluidStack.loadFluidStackFromNBT(tag.getCompound("RenderingFluid"));
        pendingFluid = FluidStack.loadFluidStackFromNBT(tag.getCompound("PendingFluid"));
        isFillingItem = tag.getBoolean("IsFillingItem");
        processingTicks = tag.getInt("ProcessingTicks");
        transferCooldown = tag.getInt("TransferCooldown");
        if (tag.contains("ProcessingItem")) {
            processingItem = ItemStack.of(tag.getCompound("ProcessingItem"));
        }
        if (tag.contains("SourceDirection")) {
            sourceDirection = Direction.from3DDataValue(tag.getInt("SourceDirection"));
        }
        if (tag.contains("SourcePos")) {
            sourceBlockPos = BlockPos.of(tag.getLong("SourcePos"));
        }
    }

    // Getter方法供渲染器使用
    public FluidStack getRenderingFluid() {
        return renderingFluid;
    }

    public int getProcessingTicks() {
        return processingTicks;
    }

    public boolean isProcessing() {
        return isFillingItem && processingTicks > 0;
    }

    public boolean hasFluidToRender() {
        return !renderingFluid.isEmpty();
    }
}