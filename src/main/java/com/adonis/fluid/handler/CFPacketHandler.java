package com.adonis.fluid.handler;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

public class CFPacketHandler {

    /**
     * 向追踪指定区块的所有玩家发送数据包
     */
    public static void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos chunkPos, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersTrackingChunk(level, chunkPos, payload);
    }

    /**
     * 向指定位置附近的玩家发送数据包
     */
    public static void sendToPlayersNear(ServerLevel level, Vec3 pos, double radius, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, radius, payload);
    }

    /**
     * 向特定玩家发送数据包
     */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}