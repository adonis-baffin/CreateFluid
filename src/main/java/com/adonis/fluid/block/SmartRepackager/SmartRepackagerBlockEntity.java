package com.adonis.fluid.block.SmartRepackager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.adonis.fluid.block.CanFiller.CanFillerBlockEntity;
import com.adonis.fluid.datacomponent.BrassBoxFluidContent.FluidEntry;
import com.adonis.fluid.datacomponent.BrassBoxRoutingData;
import com.adonis.fluid.item.BrassBoxItem;
import com.adonis.fluid.item.CopperCanItem;
import com.adonis.fluid.logistics.data.ContentRoute;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageItem.PackageOrderData;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerItemHandler;
import com.simibubi.create.content.logistics.packager.PackagingRequest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public class SmartRepackagerBlockEntity extends PackagerBlockEntity {
	public SmartRepackagerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
		super(typeIn, pos, state);
	}

	@Override
	public void recheckIfLinksPresent() {
	}

	@Override
	public boolean redstoneModeActive() {
		return true;
	}

	@Override
	public boolean unwrapBox(ItemStack box, boolean simulate) {
		if (animationTicks > 0)
			return false;
		IItemHandler targetInv = targetInventory.getInventory();
		if (targetInv == null || targetInv instanceof PackagerItemHandler)
			return false;
		for (int slot = 0; slot < targetInv.getSlots(); slot++) {
			ItemStack remainder = targetInv.insertItem(slot, box, simulate);
			if (remainder.isEmpty()) {
				if (!simulate) {
					previouslyUnwrapped = box;
					animationInward = true;
					animationTicks = CYCLE;
					notifyUpdate();
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public void attemptToSend(List<PackagingRequest> queuedRequests) {
		if (!heldBox.isEmpty() || animationTicks != 0 || buttonCooldown > 0)
			return;
		if (!queuedExitingPackages.isEmpty())
			return;
		IItemHandler targetInv = targetInventory.getInventory();
		if (targetInv == null || targetInv instanceof PackagerItemHandler)
			return;

		List<BigItemStack> outputs = collectBrassBoxes(targetInv);
		if (outputs.isEmpty())
			return;
		queuedExitingPackages.addAll(outputs);
		notifyUpdate();
	}

	private List<BigItemStack> collectBrassBoxes(IItemHandler targetInv) {
		Map<Integer, List<ItemStack>> collectedByOrder = new LinkedHashMap<>();

		for (int slot = 0; slot < targetInv.getSlots(); slot++) {
			ItemStack extracted = targetInv.extractItem(slot, 1, true);
			if (extracted.isEmpty() || !PackageItem.isPackage(extracted))
				continue;
			if (!extracted.has(com.simibubi.create.AllDataComponents.PACKAGE_ORDER_DATA)) {
				targetInv.extractItem(slot, 1, false);
				return List.of(new BigItemStack(extracted.copy(), 1));
			}
			int orderId = PackageItem.getOrderId(extracted);
			collectedByOrder.computeIfAbsent(orderId, $ -> new LinkedList<>()).add(extracted.copy());
			if (isOrderComplete(collectedByOrder.get(orderId))) {
				List<BigItemStack> created = createBoxesFromOrder(orderId, collectedByOrder.get(orderId));
				removeOrderPackages(targetInv, orderId);
				return created;
			}
		}

		return List.of();
	}

	private void removeOrderPackages(IItemHandler targetInv, int orderId) {
		for (int slot = 0; slot < targetInv.getSlots(); slot++) {
			ItemStack extracted = targetInv.extractItem(slot, 1, true);
			if (extracted.isEmpty() || !PackageItem.isPackage(extracted))
				continue;
			if (PackageItem.getOrderId(extracted) != orderId)
				continue;
			targetInv.extractItem(slot, 1, false);
		}
	}

	private List<BigItemStack> createBoxesFromOrder(int orderId, List<ItemStack> fragments) {
		InventorySummary itemSummary = new InventorySummary();
		Map<String, FluidAccumulator> fluids = new LinkedHashMap<>();
		String address = "";
		BrassBoxRoutingData routing = BrassBoxRoutingData.EMPTY;

		for (ItemStack fragment : fragments) {
			address = PackageItem.getAddress(fragment);
			if (fragment.has(com.adonis.fluid.registry.CFDataComponents.BRASS_BOX_ROUTING.get()))
				routing = BrassBoxItem.getRoutingData(fragment);

			if (CopperCanItem.isCopperCan(fragment)) {
				FluidStack fluid = CopperCanItem.getFluid(fragment);
				if (!fluid.isEmpty()) {
					String key = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString();
					ContentRoute route = routing.routeForFluid(fluid);
					fluids.computeIfAbsent(key, $ -> new FluidAccumulator(fluid.copy(), route))
						.amount += fluid.getAmount();
				}
				continue;
			}

			if (BrassBoxItem.isBrassBox(fragment)) {
				for (FluidEntry fluidEntry : BrassBoxItem.getFluidContent(fragment).fluids()) {
					if (fluidEntry.isEmpty())
						continue;
					String key = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluidEntry.fluid().getFluid()).toString();
					fluids.computeIfAbsent(key, $ -> new FluidAccumulator(fluidEntry.fluid().copy(), fluidEntry.route()))
						.amount += fluidEntry.fluid().getAmount();
				}
			}

			ItemStackHandler contents = PackageItem.getContents(fragment);
			for (int slot = 0; slot < contents.getSlots(); slot++)
				itemSummary.add(contents.getStackInSlot(slot));
		}

		List<ItemStack> itemSlots = flattenItems(itemSummary.getStacks());
		List<FluidEntry> fluidEntries = flattenFluids(fluids);
		List<BigItemStack> output = new ArrayList<>();

		int itemIndex = 0;
		int fluidIndex = 0;
		while (itemIndex < itemSlots.size() || fluidIndex < fluidEntries.size()) {
			ItemStackHandler handler = new ItemStackHandler(PackageItem.SLOTS);
			for (int slot = 0; slot < PackageItem.SLOTS && itemIndex < itemSlots.size(); slot++)
				handler.setStackInSlot(slot, itemSlots.get(itemIndex++));

			List<FluidEntry> fluidChunk = new ArrayList<>();
			for (int i = 0; i < BrassBoxItem.MAX_FLUID_TYPES && fluidIndex < fluidEntries.size(); i++)
				fluidChunk.add(fluidEntries.get(fluidIndex++).copy());

			ItemStack brassBox = BrassBoxItem.create(handler, fluidChunk, routing);
			PackageItem.addAddress(brassBox, address);
			PackageItem.setOrder(brassBox, orderId, 0, true, output.size(), itemIndex >= itemSlots.size() && fluidIndex >= fluidEntries.size(), null);
			output.add(new BigItemStack(brassBox, 1));
		}

		return output;
	}

	private List<ItemStack> flattenItems(List<BigItemStack> items) {
		List<ItemStack> result = new ArrayList<>();
		for (BigItemStack entry : items) {
			int remaining = entry.count;
			while (remaining > 0) {
				int moved = Math.min(remaining, entry.stack.getMaxStackSize());
				result.add(entry.stack.copyWithCount(moved));
				remaining -= moved;
			}
		}
		return result;
	}

	private List<FluidEntry> flattenFluids(Map<String, FluidAccumulator> fluids) {
		List<FluidEntry> result = new ArrayList<>();
		for (FluidAccumulator entry : fluids.values()) {
			int remaining = entry.amount;
			while (remaining > 0) {
				int moved = Math.min(remaining, BrassBoxItem.MAX_FLUID_AMOUNT);
				FluidStack stack = entry.prototype.copyWithAmount(moved);
				if (!stack.isEmpty() && stack.getAmount() > 0)
					result.add(new FluidEntry(stack, entry.route));
				remaining -= moved;
			}
		}
		return result;
	}

	private boolean isOrderComplete(List<ItemStack> fragments) {
		boolean finalLinkReached = false;
		links:
		for (int linkCounter = 0; linkCounter < 1000; linkCounter++) {
			if (finalLinkReached)
				break;
			packages:
			for (int packageCounter = 0; packageCounter < 1000; packageCounter++) {
				for (ItemStack box : fragments) {
					PackageOrderData data = box.get(com.simibubi.create.AllDataComponents.PACKAGE_ORDER_DATA);
					if (data == null)
						continue;
					if (linkCounter != data.linkIndex())
						continue;
					if (packageCounter != data.fragmentIndex())
						continue;
					finalLinkReached = data.isFinalLink();
					if (data.isFinal())
						continue links;
					continue packages;
				}
				return false;
			}
		}
		return true;
	}

	private static class FluidAccumulator {
		private final FluidStack prototype;
		private final ContentRoute route;
		private int amount;

		private FluidAccumulator(FluidStack prototype, ContentRoute route) {
			this.prototype = prototype;
			this.route = route;
		}
	}
}
