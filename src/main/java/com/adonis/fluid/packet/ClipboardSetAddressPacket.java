package com.adonis.fluid.packet;

import com.adonis.fluid.util.ClipboardAddressUtil;
import com.adonis.fluid.util.IPackagerData;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraftforge.network.NetworkEvent;

public class ClipboardSetAddressPacket extends SimplePacketBase {
    private final BlockPos pos;

    public ClipboardSetAddressPacket(BlockPos pos) {
        this.pos = pos;
    }

    public ClipboardSetAddressPacket(FriendlyByteBuf buffer) {
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
            if (player == null || !player.mayBuild()) {
                return;
            }

            Level level = player.level();
            if (!level.isLoaded(this.pos)) {
                return;
            }

            if (!(level.getBlockEntity(this.pos) instanceof PackagerBlockEntity packager)) {
                return;
            }

            if (!AllBlocks.PACKAGER.has(level.getBlockState(this.pos)) && !AllBlocks.REPACKAGER.has(level.getBlockState(this.pos))) {
                return;
            }

            if (player.distanceToSqr((double) this.pos.getX() + 0.5, (double) this.pos.getY() + 0.5, (double) this.pos.getZ() + 0.5) > 64.0) {
                return;
            }

            ItemStack stack = player.getMainHandItem();
            if (!AllBlocks.CLIPBOARD.isIn(stack)) {
                return;
            }

            String address = ClipboardAddressUtil.extractFirstAddress(stack);
            if (address == null) {
                player.displayClientMessage(Component.translatable("create.create_fluid.clipboard.no_valid_address").withStyle(ChatFormatting.RED), true);
                com.simibubi.create.AllPackets.getChannel().send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                        new ClipboardAddressParticlePacket(this.pos)
                );
                return;
            }

            applyAddressToPackager(packager, address, level, this.pos, player);
        });
        return true;
    }

    private static void applyAddressToPackager(PackagerBlockEntity packager, String address, Level level, BlockPos pos, Player player) {
        if (!(packager instanceof IPackagerData packagerData)) return;

        boolean isRepackager = packager instanceof RepackagerBlockEntity;
        String blockTypeName = isRepackager ? "Repackager" : "Packager";
        boolean hasSignAddress = checkHasSignAddress(level, pos);
        if (hasSignAddress) {
            player.displayClientMessage(
                    Component.translatable("create.create_fluid.clipboard.address_set_by_sign", blockTypeName).withStyle(ChatFormatting.RED), true
            );
            com.simibubi.create.AllPackets.getChannel().send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> (ServerPlayer) player),
                    new ClipboardAddressParticlePacket(pos)
            );
        } else {
            packagerData.setClipboardAddress(address);
            packager.signBasedAddress = address;
            packager.notifyUpdate();
            player.displayClientMessage(Component.translatable("create.create_fluid.clipboard.address_set", blockTypeName, address), true);
            com.simibubi.create.AllPackets.getChannel().send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> (ServerPlayer) player),
                    new ClipboardAddressParticlePacket(pos)
            );
        }
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
