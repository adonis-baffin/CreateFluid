package com.adonis.createfisheryindustry.block.MeshTrap;

import com.adonis.createfisheryindustry.block.common.TrapBlockEntity;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MeshTrapBlockEntity extends TrapBlockEntity implements IHaveGoggleInformation {
    protected static final int PROCESSING_TIME = 20;
    protected static final double ENTITY_KILL_RANGE = 1.0; // 与 FrameTrapMovementBehaviour 一致
    protected static final float PHANTOM_MAX_HEALTH = 20.0F; // 与 FrameTrapMovementBehaviour 一致
    private static final Set<String> FISH_ITEM_IDS = new HashSet<>(Arrays.asList(
            "minecraft:cod",
            "minecraft:salmon",
            "minecraft:tropical_fish",
            "minecraft:pufferfish",
            "minecraft:cooked_cod",
            "minecraft:cooked_salmon"));

    public MeshTrapBlockEntity(BlockPos pos, BlockState state) {
        super(CreateFisheryBlockEntities.MESH_TRAP.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MeshTrapBlockEntity be) {
        if (level.isClientSide) {
            return;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        be.processingTicks++;
        if (be.processingTicks >= PROCESSING_TIME) {
            be.processingTicks = 0;
            be.collectNearbyItems(serverLevel);

            // Check if inventory has space before processing fish
            boolean hasSpace = false;
            for (int i = 0; i < be.inventory.getSlots(); i++) {
                ItemStack stack = be.inventory.getStackInSlot(i);
                if (stack.isEmpty() || (stack.getCount() < stack.getMaxStackSize() && stack.getMaxStackSize() > 0)) {
                    hasSpace = true;
                    break;
                }
            }

            if (!hasSpace) {
                System.out.println("MeshTrapBlockEntity: Inventory full, skipping tryProcessFish at " + pos);
                return;
            }

            be.tryProcessFish(serverLevel);
        }
    }

    @Override
    protected void tryProcessFish(ServerLevel level) {
        AABB boundingBox = new AABB(getBlockPos()).inflate(ENTITY_KILL_RANGE);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, boundingBox);

        for (Entity entity : entities) {
            if (entity == null || !entity.isAlive()) continue;

            // Check for rabbits and phantoms
            boolean isRabbit = entity instanceof Rabbit || entity.getType() == EntityType.RABBIT;
            boolean isPhantom = entity instanceof Phantom || entity.getType() == EntityType.PHANTOM;

            // Check for aquatic entities
            boolean isAquatic = entity.getType() == EntityType.COD ||
                    entity.getType() == EntityType.SALMON ||
                    entity.getType() == EntityType.TROPICAL_FISH ||
                    entity.getType() == EntityType.PUFFERFISH;

            if (!isAquatic && !isRabbit && !isPhantom) {
                String entityId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString().toLowerCase();
                isAquatic = entityId.contains("fish") || entityId.contains("cod") ||
                        entityId.contains("salmon") || entityId.contains("squid");
            }

            if ((isAquatic || isRabbit || isPhantom) && entity instanceof Mob mob) {
                // Apply health limit for aquatic entities and rabbits, but not for phantoms
                if ((isAquatic || isRabbit) && mob.getMaxHealth() >= 10.0F) continue;
                if (isPhantom && mob.getMaxHealth() > PHANTOM_MAX_HEALTH) continue;

                // Generate loot based on entity type
                List<ItemStack> loots = new ArrayList<>();
                if (isPhantom) {
                    // Phantom: 50% chance for 1 phantom membrane
                    if (level.random.nextFloat() < 0.5F) {
                        loots.add(new ItemStack(Items.PHANTOM_MEMBRANE, 1));
                    }
                } else if (isRabbit) {
                    // Rabbit: 100% chance for 1 raw rabbit, 50% chance for 1 rabbit hide, 10% chance for 1 rabbit foot
                    loots.add(new ItemStack(Items.RABBIT, 1));
                    if (level.random.nextFloat() < 0.5F) {
                        loots.add(new ItemStack(Items.RABBIT_HIDE, 1));
                    }
                    if (level.random.nextFloat() < 0.1F) {
                        loots.add(new ItemStack(Items.RABBIT_FOOT, 1));
                    }
                } else if (isAquatic) {
                    // Aquatic: Use entity loot table and validate fish items
                    ResourceLocation lootTableId = mob.getLootTable();
                    if (lootTableId == null) continue;

                    LootParams params = new LootParams.Builder(level)
                            .withParameter(LootContextParams.THIS_ENTITY, mob)
                            .withParameter(LootContextParams.ORIGIN, mob.position())
                            .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                            .create(LootContextParamSets.ENTITY);

                    LootTable lootTable = level.getServer().getLootData().getLootTable(lootTableId);
                    loots = lootTable.getRandomItems(params);

                    // Validate aquatic loot
                    boolean hasFish = false;
                    for (ItemStack stack : loots) {
                        String itemId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
                        if (FISH_ITEM_IDS.contains(itemId) || itemId.contains("fish") || itemId.contains("cod") || itemId.contains("salmon")) {
                            hasFish = true;
                            break;
                        }
                    }
                    if (!hasFish) {
                        System.out.println("MeshTrapBlockEntity: Invalid loot for aquatic entity " + mob.getType().getDescriptionId() + ", skipping.");
                        continue;
                    }
                }

                // Log generated loot for debugging
                System.out.println("MeshTrapBlockEntity: Processing entity " + mob.getType().getDescriptionId() +
                        (isRabbit ? " (Rabbit)" : isPhantom ? " (Phantom)" : " (Aquatic)"));
                for (ItemStack stack : loots) {
                    System.out.println("MeshTrapBlockEntity: Generated loot: " + stack.getCount() + "x " + stack.getItem().getDescriptionId());
                }

                // Insert loots into inventory
                boolean allInserted = true;
                for (ItemStack stack : loots) {
                    ItemStack remainder = stack.copy();
                    for (int i = 0; i < inventory.getSlots(); i++) {
                        remainder = inventory.insertItem(i, remainder, false);
                        System.out.println("MeshTrapBlockEntity: Inserted into slot " + i + ", remaining: " + (remainder.isEmpty() ? "None" : remainder.getCount() + "x " + remainder.getItem().getDescriptionId()));
                        if (remainder.isEmpty()) break;
                    }
                    if (!remainder.isEmpty()) {
                        ItemEntity itemEntity = new ItemEntity(level, mob.getX(), mob.getY(), mob.getZ(), remainder);
                        level.addFreshEntity(itemEntity);
                        allInserted = false;
                        System.out.println("MeshTrapBlockEntity: Dropped " + remainder.getCount() + "x " + remainder.getItem().getDescriptionId() + " at " + mob.position());
                    }
                    System.out.println("MeshTrapBlockEntity: Captured entity " + mob.getType().getDescriptionId() +
                            ", dropped " + stack.getCount() + "x " + stack.getItem().getDescriptionId() + " at " + getBlockPos() +
                            (isRabbit ? " (Rabbit)" : isPhantom ? " (Phantom)" : " (Aquatic)"));
                }

                if (allInserted) {
                    mob.setRemoved(Entity.RemovalReason.KILLED);
                }
            }
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        // Add inventory title using CreateLang
        CreateLang.translate("gui.goggles.mesh_trap_contents")
                .forGoggles(tooltip);

        // Get inventory
        ItemStackHandler inv = getInventory();
        boolean isEmpty = true;

        // Log inventory contents for debugging
        System.out.println("MeshTrapBlockEntity: Goggle tooltip requested at " + getBlockPos() + ", isClientSide: " + (level != null && level.isClientSide));
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            System.out.println("Slot " + i + ": " + (stack.isEmpty() ? "Empty" : stack.getCount() + "x " + stack.getItem().getDescriptionId()));
        }

        // Add inventory contents
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                isEmpty = false;
                CreateLang.builder()
                        .add(Component.translatable(stack.getDescriptionId()))
                        .text(" x" + stack.getCount())
                        .forGoggles(tooltip, 1); // Indent by 1
            }
        }

        // If inventory is empty, add "Empty" message
        if (isEmpty) {
            CreateLang.translate("gui.goggles.inventory.empty")
                    .forGoggles(tooltip, 1);
        }

        // Check capability-based inventory for debugging
        LazyOptional<IItemHandler> handler = getCapability(ForgeCapabilities.ITEM_HANDLER, null);
        if (handler.isPresent()) {
            IItemHandler capInv = handler.orElse(null);
            System.out.println("MeshTrapBlockEntity: Capability inventory check at " + getBlockPos());
            for (int i = 0; i < capInv.getSlots(); i++) {
                ItemStack stack = capInv.getStackInSlot(i);
                System.out.println("Capability Slot " + i + ": " + (stack.isEmpty() ? "Empty" : stack.getCount() + "x " + stack.getItem().getDescriptionId()));
            }
        }

        return true;
    }
}