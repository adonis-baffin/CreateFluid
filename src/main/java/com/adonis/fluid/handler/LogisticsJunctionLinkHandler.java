package com.adonis.fluid.handler;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.block.LogisticsJunction.LogisticsJunctionBlockEntity;
import com.adonis.fluid.config.CFCommonConfig;
import com.adonis.fluid.item.BatonItem;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = CreateFluid.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class LogisticsJunctionLinkHandler {
	private static final String LINK_TAG = "LogisticsJunctionLink";
	private static final String POS_TAG = "SelectedPos";
	private static final String DIM_TAG = "SelectedDim";

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (event.getHand() != InteractionHand.MAIN_HAND)
			return;

		Player player = event.getEntity();
		ItemStack heldItem = player.getMainHandItem();
		if (!(heldItem.getItem() instanceof BatonItem))
			return;

		PendingLink pendingLink = readPendingLink(heldItem);
		if (pendingLink == null && BatonInteractionHandler.isInSelectionMode())
			return;

		Level level = event.getLevel();
		BlockPos clickedPos = event.getPos();
		Direction clickedFace = event.getFace();
		if (clickedFace == null)
			return;

		if (level.getBlockEntity(clickedPos) instanceof LogisticsJunctionBlockEntity junction) {
			event.setCanceled(true);
			event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
			if (level.isClientSide)
				return;

			if (player.isShiftKeyDown()) {
				junction.clearFlexibleTarget();
				clearPendingLink(heldItem);
				CreateLang.builder()
					.translate("create.fluid.logistics_junction.link_cleared")
					.style(ChatFormatting.GRAY)
					.sendStatus(player);
				return;
			}

			writePendingLink(heldItem, clickedPos, level.dimension().location());
			CreateLang.builder()
				.translate("create.fluid.logistics_junction.link_source_set")
				.style(ChatFormatting.GOLD)
				.sendStatus(player);
			return;
		}

		if (pendingLink == null)
			return;

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
		if (level.isClientSide)
			return;

		if (!pendingLink.dimension().equals(level.dimension().location())) {
			CreateLang.builder()
				.translate("create.fluid.logistics_junction.link_wrong_dimension")
				.style(ChatFormatting.RED)
				.sendStatus(player);
			return;
		}

		if (!pendingLink.sourcePos().closerThan(clickedPos, CFCommonConfig.getLogisticsJunctionLinkRange() + 0.5)) {
			CreateLang.builder()
				.translate("create.fluid.logistics_junction.link_too_far")
				.style(ChatFormatting.RED)
				.sendStatus(player);
			return;
		}

		if (clickedPos.equals(pendingLink.sourcePos())) {
			CreateLang.builder()
				.translate("create.fluid.logistics_junction.link_invalid_target")
				.style(ChatFormatting.RED)
				.sendStatus(player);
			return;
		}

		if (!(level.getBlockEntity(pendingLink.sourcePos()) instanceof LogisticsJunctionBlockEntity junction)) {
			clearPendingLink(heldItem);
			CreateLang.builder()
				.translate("create.fluid.logistics_junction.link_source_missing")
				.style(ChatFormatting.RED)
				.sendStatus(player);
			return;
		}

		boolean hasItemTarget = level.getCapability(Capabilities.ItemHandler.BLOCK, clickedPos, clickedFace) != null;
		boolean hasFluidTarget = level.getCapability(Capabilities.FluidHandler.BLOCK, clickedPos, clickedFace) != null;
		if (!hasItemTarget && !hasFluidTarget) {
			CreateLang.builder()
				.translate("create.fluid.logistics_junction.link_invalid_target")
				.style(ChatFormatting.RED)
				.sendStatus(player);
			return;
		}

		junction.setFlexibleTarget(clickedPos, clickedFace);
		clearPendingLink(heldItem);
		CreateLang.builder()
			.translate("create.fluid.logistics_junction.link_success")
			.style(ChatFormatting.GREEN)
			.sendStatus(player);
	}

	private static PendingLink readPendingLink(ItemStack stack) {
		CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		if (!root.contains(LINK_TAG, CompoundTag.TAG_COMPOUND))
			return null;
		CompoundTag tag = root.getCompound(LINK_TAG);
		if (!tag.contains(POS_TAG) || !tag.contains(DIM_TAG))
			return null;
		return new PendingLink(NbtUtils.readBlockPos(tag, POS_TAG).orElse(BlockPos.ZERO),
			ResourceLocation.parse(tag.getString(DIM_TAG)));
	}

	private static void writePendingLink(ItemStack stack, BlockPos pos, ResourceLocation dimension) {
		CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		CompoundTag tag = new CompoundTag();
		tag.put(POS_TAG, NbtUtils.writeBlockPos(pos));
		tag.putString(DIM_TAG, dimension.toString());
		root.put(LINK_TAG, tag);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
	}

	private static void clearPendingLink(ItemStack stack) {
		CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		root.remove(LINK_TAG);
		if (root.isEmpty())
			stack.remove(DataComponents.CUSTOM_DATA);
		else
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
	}

	private record PendingLink(BlockPos sourcePos, ResourceLocation dimension) {
	}
}
