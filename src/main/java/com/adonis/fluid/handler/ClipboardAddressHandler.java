package com.adonis.fluid.handler;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.packet.ClipboardCopyAddressPacket;
import com.adonis.fluid.packet.ClipboardSetAddressPacket;
import com.adonis.fluid.registry.CFBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.trains.track.TrackBlockOutline;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = CreateFluid.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class ClipboardAddressHandler {

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
		if (event.getLevel().isClientSide)
			handleCopy(event);
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onClickInput(InputEvent.InteractionKeyMappingTriggered event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen != null || mc.player == null || mc.level == null)
			return;
		if (event.getKeyMapping() != mc.options.keyAttack)
			return;
		if (!isHoldingClipboard(mc.player))
			return;
		if (!(mc.hitResult instanceof BlockHitResult target) || target.getType() != HitResult.Type.BLOCK)
			return;
		ItemStack clipboard = findClipboard(mc.player);
		BlockPos pos = target.getBlockPos();
		if (!canPasteTo(clipboard, mc.level.getBlockEntity(pos), mc.level, pos))
			return;

		event.setCanceled(true);
		event.setSwingHand(true);
		performPaste(mc.player, mc.level, pos, clipboard);
	}

	@SubscribeEvent
	public static void onRenderHighlight(RenderHighlightEvent.Block event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.player.isSpectator())
			return;
		if (!isHoldingClipboard(mc.player))
			return;

		BlockHitResult target = event.getTarget();
		BlockPos pos = target.getBlockPos();
		if (!mc.level.getWorldBorder().isWithinBounds(pos))
			return;

		BlockState state = mc.level.getBlockState(pos);
		if (!isClipboardTarget(state))
			return;

		VoxelShape shape = state.getShape(mc.level, pos);
		if (shape.isEmpty())
			return;

		var ms = event.getPoseStack();
		var vb = event.getMultiBufferSource().getBuffer(RenderType.lines());
		var camPos = event.getCamera().getPosition();

		ms.pushPose();
		ms.translate(pos.getX() - camPos.x, pos.getY() - camPos.y, pos.getZ() - camPos.z);
		TrackBlockOutline.renderShape(shape, ms, vb, true);
		event.setCanceled(true);
		ms.popPose();
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.screen != null)
			return;

		ItemStack clipboard = findClipboard(mc.player);
		if (clipboard == null)
			return;
		if (!(mc.hitResult instanceof BlockHitResult target) || target.getType() != HitResult.Type.BLOCK)
			return;

		BlockPos pos = target.getBlockPos();
		BlockState state = mc.level.getBlockState(pos);
		if (!isClipboardTarget(state))
			return;

		BlockEntity be = mc.level.getBlockEntity(pos);
		boolean canCopy = canCopyFrom(be);
		boolean canPaste = canPasteTo(clipboard, be, mc.level, pos);
		if (!canCopy && !canPaste)
			return;

		List<MutableComponent> tip = new ArrayList<>();
		tip.add(CreateLang.translateDirect("clipboard.actions"));
		if (canCopy)
			tip.add(CreateLang.translateDirect("clipboard.to_copy", Component.keybind("key.use")));
		if (canPaste)
			tip.add(CreateLang.translateDirect("clipboard.to_paste", Component.keybind("key.attack")));
		CreateClient.VALUE_SETTINGS_HANDLER.showHoverTip(tip);
	}

	private static void handleCopy(PlayerInteractEvent.RightClickBlock event) {
		Player player = event.getEntity();
		ItemStack clipboard = findClipboard(player);
		if (clipboard == null)
			return;

		Level level = event.getLevel();
		BlockPos pos = event.getPos();
		BlockEntity be = level.getBlockEntity(pos);
		if (!canCopyFrom(be))
			return;

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);

		String address = getAddressFromBlockEntity(be);
		if (address == null || address.isBlank()) {
			CreateLang.builder()
				.translate("fluid.clipboard.copy.no_address")
				.style(ChatFormatting.RED)
				.sendStatus(player);
			player.swing(event.getHand());
			return;
		}

		CatnipServices.NETWORK.sendToServer(new ClipboardCopyAddressPacket(pos));
		player.swing(event.getHand());

		writeAddressToClipboard(clipboard, address);
		InteractionHand clipboardHand = getClipboardHand(player);
		if (clipboardHand != null)
			player.setItemInHand(clipboardHand, clipboard);
		CreateLang.builder()
			.translate("fluid.clipboard.copy.success", address)
			.style(ChatFormatting.GREEN)
			.sendStatus(player);
	}

	private static void performPaste(Player player, Level level, BlockPos pos, ItemStack clipboard) {
		String address = extractFirstAddress(clipboard);
		if (address == null) {
			CreateLang.builder()
				.translate("create.fluid.clipboard.paste.no_valid_address")
				.style(ChatFormatting.RED)
				.sendStatus(player);
			return;
		}

		CatnipServices.NETWORK.sendToServer(new ClipboardSetAddressPacket(pos, address));
	}

	public static boolean isClipboardTarget(BlockState state) {
		return AllBlocks.PACKAGER.has(state)
			|| AllBlocks.REPACKAGER.has(state)
			|| state.is(CFBlocks.CAN_FILLER.get());
	}

	private static boolean canCopyFrom(@Nullable BlockEntity be) {
		if (!(be instanceof PackagerBlockEntity packager))
			return false;

		String address = packager.signBasedAddress;
		if (address != null && !address.isBlank())
			return true;
		if (be instanceof com.adonis.fluid.util.ICanFillerData data) {
			address = data.getClipboardAddress();
			return address != null && !address.isBlank();
		}
		return false;
	}

	private static boolean canPasteTo(@Nullable ItemStack clipboard, @Nullable BlockEntity be, Level level, BlockPos pos) {
		if (clipboard == null || !(be instanceof PackagerBlockEntity) || !isClipboardTarget(level.getBlockState(pos)))
			return false;
		if (extractFirstAddress(clipboard) == null)
			return false;

		for (var side : net.minecraft.core.Direction.values()) {
			BlockEntity neighbor = level.getBlockEntity(pos.relative(side));
			if (!(neighbor instanceof SignBlockEntity sign))
				continue;

			for (boolean front : new boolean[] {true, false}) {
				var text = sign.getText(front);
				StringBuilder sb = new StringBuilder();
				for (var component : text.getMessages(false)) {
					String s = component.getString();
					if (!s.isBlank())
						sb.append(s.trim()).append(" ");
				}
				if (sb.length() > 0)
					return false;
			}
		}

		return true;
	}

	@Nullable
	private static String getAddressFromBlockEntity(BlockEntity be) {
		if (be instanceof PackagerBlockEntity packager) {
			String address = packager.signBasedAddress;
			if (address != null && !address.isBlank())
				return address;
			if (be instanceof com.adonis.fluid.util.ICanFillerData data) {
				address = data.getClipboardAddress();
				if (address != null && !address.isBlank())
					return address;
			}
		}
		return null;
	}

	@Nullable
	public static ItemStack findClipboard(Player player) {
		ItemStack main = player.getMainHandItem();
		if (AllBlocks.CLIPBOARD.isIn(main))
			return main;
		ItemStack off = player.getOffhandItem();
		if (AllBlocks.CLIPBOARD.isIn(off))
			return off;
		return null;
	}

	@Nullable
	public static InteractionHand getClipboardHand(Player player) {
		if (AllBlocks.CLIPBOARD.isIn(player.getMainHandItem()))
			return InteractionHand.MAIN_HAND;
		if (AllBlocks.CLIPBOARD.isIn(player.getOffhandItem()))
			return InteractionHand.OFF_HAND;
		return null;
	}

	private static boolean isHoldingClipboard(Player player) {
		return AllBlocks.CLIPBOARD.isIn(player.getMainHandItem()) || AllBlocks.CLIPBOARD.isIn(player.getOffhandItem());
	}

	public static void writeAddressToClipboard(ItemStack clipboard, String address) {
		List<List<ClipboardEntry>> pages = new ArrayList<>(ClipboardEntry.readAll(clipboard));
		if (pages.isEmpty()) {
			pages = new ArrayList<>();
			pages.add(new ArrayList<>());
		}

		List<ClipboardEntry> firstPage = new ArrayList<>(pages.get(0));
		pages.set(0, firstPage);
		boolean found = false;
		for (ClipboardEntry entry : firstPage) {
			String text = entry.text.getString();
			if (text.startsWith("#")) {
				entry.text = Component.literal("#" + address);
				found = true;
				break;
			}
		}

		if (!found)
			firstPage.add(new ClipboardEntry(false, Component.literal("#" + address)));

		if (trySavePagesLegacy(clipboard, pages)) {
			trySwitchClipboardTypeLegacy(clipboard, ClipboardOverrides.ClipboardType.WRITTEN);
			return;
		}

		trySavePagesViaContentComponent(clipboard, pages);
	}

	private static boolean trySavePagesLegacy(ItemStack clipboard, List<List<ClipboardEntry>> pages) {
		try {
			var saveAll = ClipboardEntry.class.getMethod("saveAll", List.class, ItemStack.class);
			saveAll.invoke(null, pages, clipboard);
			return true;
		} catch (ReflectiveOperationException ignored) {
			return false;
		}
	}

	private static boolean trySavePagesViaContentComponent(ItemStack clipboard, List<List<ClipboardEntry>> pages) {
		try {
			Class<?> allDataComponents = Class.forName("com.simibubi.create.AllDataComponents");
			Class<?> clipboardContentClass =
				Class.forName("com.simibubi.create.content.equipment.clipboard.ClipboardContent");
			Object componentType = allDataComponents.getField("CLIPBOARD_CONTENT").get(null);
			Object empty = clipboardContentClass.getField("EMPTY").get(null);

			var getOrDefault = findMethod(ItemStack.class, "getOrDefault", 2);
			Object content = getOrDefault != null ? getOrDefault.invoke(clipboard, componentType, empty) : empty;
			Object updated = clipboardContentClass.getMethod("setPages", List.class).invoke(content, pages);
			updated = clipboardContentClass.getMethod("setType", ClipboardOverrides.ClipboardType.class)
				.invoke(updated, ClipboardOverrides.ClipboardType.WRITTEN);

			var set = findMethod(ItemStack.class, "set", 2);
			if (set == null)
				return false;
			set.invoke(clipboard, componentType, updated);
			return true;
		} catch (ReflectiveOperationException ignored) {
			return false;
		}
	}

	private static boolean trySwitchClipboardTypeLegacy(ItemStack clipboard, ClipboardOverrides.ClipboardType type) {
		try {
			var switchTo = ClipboardOverrides.class.getMethod("switchTo", ClipboardOverrides.ClipboardType.class, ItemStack.class);
			switchTo.invoke(null, type, clipboard);
			return true;
		} catch (ReflectiveOperationException ignored) {
			return false;
		}
	}

	@Nullable
	private static java.lang.reflect.Method findMethod(Class<?> type, String name, int parameterCount) {
		for (var method : type.getMethods()) {
			if (method.getName().equals(name) && method.getParameterCount() == parameterCount)
				return method;
		}
		return null;
	}

	@Nullable
	public static String extractFirstAddress(ItemStack clipboardItem) {
		if (clipboardItem == null || clipboardItem.isEmpty())
			return null;
		List<List<ClipboardEntry>> pages = ClipboardEntry.readAll(clipboardItem);
		for (List<ClipboardEntry> page : pages) {
			for (ClipboardEntry entry : page) {
				String text = entry.text.getString();
				if (text != null && text.startsWith("#") && !text.substring(1).isBlank())
					return text.substring(1).stripLeading();
			}
		}
		return null;
	}
}
