package com.adonis.fluid.packet;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.handler.ClipboardAddressHandler;
import com.adonis.fluid.util.ICanFillerData;
import io.netty.buffer.ByteBuf;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClipboardSetAddressPacket(BlockPos pos, String address) implements CustomPacketPayload {
	public static final Type<ClipboardSetAddressPacket> TYPE =
		new Type<>(CreateFluid.asResource("clipboard_set_address"));
	public static final StreamCodec<ByteBuf, ClipboardSetAddressPacket> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, ClipboardSetAddressPacket::pos,
		ByteBufCodecs.STRING_UTF8, ClipboardSetAddressPacket::address,
		ClipboardSetAddressPacket::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public void handle(IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player) || !player.mayBuild())
				return;

			Level level = player.level();
			if (!level.isLoaded(pos))
				return;
			if (!(level.getBlockEntity(pos) instanceof PackagerBlockEntity packager))
				return;
			if (!ClipboardAddressHandler.isClipboardTarget(level.getBlockState(pos)))
				return;
			if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0)
				return;

			ItemStack clipboard = ClipboardAddressHandler.findClipboard(player);
			if (clipboard == null) {
				player.displayClientMessage(
					Component.translatable("create.fluid.clipboard.paste.no_clipboard").withStyle(ChatFormatting.RED), true);
				return;
			}

			String resolvedAddress = address == null ? "" : address.strip();
			if (resolvedAddress.isEmpty()) {
				resolvedAddress = ClipboardAddressHandler.extractFirstAddress(clipboard);
				if (resolvedAddress == null)
					resolvedAddress = "";
			}

			if (resolvedAddress.isEmpty()) {
				player.displayClientMessage(
					Component.translatable("create.fluid.clipboard.paste.no_valid_address").withStyle(ChatFormatting.RED), true);
				PacketDistributor.sendToPlayer(player, new ClipboardAddressParticlePacket(pos));
				return;
			}

			applyAddressToPackager(packager, resolvedAddress, level, pos, player);
		});
	}

	private static void applyAddressToPackager(PackagerBlockEntity packager, String address, Level level, BlockPos pos,
		Player player) {
		if (!(packager instanceof ICanFillerData packagerData))
			return;

		Component blockTypeName = level.getBlockState(pos).getBlock().getName().copy().withStyle(ChatFormatting.WHITE);
		if (checkHasSignAddress(level, pos)) {
			player.displayClientMessage(
				Component.translatable("fluid.clipboard.address_set_by_sign", blockTypeName).withStyle(ChatFormatting.RED),
				true);
			PacketDistributor.sendToPlayer((ServerPlayer) player, new ClipboardAddressParticlePacket(pos));
			return;
		}

		packagerData.setClipboardAddress(address);
		packager.signBasedAddress = address;
		packager.notifyUpdate();
		player.displayClientMessage(
			Component.translatable("fluid.clipboard.address_set", blockTypeName, address).withStyle(ChatFormatting.GREEN),
			true);
		PacketDistributor.sendToPlayer((ServerPlayer) player, new ClipboardAddressParticlePacket(pos));
	}

	private static boolean checkHasSignAddress(Level level, BlockPos pos) {
		for (var side : net.minecraft.core.Direction.values()) {
			var blockEntity = level.getBlockEntity(pos.relative(side));
			if (!(blockEntity instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign))
				continue;

			for (boolean front : new boolean[] {true, false}) {
				var text = sign.getText(front);
				StringBuilder address = new StringBuilder();
				for (Component component : text.getMessages(false)) {
					String string = component.getString();
					if (!string.isBlank())
						address.append(string.trim()).append(" ");
				}
				if (!address.toString().isBlank())
					return true;
			}
		}
		return false;
	}
}
