package com.adonis.fluid.packet;

import com.adonis.fluid.util.IPackagerData;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraftforge.network.NetworkEvent;

public class PackagerClearAddressPacket extends SimplePacketBase {
    private final BlockPos pos;

    public PackagerClearAddressPacket(BlockPos pos) {
        this.pos = pos;
    }

    public PackagerClearAddressPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.mayBuild()) return;

            Level world = player.level();
            if (!world.isLoaded(this.pos)) return;

            if (!(world.getBlockEntity(this.pos) instanceof PackagerBlockEntity packager)) return;

            if (player.distanceToSqr(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) > 64.0) return;

            boolean isRepackager = packager instanceof RepackagerBlockEntity;
            String blockTypeName = isRepackager ? "Repackager" : "Packager";
            boolean hasSignAddress = checkHasSignAddress(world, this.pos);
            if (hasSignAddress) {
                player.displayClientMessage(Component.translatable("create.create_fluid.baton.packager.address_cannot_clear_sign").withStyle(ChatFormatting.RED), true);
                return;
            }

            if (packager instanceof IPackagerData packagerData) {
                String clipboardAddress = packagerData.getClipboardAddress();
                if (clipboardAddress.isBlank()) {
                    player.displayClientMessage(Component.translatable("create.create_fluid.baton.packager.address_already_empty").withStyle(ChatFormatting.GRAY), true);
                    return;
                }

                packager.signBasedAddress = "";
                packagerData.setClipboardAddress("");
                packager.notifyUpdate();
                player.displayClientMessage(Component.translatable("create.create_fluid.baton.packager.address_cleared").withStyle(ChatFormatting.WHITE), true);
            }
        });
        return true;
    }

    private static boolean checkHasSignAddress(Level level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            BlockEntity blockEntity = level.getBlockEntity(pos.relative(side));
            if (blockEntity instanceof SignBlockEntity sign) {
                for (boolean front : new boolean[]{true, false}) {
                    SignText text = sign.getText(front);
                    String address = "";

                    for (net.minecraft.network.chat.Component component : text.getMessages(false)) {
                        String string = component.getString();
                        if (!string.isBlank()) {
                            address = address + string.trim() + " ";
                        }
                    }

                    if (!address.isBlank()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
