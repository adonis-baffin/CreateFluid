package com.adonis.fluid.packet;

import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public class FrogportConnectionFeedbackPacket extends SimplePacketBase {
    private final boolean success;
    private final BlockPos frogportPos;
    private final String messageKey;

    public static FrogportConnectionFeedbackPacket success(BlockPos frogportPos) {
        return new FrogportConnectionFeedbackPacket(true, frogportPos, "create_fluid.baton.frogport.connection_set");
    }

    public static FrogportConnectionFeedbackPacket failure(BlockPos frogportPos, String messageKey) {
        return new FrogportConnectionFeedbackPacket(false, frogportPos, messageKey);
    }

    public FrogportConnectionFeedbackPacket(boolean success, BlockPos frogportPos, String messageKey) {
        this.success = success;
        this.frogportPos = frogportPos;
        this.messageKey = messageKey;
    }

    public FrogportConnectionFeedbackPacket(FriendlyByteBuf buffer) {
        this.success = buffer.readBoolean();
        this.frogportPos = buffer.readBlockPos();
        this.messageKey = buffer.readUtf();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(success);
        buffer.writeBlockPos(frogportPos);
        buffer.writeUtf(messageKey);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.level != null) {
                    LangBuilder builder = com.simibubi.create.foundation.utility.CreateLang.builder().translate(this.messageKey);
                    if (this.success) {
                        builder.color(10416499).sendStatus(mc.player);
                        mc.level.playLocalSound(
                                (double) this.frogportPos.getX() + 0.5,
                                (double) this.frogportPos.getY() + 0.5,
                                (double) this.frogportPos.getZ() + 0.5,
                                SoundEvents.NOTE_BLOCK_CHIME.get(),
                                SoundSource.BLOCKS,
                                0.8F,
                                1.0F,
                                false
                        );
                    } else {
                        builder.color(16736625).sendStatus(mc.player);
                    }
                }
            });
        });
        return true;
    }
}
