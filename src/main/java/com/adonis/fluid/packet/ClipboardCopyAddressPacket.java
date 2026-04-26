package com.adonis.fluid.packet;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.handler.ClipboardAddressHandler;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClipboardCopyAddressPacket(BlockPos pos) implements CustomPacketPayload {
	public static final Type<ClipboardCopyAddressPacket> TYPE =
		new Type<>(CreateFluid.asResource("clipboard_copy_address"));
	public static final StreamCodec<ByteBuf, ClipboardCopyAddressPacket> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, ClipboardCopyAddressPacket::pos, ClipboardCopyAddressPacket::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public void handle(IPayloadContext context) {
		context.enqueueWork(() -> {
			Player player = context.player();
			if (!(player instanceof ServerPlayer serverPlayer) || !player.mayBuild())
				return;

			Level level = player.level();
			if (!level.isLoaded(pos))
				return;
			if (!(level.getBlockEntity(pos) instanceof PackagerBlockEntity packager))
				return;
			if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0)
				return;

			ItemStack clipboard = ClipboardAddressHandler.findClipboard(player);
			if (clipboard == null)
				return;

			String address = packager.signBasedAddress;
			if ((address == null || address.isBlank()) && packager instanceof com.adonis.fluid.util.ICanFillerData data)
				address = data.getClipboardAddress();
			if (address == null || address.isBlank())
				return;

			ClipboardAddressHandler.writeAddressToClipboard(clipboard, address);
			InteractionHand clipboardHand = ClipboardAddressHandler.getClipboardHand(player);
			if (clipboardHand != null)
				player.setItemInHand(clipboardHand, clipboard);

			serverPlayer.containerMenu.broadcastChanges();
			PacketDistributor.sendToPlayer(serverPlayer, new ClipboardAddressParticlePacket(pos));
		});
	}
}
