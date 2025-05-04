package com.adonis.createfisheryindustry.block.FrameTrap;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemFishedEvent;

import java.util.List;

public class FrameTrapMovementBehaviour implements MovementBehaviour {
    private static final double MAX_COLLISION_BOX_SIZE = 0.8;
    private static final float WORN_HARPOON_CHANCE = 0.03f;

    @Override
    public void tick(MovementContext context) {
        if (context.world instanceof ServerLevel level) {
            FrameTrapContext fishing = getFishingNetContext(context, level);
            if (fishing.timeUntilCatch > 0) {
                fishing.timeUntilCatch--;
            }
        }
    }

    @Override
    public void visitNewPosition(MovementContext context, BlockPos pos) {
        if (context.world instanceof ServerLevel level) {
            FrameTrapContext fishing = getFishingNetContext(context, level);
            boolean inWater = fishing.visitNewPosition(level, pos);
            killNearbyEntities(context, pos, level);
            collectNearbyItems(context, pos, level);
            if (!inWater || fishing.timeUntilCatch > 0) return;

            if (fishing.canCatch()) {
                LootParams params = fishing.buildLootContext(context, level, pos);
                LootTable lootTable = level.getServer().getLootData().getLootTable(BuiltInLootTables.FISHING);
                List<ItemStack> loots = lootTable.getRandomItems(params);

                if (fishing.randomSource.nextFloat() < WORN_HARPOON_CHANCE) {
                    ItemStack wornHarpoon = new ItemStack(CreateFisheryItems.WORN_HARPOON.get());
                    loots.add(wornHarpoon);
                }

                List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
                ServerPlayer selectedPlayer = null;
                if (!players.isEmpty()) {
                    Player nearestPlayer = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, -1, false);
                    if (nearestPlayer instanceof ServerPlayer serverPlayer) {
                        selectedPlayer = serverPlayer;
                    }
                }

                FishingHook fishingHook = selectedPlayer != null ? new FishingHook(selectedPlayer, level, 0, 0) : fishing.getFishingHook();
                ItemFishedEvent event = new ItemFishedEvent(loots, 0, fishingHook);
                MinecraftForge.EVENT_BUS.post(event);
                if (!event.isCanceled()) {
                    loots.forEach(stack -> dropItem(context, stack));
                    addExperienceNugget(context, level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    spawnParticles(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, inWater);
                }
                fishing.reset(level);
            }
        }
    }

    @Override
    public void stopMoving(MovementContext context) {
        if (context.temporaryData instanceof FrameTrapContext fishing && context.world instanceof ServerLevel level) {
            fishing.invalidate(level);
        }
    }

    protected FrameTrapContext getFishingNetContext(MovementContext context, ServerLevel level) {
        if (context.temporaryData == null || !(context.temporaryData instanceof FrameTrapContext)) {
            context.temporaryData = new FrameTrapContext(level, new ItemStack(Items.FISHING_ROD));
        }
        return (FrameTrapContext) context.temporaryData;
    }

    protected void collectNearbyItems(MovementContext context, BlockPos pos, ServerLevel level) {
        AABB boundingBox = new AABB(pos).inflate(0.2);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, boundingBox);
        for (ItemEntity itemEntity : items) {
            ItemStack stack = itemEntity.getItem();
            if (!stack.isEmpty()) {
                dropItem(context, stack.copy());
                itemEntity.discard();
            }
        }
    }

    protected void killNearbyEntities(MovementContext context, BlockPos pos, ServerLevel level) {
        AABB boundingBox = new AABB(pos).inflate(0.5);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, boundingBox);

        for (Entity entity : entities) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }

            EntityType<?> entityType = entity.getType();
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);

            if (CreateFisheryCommonConfig.isEntityBlacklisted(entityId)) {
                continue;
            }

            if (CreateFisheryCommonConfig.isEntityWhitelisted(entityId)) {
                processEntityDrops(context, level, mob);
                continue;
            }

            AABB collisionBox = entity.getBoundingBox();
            double width = collisionBox.getXsize();
            double height = collisionBox.getYsize();
            double depth = collisionBox.getZsize();

            if (width <= MAX_COLLISION_BOX_SIZE && height <= MAX_COLLISION_BOX_SIZE && depth <= MAX_COLLISION_BOX_SIZE) {
                processEntityDrops(context, level, mob);
            }
        }
    }

    private void processEntityDrops(MovementContext context, ServerLevel level, Mob mob) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
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
        for (ItemStack stack : loots) {
            dropItem(context, stack);
        }

        mob.remove(Entity.RemovalReason.KILLED);
        addExperienceNugget(context, level, mob.getX(), mob.getY() + 0.5, mob.getZ());
        BlockPos mobPos = new BlockPos((int) mob.getX(), (int) mob.getY(), (int) mob.getZ());
        // 检查实体脚下和当前位置是否为水源
        boolean inWater = level.getFluidState(mobPos).isSource() || level.getFluidState(mobPos.below()).isSource();
        CreateFisheryMod.LOGGER.debug("Spawning particles at {} for entity {}, inWater: {}, entityPos: ({}, {}, {}), fluidState: {}",
                mobPos, entityId, inWater, mob.getX(), mob.getY(), mob.getZ(), level.getFluidState(mobPos).toString());
        spawnParticles(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), inWater);
    }

    private void addExperienceNugget(MovementContext context, ServerLevel level, double x, double y, double z) {
        Item expNuggetItem = BuiltInRegistries.ITEM.get(new ResourceLocation("create", "experience_nugget"));
        if (expNuggetItem != null && expNuggetItem != BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft", "air"))) {
            ItemStack expNugget = new ItemStack(expNuggetItem, 1);
            dropItem(context, expNugget);
        } else {
            level.addFreshEntity(new net.minecraft.world.entity.ExperienceOrb(level, x, y, z, 1));
        }
    }

    private void spawnParticles(ServerLevel level, double x, double y, double z, boolean inWater) {
        var particleType = inWater ? ParticleTypes.BUBBLE : ParticleTypes.CLOUD;
        level.sendParticles(particleType, x, y, z, 15, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, new BlockPos((int) x, (int) y, (int) z),
                SoundEvents.BUCKET_FILL_FISH, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}