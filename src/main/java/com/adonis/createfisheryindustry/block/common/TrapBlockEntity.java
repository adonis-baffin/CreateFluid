package com.adonis.createfisheryindustry.block.common;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TrapBlockEntity extends BlockEntity {
    public static final int INVENTORY_SLOTS = 9; // 可调整的库存槽位数量
    protected static final double COLLECTION_RANGE = 1.5;
    protected static final double FISH_PROCESSING_RANGE = 1.5;
    protected final ItemStackHandler inventory;
    protected int processingTicks = 0;
    private final LazyOptional<IItemHandler> inventoryOptional;
    protected int syncCooldown = 0;
    protected boolean queuedSync = false;
    private static final int SYNC_RATE = 8;

    public TrapBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.inventory = createInventory();
        this.inventoryOptional = LazyOptional.of(() -> inventory);
        System.out.println("TrapBlockEntity: Inventory initialized with " + INVENTORY_SLOTS + " slots");
    }

    protected ItemStackHandler createInventory() {
        return new ItemStackHandler(INVENTORY_SLOTS) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                if (level != null && !level.isClientSide) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                    sendData();
                }
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return true;
            }
        };
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public boolean insertItem(ItemStack stack) {
        if (stack.isEmpty() || level == null || level.isClientSide) {
            return false;
        }

        ItemStack remainder = stack.copy();
        for (int i = 0; i < inventory.getSlots(); i++) {
            remainder = inventory.insertItem(i, remainder, false);
            if (remainder.isEmpty()) {
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                sendData();
                return true;
            }
        }

        boolean partialSuccess = stack.getCount() != remainder.getCount();
        if (partialSuccess) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            sendData();
        }
        return partialSuccess;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        CompoundTag invTag = inventory.serializeNBT();
        tag.put("Inventory", invTag);
        tag.putInt("ProcessingTicks", processingTicks);
        System.out.println("TrapBlockEntity: Saving inventory NBT at " + worldPosition + ": " + invTag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(tag.getCompound("Inventory"));
            System.out.println("TrapBlockEntity: Loaded inventory NBT at " + worldPosition + ": " + tag.getCompound("Inventory"));
        }
        processingTicks = tag.getInt("ProcessingTicks");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.put("Inventory", inventory.serializeNBT());
        tag.putInt("ProcessingTicks", processingTicks);
        System.out.println("TrapBlockEntity: Sending update tag at " + worldPosition + ": " + tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        System.out.println("TrapBlockEntity: Sending update packet at " + worldPosition);
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        System.out.println("TrapBlockEntity: Received update tag at " + worldPosition + ": " + tag);
        load(tag);
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return inventoryOptional.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        inventoryOptional.invalidate();
    }

    protected void collectNearbyItems(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }

        AABB boundingBox = new AABB(getBlockPos()).inflate(COLLECTION_RANGE);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, boundingBox);

        for (ItemEntity itemEntity : items) {
            if (itemEntity == null || !itemEntity.isAlive()) continue;
            ItemStack stack = itemEntity.getItem();
            if (!stack.isEmpty()) {
                ItemStack copy = stack.copy();
                if (insertItem(copy)) {
                    itemEntity.discard();
                } else if (copy.getCount() < stack.getCount()) {
                    itemEntity.setItem(copy);
                }
            }
        }
    }

    protected void tryProcessFish(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }

        AABB boundingBox = new AABB(getBlockPos()).inflate(FISH_PROCESSING_RANGE);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, boundingBox);

        for (Entity entity : entities) {
            if (entity == null || !entity.isAlive()) continue;

            boolean isAquatic = entity instanceof WaterAnimal ||
                    (entity.getType() == EntityType.COD ||
                            entity.getType() == EntityType.SALMON ||
                            entity.getType() == EntityType.TROPICAL_FISH ||
                            entity.getType() == EntityType.PUFFERFISH);

            if (isAquatic && entity instanceof Mob mob && mob.getMaxHealth() < 10.0F) {
                try {
                    mob.hurt(level.damageSources().generic(), mob.getMaxHealth() * 2);
                    entity.discard();
                } catch (Exception e) {
// 记录异常但不中断游戏
                }
            }
        }
    }

    public void dropInventory() {
        if (level instanceof ServerLevel serverLevel) {
            Vec3 pos = Vec3.atCenterOf(worldPosition);
            for (int i = 0; i < inventory.getSlots(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    ItemEntity itemEntity = new ItemEntity(level, pos.x, pos.y, pos.z, stack);
                    serverLevel.addFreshEntity(itemEntity);
                    inventory.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        }
    }

    public void sendData() {
        if (syncCooldown > 0) {
            queuedSync = true;
            return;
        }
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            setChanged();
            System.out.println("TrapBlockEntity: Triggering sendData at " + worldPosition);
        }
        queuedSync = false;
        syncCooldown = SYNC_RATE;
    }

    public void tick() {
        if (syncCooldown > 0) {
            syncCooldown--;
            if (syncCooldown == 0 && queuedSync) {
                sendData();
            }
        }
    }
}