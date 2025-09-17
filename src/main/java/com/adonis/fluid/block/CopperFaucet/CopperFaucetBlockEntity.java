package com.adonis.fluid.block.CopperFaucet;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.List;

public class CopperFaucetBlockEntity extends SmartBlockEntity {

    // 流体缓存，用于渲染
    private FluidStack cache = FluidStack.EMPTY;

    // 注液进度
    private int processingTicks = -1;
    private static final int FILLING_TIME = 20;

    // 填充参数
    private static final int TRANSFER_RATE = 250; // 每次最多传输250mb
    private static final int TRANSFER_INTERVAL = 10; // 每10tick传输一次
    private int transferCooldown = 0;

    // 正在处理的物品
    private ItemStack processingItem = ItemStack.EMPTY;

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
            // 关闭状态，清空缓存并返回
            if (!cache.isEmpty()) {
                cache = FluidStack.EMPTY;
                notifyUpdate();
            }
            processingTicks = -1;
            transferCooldown = 0;
            return;
        }

        // 开启状态，执行填充逻辑
        if (transferCooldown > 0) {
            transferCooldown--;
            return;
        }

        // 获取源容器和目标位置
        Direction attached = state.getValue(CopperFaucetBlock.FACING);
        BlockPos sourcePos = worldPosition.relative(attached.getOpposite());
        BlockPos targetPos = worldPosition.below();

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

        // 尝试填充下方容器或物品
        boolean success = tryFillBelow(sourceHandler, targetPos);

        if (!success) {
            // 如果填充失败，尝试处理置物台上的物品
            success = tryProcessDepotItem(sourceHandler, targetPos);
        }

        if (success) {
            transferCooldown = TRANSFER_INTERVAL;
        }
    }

    private boolean tryFillBelow(IFluidHandler sourceHandler, BlockPos targetPos) {
        BlockEntity targetEntity = level.getBlockEntity(targetPos);
        if (targetEntity == null)
            return false;

        // 检查是否是置物台
        if (isDepot(targetEntity))
            return false;

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
        cache = actualDrain.copy();
        targetHandler.fill(actualDrain, IFluidHandler.FluidAction.EXECUTE);

        // 播放声音
        if (level.random.nextFloat() < 0.1f) {
            AllSoundEvents.SPOUTING.playOnServer(level, worldPosition, 0.3f, 0.9f + 0.2f * level.random.nextFloat());
        }

        notifyUpdate();
        return true;
    }

    private boolean tryProcessDepotItem(IFluidHandler sourceHandler, BlockPos targetPos) {
        BlockEntity targetEntity = level.getBlockEntity(targetPos);
        if (targetEntity == null || !isDepot(targetEntity))
            return false;

        // 获取置物台上的物品
        ItemStack itemOnDepot = getItemOnDepot(targetEntity);
        if (itemOnDepot.isEmpty())
            return false;

        // 检查是否可以被注液
        if (!FillingBySpout.canItemBeFilled(level, itemOnDepot))
            return false;

        // 获取所需的流体
        FluidStack availableFluid = sourceHandler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (availableFluid.isEmpty())
            return false;

        int requiredAmount = FillingBySpout.getRequiredAmountForItem(level, itemOnDepot, availableFluid);
        if (requiredAmount <= 0 || requiredAmount > availableFluid.getAmount())
            return false;

        // 开始注液处理
        if (processingTicks == -1) {
            processingTicks = FILLING_TIME;
            processingItem = itemOnDepot.copy();
            cache = availableFluid.copy();
            cache.setAmount(requiredAmount);
            notifyUpdate();

            // 播放开始注液的声音
            AllSoundEvents.SPOUTING.playOnServer(level, worldPosition, 0.75f, 0.9f + 0.2f * level.random.nextFloat());
            return true;
        }

        // 处理注液进度
        if (processingTicks > 0) {
            processingTicks--;

            if (processingTicks == 0) {
                // 完成注液
                FluidStack actualDrain = sourceHandler.drain(requiredAmount, IFluidHandler.FluidAction.EXECUTE);
                ItemStack result = FillingBySpout.fillItem(level, requiredAmount, itemOnDepot, actualDrain);

                if (!result.isEmpty()) {
                    // 替换置物台上的物品
                    setItemOnDepot(targetEntity, result);
                }

                processingTicks = -1;
                processingItem = ItemStack.EMPTY;
                notifyUpdate();
            }

            return true;
        }

        return false;
    }

    private boolean isDepot(BlockEntity entity) {
        // 检查是否是置物台或类似的方块
        return entity.getClass().getSimpleName().contains("Depot") ||
                entity.getClass().getSimpleName().contains("Basin");
    }

    private ItemStack getItemOnDepot(BlockEntity depot) {
        // 尝试获取置物台上的物品
        // 这里需要根据实际的置物台实现来调整
        if (depot.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).isPresent()) {
            var handler = depot.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).resolve().get();
            if (handler.getSlots() > 0) {
                return handler.getStackInSlot(0);
            }
        }

        // 备用方案：搜索上方的掉落物
        AABB searchArea = new AABB(depot.getBlockPos()).inflate(0.5, 1, 0.5);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, searchArea);
        if (!items.isEmpty()) {
            return items.get(0).getItem();
        }

        return ItemStack.EMPTY;
    }

    private void setItemOnDepot(BlockEntity depot, ItemStack stack) {
        // 设置置物台上的物品
        if (depot.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).isPresent()) {
            var handler = depot.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).resolve().get();
            if (handler.getSlots() > 0) {
                handler.extractItem(0, Integer.MAX_VALUE, false);
                handler.insertItem(0, stack, false);
            }
        }
    }

    private void closeFaucet() {
        // 关闭龙头
        level.setBlockAndUpdate(worldPosition, getBlockState().setValue(BlockStateProperties.OPEN, false));
        cache = FluidStack.EMPTY;
        processingTicks = -1;
        notifyUpdate();
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.put("Cache", cache.writeToNBT(new CompoundTag()));
        tag.putInt("ProcessingTicks", processingTicks);
        tag.putInt("TransferCooldown", transferCooldown);
        if (!processingItem.isEmpty()) {
            tag.put("ProcessingItem", processingItem.save(new CompoundTag()));
        }
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        cache = FluidStack.loadFluidStackFromNBT(tag.getCompound("Cache"));
        processingTicks = tag.getInt("ProcessingTicks");
        transferCooldown = tag.getInt("TransferCooldown");
        if (tag.contains("ProcessingItem")) {
            processingItem = ItemStack.of(tag.getCompound("ProcessingItem"));
        }
    }

    // Getter方法供渲染器使用
    public FluidStack getCache() {
        return cache;
    }

    public int getProcessingTicks() {
        return processingTicks;
    }

    public boolean isProcessing() {
        return processingTicks > 0;
    }
}