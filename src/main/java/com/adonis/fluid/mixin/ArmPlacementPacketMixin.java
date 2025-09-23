package com.adonis.fluid.mixin;

import com.adonis.fluid.mixin.accessor.ArmBlockEntityAccessor;
import com.adonis.fluid.packet.ArmInteractionPointSyncPacket;
import com.simibubi.create.AllPackets;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmPlacementPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = ArmPlacementPacket.class, remap = false)
public class ArmPlacementPacketMixin {

    @Shadow
    private ListTag receivedTag;

    @Shadow
    private BlockPos pos;

    /**
     * @author Adonis
     * @reason 修复动力臂交互点同步问题
     */
    @Overwrite
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                Level world = player.level();
                if (world != null && world.isLoaded(this.pos)) {
                    BlockEntity blockEntity = world.getBlockEntity(this.pos);
                    if (blockEntity instanceof ArmBlockEntity arm) {
                        ArmBlockEntityAccessor accessor = (ArmBlockEntityAccessor) arm;

                        // 更新服务端
                        accessor.getInputs().clear();
                        accessor.getOutputs().clear();
                        accessor.setInteractionPointTag(this.receivedTag);
                        accessor.setUpdateInteractionPoints(true);
                        accessor.setPhase(ArmBlockEntity.Phase.SEARCH_INPUTS);
                        accessor.setChasedPointProgress(0.0F);
                        accessor.setChasedPointIndex(-1);

                        arm.setChanged();

                        // 立即初始化
                        try {
                            java.lang.reflect.Method initMethod = ArmBlockEntity.class.getDeclaredMethod("initInteractionPoints");
                            initMethod.setAccessible(true);
                            initMethod.invoke(arm);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        // 关键：发送同步包给所有附近的客户端
                        if (world instanceof ServerLevel) {
                            AllPackets.getChannel().send(
                                    PacketDistributor.TRACKING_CHUNK.with(
                                            () -> world.getChunkAt(pos)
                                    ),
                                    new ArmInteractionPointSyncPacket(pos, this.receivedTag)
                            );
                        }
                    }
                }
            }
        });
        return true;
    }
}