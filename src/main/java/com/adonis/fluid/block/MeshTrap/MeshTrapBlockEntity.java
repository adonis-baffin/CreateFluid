package com.adonis.fluid.block.MeshTrap;

import com.adonis.fluid.block.common.TrapBlockEntity;
import com.adonis.fluid.config.CFCommonConfig;
import com.adonis.fluid.registry.CFBlockEntity;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MeshTrapBlockEntity extends TrapBlockEntity implements IHaveGoggleInformation {
    protected static final int PROCESSING_TIME = 10;
    protected static final double ENTITY_KILL_RANGE = 1.0;
    private static final double MAX_COLLISION_BOX_SIZE = 0.8;

    // 自动输出相关常量
    private static final int AUTO_EXPORT_COOLDOWN = 20;
    private int autoExportTicks = 0;

    // 传送带提取相关常量
    private static final int BELT_EXTRACTION_COOLDOWN = 5;
    private int beltExtractionTicks = 0;
    private final Map<Direction, TransportedItemStackHandlerBehaviour> beltHandlers = new HashMap<>();

    private final IItemHandler insertionHandler;
    private final IItemHandler extractionHandler;

    public MeshTrapBlockEntity(BlockPos pos, BlockState state) {
        super(CFBlockEntity.MESH_TRAP.get(), pos, state);
        this.insertionHandler = new InsertionOnlyItemHandler(inventory);
        this.extractionHandler = new ExtractionOnlyItemHandler(inventory);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MeshTrapBlockEntity be) {
        if (level.isClientSide()) {
            return;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        be.processingTicks++;
        if (be.processingTicks >= PROCESSING_TIME) {
            be.processingTicks = 0;
            be.collectNearbyItems(serverLevel);
            be.tryProcessEntities(serverLevel);

            be.setChanged();
            be.sendData();
            serverLevel.sendBlockUpdated(pos, state, state, 3);
        }

        // 自动输出逻辑
        be.autoExportTicks++;
        if (be.autoExportTicks >= AUTO_EXPORT_COOLDOWN) {
            be.autoExportTicks = 0;
            be.tryExportItems(serverLevel);
        }

        // 传送带提取逻辑
        be.beltExtractionTicks++;
        if (be.beltExtractionTicks >= BELT_EXTRACTION_COOLDOWN) {
            be.beltExtractionTicks = 0;
            be.extractItemsFromBelts(serverLevel);
        }

        be.tick();
    }

    // 从周围的传送带提取物品
    protected void extractItemsFromBelts(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }

        updateBeltHandlers(level);

        for (Direction direction : Direction.values()) {
            if (direction == Direction.UP || direction == Direction.DOWN) {
                continue;
            }

            TransportedItemStackHandlerBehaviour handler = beltHandlers.get(direction);
            if (handler != null) {
                extractFromBelt(handler);
            }
        }
    }

    private void updateBeltHandlers(ServerLevel level) {
        beltHandlers.clear();

        for (Direction direction : Direction.values()) {
            if (direction == Direction.UP || direction == Direction.DOWN) {
                continue;
            }

            BlockPos neighborPos = getBlockPos().relative(direction);
            if (level.getBlockEntity(neighborPos) instanceof SmartBlockEntity smartBE) {
                TransportedItemStackHandlerBehaviour behaviour = BlockEntityBehaviour.get(level, neighborPos,
                        TransportedItemStackHandlerBehaviour.TYPE);
                if (behaviour != null) {
                    beltHandlers.put(direction, behaviour);
                }
            }
        }
    }

    private void extractFromBelt(TransportedItemStackHandlerBehaviour beltHandler) {
        boolean hasSpace = false;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) {
                hasSpace = true;
                break;
            }
        }

        if (!hasSpace) {
            return;
        }

        beltHandler.handleCenteredProcessingOnAllItems(.5f, transportedItem -> {
            ItemStack stack = transportedItem.stack;
            if (stack.isEmpty()) {
                return TransportedResult.doNothing();
            }

            ItemStack remaining = stack.copy();
            boolean anyInserted = false;

            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack slotStack = inventory.getStackInSlot(slot);

                if (slotStack.isEmpty() || (ItemStack.isSameItem(slotStack, remaining) &&
                        slotStack.getCount() < slotStack.getMaxStackSize())) {

                    ItemStack toInsert = remaining.copy();
                    remaining = inventory.insertItem(slot, toInsert, false);

                    if (remaining.getCount() < toInsert.getCount()) {
                        anyInserted = true;
                        if (remaining.isEmpty()) {
                            break;
                        }
                    }
                }
            }

            if (anyInserted) {
                setChanged();
                sendData();
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
                }

                if (remaining.isEmpty()) {
                    return TransportedResult.removeItem();
                } else {
                    TransportedItemStack newTransportedItem = new TransportedItemStack(remaining);
                    newTransportedItem.prevBeltPosition = transportedItem.prevBeltPosition;
                    newTransportedItem.beltPosition = transportedItem.beltPosition;
                    newTransportedItem.insertedFrom = transportedItem.insertedFrom;
                    newTransportedItem.insertedAt = transportedItem.insertedAt;
                    return TransportedResult.convertTo(newTransportedItem);
                }
            }

            return TransportedResult.doNothing();
        });
    }

    // 尝试将物品输出到相邻容器
    protected void tryExportItems(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }

        boolean anyItemMoved = false;

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = getBlockPos().relative(direction);

            // 检查是否是传送带，如果是则跳过
            TransportedItemStackHandlerBehaviour beltBehaviour = BlockEntityBehaviour.get(level, neighborPos,
                    TransportedItemStackHandlerBehaviour.TYPE);
            if (beltBehaviour != null) {
                continue;
            }

            // 检查目标方块实体类型，如果是同类型则跳过
            if (level.getBlockEntity(neighborPos) instanceof MeshTrapBlockEntity) {
                continue;
            }

            // 使用Forge能力系统获取相邻方块的物品处理器
            LazyOptional<IItemHandler> capability = level.getBlockEntity(neighborPos) != null
                    ? level.getBlockEntity(neighborPos).getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite())
                    : LazyOptional.empty();

            if (capability.isPresent()) {
                IItemHandler targetInventory = capability.orElse(null);
                if (targetInventory != null) {
                    for (int sourceSlot = 0; sourceSlot < inventory.getSlots(); sourceSlot++) {
                        ItemStack stackInSlot = inventory.getStackInSlot(sourceSlot);

                        if (stackInSlot.isEmpty()) {
                            continue;
                        }

                        ItemStack extractedItem = inventory.extractItem(sourceSlot, stackInSlot.getCount(), true);
                        if (extractedItem.isEmpty()) {
                            continue;
                        }

                        ItemStack remainingItem = insertItemIntoTarget(extractedItem, targetInventory);

                        int actualExtractAmount = extractedItem.getCount() - remainingItem.getCount();
                        if (actualExtractAmount > 0) {
                            inventory.extractItem(sourceSlot, actualExtractAmount, false);
                            anyItemMoved = true;
                            break;
                        }
                    }

                    if (anyItemMoved) {
                        break;
                    }
                }
            }
        }

        if (anyItemMoved) {
            setChanged();
            sendData();
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    private ItemStack insertItemIntoTarget(ItemStack stack, IItemHandler targetInventory) {
        ItemStack remaining = stack.copy();

        for (int slot = 0; slot < targetInventory.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = targetInventory.insertItem(slot, remaining, false);
        }

        return remaining;
    }

    protected void tryProcessEntities(ServerLevel level) {
        CFCommonConfig.refreshCache();

        AABB boundingBox = new AABB(getBlockPos()).inflate(ENTITY_KILL_RANGE);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, boundingBox);

        for (Entity entity : entities) {
            if (!(entity instanceof Mob mob) || !entity.isAlive()) {
                continue;
            }

            EntityType<?> entityType = entity.getType();
            ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(entityType);

            if (CFCommonConfig.isEntityBlacklisted(entityId)) {
                continue;
            }

            if (CFCommonConfig.isEntityWhitelisted(entityId)) {
                processEntityDrops(level, mob);
                continue;
            }

            AABB collisionBox = entity.getBoundingBox();
            double width = collisionBox.getXsize();
            double height = collisionBox.getYsize();
            double depth = collisionBox.getZsize();

            if (width <= MAX_COLLISION_BOX_SIZE && height <= MAX_COLLISION_BOX_SIZE && depth <= MAX_COLLISION_BOX_SIZE) {
                processEntityDrops(level, mob);
            }
        }
    }

    private void processEntityDrops(ServerLevel level, Mob mob) {
        var lootTableKey = mob.getLootTable();
        if (lootTableKey == null) return;

        LootParams.Builder paramsBuilder = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, mob)
                .withParameter(LootContextParams.ORIGIN, mob.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic());

        List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
        if (!players.isEmpty()) {
            Player nearestPlayer = level.getNearestPlayer(mob, -1);
            if (nearestPlayer instanceof ServerPlayer serverPlayer) {
                paramsBuilder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, serverPlayer);
            }
        }

        LootParams params = paramsBuilder.create(LootContextParamSets.ENTITY);
        LootTable lootTable = level.getServer().getLootData().getLootTable(lootTableKey);
        List<ItemStack> loots = lootTable.getRandomItems(params);

        boolean allInserted = true;
        for (ItemStack stack : loots) {
            ItemStack remainder = stack.copy();
            for (int i = 0; i < inventory.getSlots(); i++) {
                remainder = inventory.insertItem(i, remainder, false);
                if (remainder.isEmpty()) {
                    break;
                }
            }
            if (!remainder.isEmpty()) {
                ItemEntity itemEntity = new ItemEntity(level, mob.getX(), mob.getY(), mob.getZ(), remainder);
                level.addFreshEntity(itemEntity);
                allInserted = false;
            }
        }

        // 生成粒子效果
        boolean inWater = getBlockState().getValue(MeshTrapBlock.WATERLOGGED);
        var particleType = inWater ? ParticleTypes.BUBBLE : ParticleTypes.CLOUD;
        level.sendParticles(particleType,
                mob.getX(), mob.getY() + 0.5, mob.getZ(),
                15, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, new BlockPos((int) mob.getX(), (int) mob.getY(), (int) mob.getZ()),
                SoundEvents.BUCKET_FILL_FISH, SoundSource.BLOCKS, 1.0F, 1.0F);

        // 生成经验颗粒
        Item expNuggetItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("create", "experience_nugget"));
        if (expNuggetItem != null && expNuggetItem != ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", "air"))) {
            ItemStack expNugget = new ItemStack(expNuggetItem, 1);
            ItemStack remainder = expNugget.copy();
            for (int i = 0; i < inventory.getSlots(); i++) {
                remainder = inventory.insertItem(i, remainder, false);
                if (remainder.isEmpty()) {
                    break;
                }
            }
            if (!remainder.isEmpty()) {
                ItemEntity itemEntity = new ItemEntity(level, mob.getX(), mob.getY(), mob.getZ(), remainder);
                level.addFreshEntity(itemEntity);
            }
        }

        mob.setRemoved(Entity.RemovalReason.KILLED);
        setChanged();
        sendData();
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.translate("gui.goggles.mesh_trap_contents").forGoggles(tooltip);

        ItemStackHandler inv = getInventory();
        boolean isEmpty = true;

        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                isEmpty = false;
                CreateLang.text("")
                        .add(Component.translatable(stack.getDescriptionId()).withStyle(ChatFormatting.GRAY))
                        .add(CreateLang.text(" x" + stack.getCount()).style(ChatFormatting.GREEN))
                        .forGoggles(tooltip, 1);
            }
        }

        if (isEmpty) {
            CreateLang.translate("gui.goggles.inventory.empty").forGoggles(tooltip, 1);
        }

        return true;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return LazyOptional.of(() -> inventory).cast();
        }
        return super.getCapability(cap, side);
    }

    // 内部类：只能插入的物品处理器
    private static class InsertionOnlyItemHandler implements IItemHandler {
        private final IItemHandler wrapped;

        public InsertionOnlyItemHandler(IItemHandler wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public int getSlots() {
            return wrapped.getSlots();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return wrapped.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return wrapped.insertItem(slot, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return wrapped.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return wrapped.isItemValid(slot, stack);
        }
    }

    // 内部类：只能提取的物品处理器
    private static class ExtractionOnlyItemHandler implements IItemHandler {
        private final IItemHandler wrapped;

        public ExtractionOnlyItemHandler(IItemHandler wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public int getSlots() {
            return wrapped.getSlots();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return wrapped.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return wrapped.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return wrapped.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return wrapped.isItemValid(slot, stack);
        }
    }
}