package com.adonis.fluid.mixin;

import com.adonis.fluid.mixin.accessor.ArmBlockEntityAccessor;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmPlacementPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
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
     * @reason 修复动力臂不更新交互点的问题
     */
    @Overwrite
    public boolean handle(NetworkEvent.Context context) {
        System.out.println("[SERVER MIXIN] ArmPlacementPacket.handle() 被调用");

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                Level world = player.level();
                if (world != null && world.isLoaded(this.pos)) {
                    BlockEntity blockEntity = world.getBlockEntity(this.pos);
                    if (blockEntity instanceof ArmBlockEntity arm) {
                        ArmBlockEntityAccessor accessor = (ArmBlockEntityAccessor) arm;

                        // 清空现有的交互点列表
                        accessor.getInputs().clear();
                        accessor.getOutputs().clear();

                        // 设置新的交互点标签
                        accessor.setInteractionPointTag(this.receivedTag);

                        // 设置更新标志
                        accessor.setUpdateInteractionPoints(true);

                        // 重置移动状态
                        accessor.setPhase(ArmBlockEntity.Phase.SEARCH_INPUTS);
                        accessor.setChasedPointProgress(0.0F);
                        accessor.setChasedPointIndex(-1);

                        // 立即初始化交互点（通过调用initInteractionPoints）
                        try {
                            java.lang.reflect.Method initMethod = ArmBlockEntity.class.getDeclaredMethod("initInteractionPoints");
                            initMethod.setAccessible(true);
                            initMethod.invoke(arm);
                            System.out.println("[SERVER MIXIN] 强制初始化交互点");
                        } catch (Exception e) {
                            System.out.println("[SERVER MIXIN] 无法调用initInteractionPoints: " + e.getMessage());
                        }

                        // 标记更改
                        arm.setChanged();

                        // 发送数据
                        arm.sendData();

                        // 延迟发送方块更新
                        world.getServer().execute(() -> {
                            world.sendBlockUpdated(pos, arm.getBlockState(), arm.getBlockState(), 3);
                        });

                        System.out.println("[SERVER MIXIN] 更新完成 - 输入: " + accessor.getInputs().size() + ", 输出: " + accessor.getOutputs().size());
                    }
                }
            }
        });

        return true;
    }
}