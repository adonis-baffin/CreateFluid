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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
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
            // 关闭状态，清空渲染缓存
            if (!renderingFluid.isEmpty()) {
                renderingFluid = FluidStack.EMPTY;
                isFillingItem = false;
                processingTicks = 0;
                processingItem = ItemStack.EMPTY;
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
            if (processingTicks == 0) {
                // 注液完成，清理状态
                finishItemFilling();
            }
            return; // 注液期间不做其他事
        }

        // 只在冷却完成后尝试新的传输
        if (transferCooldown == 0) {
            tryTransferFluid();
        }
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
        boolean success = tryProcess(sourceHandler, targetPos);

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

    private boolean tryProcess(IFluidHandler sourceHandler, BlockPos targetPos) {
        BlockEntity targetEntity = level.getBlockEntity(targetPos);
        BlockState targetState = level.getBlockState(targetPos);

        // 优先检查置物台上的物品
        if (targetEntity != null && isDepot(targetEntity)) {
            ItemStack itemOnDepot = getItemOnDepot(targetEntity);
            if (!itemOnDepot.isEmpty() && FillingBySpout.canItemBeFilled(level, itemOnDepot)) {
                return startItemFilling(sourceHandler, targetPos, itemOnDepot);
            }
            // 置物台上没有可填充物品，不继续
            return false;
        }

        // 检查是否可以填充的容器
        if (targetState.is(FAUCET_FILLABLE) && targetEntity != null) {
            return tryFillContainer(sourceHandler, targetEntity);
        }

        return false;
    }

    private boolean startItemFilling(IFluidHandler sourceHandler, BlockPos targetPos, ItemStack item) {
        // 获取可用流体
        FluidStack availableFluid = sourceHandler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (availableFluid.isEmpty())
            return false;

        // 检查需要的量
        int requiredAmount = FillingBySpout.getRequiredAmountForItem(level, item, availableFluid);
        if (requiredAmount <= 0 || requiredAmount > availableFluid.getAmount())
            return false;

        // 开始注液
        FluidStack drainedFluid = sourceHandler.drain(requiredAmount, IFluidHandler.FluidAction.EXECUTE);
        if (drainedFluid.isEmpty())
            return false;

        // 设置注液状态
        isFillingItem = true;
        processingTicks = FILLING_TIME;
        processingItem = item.copy();
        renderingFluid = drainedFluid.copy();

        // 播放声音
        AllSoundEvents.SPOUTING.playOnServer(level, worldPosition, 0.75f, 0.9f + 0.2f * level.random.nextFloat());

        // 发送粒子效果
        sendFillingParticles(targetPos, drainedFluid);

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
        if (!isFillingItem || processingItem.isEmpty())
            return;

        // 找到置物台
        BlockPos targetPos = worldPosition.below();
        BlockEntity targetEntity = level.getBlockEntity(targetPos);

        if (targetEntity != null && isDepot(targetEntity)) {
            ItemStack itemOnDepot = getItemOnDepot(targetEntity);

            // 确保物品还在
            if (!itemOnDepot.isEmpty()) {
                // 执行填充
                ItemStack result = FillingBySpout.fillItem(level, renderingFluid.getAmount(),
                        itemOnDepot.copy(), renderingFluid);

                if (!result.isEmpty()) {
                    // 消耗原物品
                    itemOnDepot.shrink(1);
                    if (!itemOnDepot.isEmpty()) {
                        setItemOnDepot(targetEntity, itemOnDepot);
                    } else {
                        clearDepot(targetEntity);
                    }

                    // 放置结果物品
                    Vec3 dropPos = Vec3.atCenterOf(targetPos).add(0, 0.5, 0);
                    ItemEntity resultEntity = new ItemEntity(level, dropPos.x, dropPos.y, dropPos.z, result);
                    resultEntity.setDeltaMovement(Vec3.ZERO);
                    level.addFreshEntity(resultEntity);

                    // 播放完成声音
                    level.playSound(null, targetPos,
                            net.minecraft.sounds.SoundEvents.BOTTLE_FILL,
                            net.minecraft.sounds.SoundSource.BLOCKS,
                            0.5f, 1.0f + level.random.nextFloat() * 0.2f);
                }
            }
        }

        // 清理状态
        isFillingItem = false;
        processingTicks = 0;
        processingItem = ItemStack.EMPTY;
        renderingFluid = FluidStack.EMPTY;
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

        // 执行实际传输
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
        // 尝试通过物品处理器获取
        if (depot.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).isPresent()) {
            var handler = depot.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).resolve().get();
            if (handler.getSlots() > 0) {
                return handler.getStackInSlot(0);
            }
        }

        // 备用方案：搜索上方的掉落物
        AABB searchArea = new AABB(depot.getBlockPos()).inflate(0.5, 1, 0.5).move(0, 0.5, 0);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, searchArea);
        if (!items.isEmpty()) {
            return items.get(0).getItem();
        }

        return ItemStack.EMPTY;
    }

    private void setItemOnDepot(BlockEntity depot, ItemStack stack) {
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
        isFillingItem = false;
        processingTicks = 0;
        processingItem = ItemStack.EMPTY;
        notifyUpdate();
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.put("RenderingFluid", renderingFluid.writeToNBT(new CompoundTag()));
        tag.putBoolean("IsFillingItem", isFillingItem);
        tag.putInt("ProcessingTicks", processingTicks);
        tag.putInt("TransferCooldown", transferCooldown);
        if (!processingItem.isEmpty()) {
            tag.put("ProcessingItem", processingItem.save(new CompoundTag()));
        }
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        renderingFluid = FluidStack.loadFluidStackFromNBT(tag.getCompound("RenderingFluid"));
        isFillingItem = tag.getBoolean("IsFillingItem");
        processingTicks = tag.getInt("ProcessingTicks");
        transferCooldown = tag.getInt("TransferCooldown");
        if (tag.contains("ProcessingItem")) {
            processingItem = ItemStack.of(tag.getCompound("ProcessingItem"));
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