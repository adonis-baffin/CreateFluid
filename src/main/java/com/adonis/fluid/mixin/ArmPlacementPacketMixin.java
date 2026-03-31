package com.adonis.fluid.mixin;

import com.adonis.fluid.mixin.accessor.ArmBlockEntityAccessor;
import com.adonis.fluid.packet.ArmInteractionPointSyncPacket;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmPlacementPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ArmPlacementPacket.class, remap = false)
public class ArmPlacementPacketMixin {

    @Shadow
    @Final
    private ListTag tag;

    @Shadow
    @Final
    private BlockPos pos;

    @Inject(method = "handle", at = @At("TAIL"))
    private void afterHandle(ServerPlayer player, CallbackInfo ci) {
        Level world = player.level();
        if (!world.isLoaded(pos))
            return;

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof ArmBlockEntity arm))
            return;

        ArmBlockEntityAccessor accessor = (ArmBlockEntityAccessor) arm;

        // 强制标记需要更新
        accessor.setUpdateInteractionPoints(true);

        // 立即初始化
        try {
            java.lang.reflect.Method initMethod = ArmBlockEntity.class.getDeclaredMethod("initInteractionPoints");
            initMethod.setAccessible(true);
            initMethod.invoke(arm);
        } catch (Exception e) {
            // 静默处理
        }

        // 重置状态
        accessor.setPhase(ArmBlockEntity.Phase.SEARCH_INPUTS);
        accessor.setChasedPointProgress(0.0F);
        accessor.setChasedPointIndex(-1);

        arm.setChanged();

        // 关键：发送同步包给所有附近的客户端
        if (world instanceof ServerLevel serverLevel) {
            ArmInteractionPointSyncPacket syncPacket = new ArmInteractionPointSyncPacket(pos, tag);
            
            try {
                PacketDistributor.sendToPlayersTrackingChunk(
                        serverLevel,
                        new ChunkPos(pos),
                        syncPacket
                );
            } catch (Exception e) {
                // 静默处理
            }
        }
    }
}