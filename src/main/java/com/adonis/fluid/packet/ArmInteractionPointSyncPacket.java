package com.adonis.fluid.packet;

import com.adonis.fluid.mixin.accessor.ArmBlockEntityAccessor;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public class ArmInteractionPointSyncPacket extends SimplePacketBase {

    private BlockPos pos;
    private ListTag pointsTag;

    public ArmInteractionPointSyncPacket(BlockPos pos, ListTag pointsTag) {
        this.pos = pos;
        this.pointsTag = pointsTag;
    }

    public ArmInteractionPointSyncPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        CompoundTag nbt = buffer.readNbt();
        if (nbt != null) {
            this.pointsTag = nbt.getList("Points", 10);
        } else {
            this.pointsTag = new ListTag();
        }
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        CompoundTag nbt = new CompoundTag();
        nbt.put("Points", pointsTag);
        buffer.writeNbt(nbt);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                handleClient();
            });
        });
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    private void handleClient() {
        Level world = Minecraft.getInstance().level;
        if (world != null) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof ArmBlockEntity arm) {
                // 使用 Accessor 来访问私有字段
                ArmBlockEntityAccessor accessor = (ArmBlockEntityAccessor) arm;

                // 清空旧的交互点
                accessor.getInputs().clear();
                accessor.getOutputs().clear();

                // 设置新的标签
                accessor.setInteractionPointTag(pointsTag);

                // 设置更新标志为true，让它在下一个tick自动初始化
                accessor.setUpdateInteractionPoints(true);

                // 重置状态
                accessor.setPhase(ArmBlockEntity.Phase.SEARCH_INPUTS);
                accessor.setChasedPointProgress(0.0F);
                accessor.setChasedPointIndex(-1);

                // 标记需要更新
                arm.setChanged();

                // 尝试立即初始化交互点
                try {
                    java.lang.reflect.Method initMethod = ArmBlockEntity.class.getDeclaredMethod("initInteractionPoints");
                    initMethod.setAccessible(true);
                    initMethod.invoke(arm);
                } catch (Exception e) {
                }
            }
        }
    }
}