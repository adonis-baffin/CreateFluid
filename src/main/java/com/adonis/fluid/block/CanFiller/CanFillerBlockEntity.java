package com.adonis.fluid.block.CanFiller;

import java.util.List;

import com.adonis.fluid.config.CFCommonConfig;
import com.adonis.fluid.item.BrassBoxItem;
import com.adonis.fluid.item.CopperCanItem;
import com.adonis.fluid.item.FluidManifestItem;
import com.adonis.fluid.item.PackageRoutingHelper;
import com.adonis.fluid.logistics.api.IFluidLogisticsPackager;
import com.adonis.fluid.logistics.data.FluidNetworkEntry;
import com.adonis.fluid.logistics.data.FluidNetworkSummary;
import com.adonis.fluid.logistics.data.FluidPackagingPlan;
import com.adonis.fluid.logistics.data.FluidRequestKey;
import com.adonis.fluid.logistics.manager.FluidLogisticsManager;
import com.adonis.fluid.logistics.manager.MixedOrderRoutingManager;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.CapManipulationBehaviourBase.InterfaceProvider;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.TankManipulationBehaviour;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.BlockFace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public class CanFillerBlockEntity extends PackagerBlockEntity implements IFluidLogisticsPackager {

	public TankManipulationBehaviour fluidTarget;
	private InventorySummary cachedAvailableFluids;

	// Placeholder handler for the fluid-side IdentifiedInventory. Create's IdentifiedInventory
	// pairs an identifier with an IItemHandler, but our exclusion goes through the identifier
	// (see isTargetingSameInventory), so the handler is never dereferenced.
	private static final IItemHandler EMPTY_ITEM_HANDLER = new ItemStackHandler(0);

	public CanFillerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
		super(typeIn, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		behaviours.add(fluidTarget = new TankManipulationBehaviour(this, InterfaceProvider.oppositeOfBlockFacing()));
	}

	@Override
	public InventorySummary getAvailableItems() {
		InventorySummary summary = new InventorySummary();

		for (FluidNetworkEntry entry : getFluidSummary().entries()) {
			ItemStack manifest = createManifest(entry.key());
			if (!manifest.isEmpty())
				summary.add(manifest, entry.amountMb());
		}

		submitFluidArrivals(cachedAvailableFluids, summary);
		cachedAvailableFluids = summary;
		return summary;
	}

	@Override
	public void attemptToSend(List<PackagingRequest> queuedRequests) {
		if (queuedRequests == null) {
			attemptToSendFluidPassive();
			return;
		}

		if (queuedRequests.isEmpty())
			return;

		PackagingRequest nextRequest = queuedRequests.get(0);
		if (!(nextRequest.item().getItem() instanceof FluidManifestItem)) {
			queuedRequests.remove(0);
			return;
		}

		attemptToSendFluid(queuedRequests);
	}

	private void attemptToSendFluidPassive() {
		if (!heldBox.isEmpty() || animationTicks != 0 || buttonCooldown > 0)
			return;

		for (FluidNetworkEntry entry : getFluidSummary().entries()) {
			FluidStack extracted = executePlan(entry.key(), entry.amountMb());
			if (extracted.isEmpty()) {
				continue;
			}

			ItemStack fluidPackage = CopperCanItem.create(extracted, CFCommonConfig.getFluidPerPackage());
			PackageItem.clearAddress(fluidPackage);
			if (!signBasedAddress.isBlank())
				PackageItem.addAddress(fluidPackage, signBasedAddress);

			heldBox = fluidPackage;
			animationInward = false;
			animationTicks = CYCLE;
			triggerStockCheck();
			notifyUpdate();
			return;
		}
	}

	private void attemptToSendFluid(List<PackagingRequest> queuedRequests) {
		PackagingRequest nextRequest = queuedRequests.get(0);
		FluidRequestKey requestedKey = FluidManifestItem.readKey(nextRequest.item());
		if (requestedKey == null) {
			queuedRequests.remove(0);
			return;
		}

		int requestedMb = nextRequest.getCount();
		FluidStack extracted = executePlan(requestedKey, requestedMb);
		if (extracted.isEmpty()) {
			queuedRequests.remove(0);
			return;
		}

		ItemStack fluidPackage = CopperCanItem.create(extracted, CFCommonConfig.getFluidPerPackage());

		PackageItem.clearAddress(fluidPackage);
		String address = nextRequest.address();
		if (address != null && !address.isBlank())
			PackageItem.addAddress(fluidPackage, address);

		boolean finalPackageAtLink = nextRequest.getCount() <= extracted.getAmount();
		PackageItem.setOrder(fluidPackage, nextRequest.orderId(), nextRequest.linkIndex(),
			nextRequest.finalLink().booleanValue(), nextRequest.packageCounter().getAndIncrement(),
			finalPackageAtLink, nextRequest.context());
		var routing = MixedOrderRoutingManager.resolveAndBind(nextRequest.orderId(), nextRequest.context());
		if (!routing.isEmpty())
			PackageRoutingHelper.setRoutingData(fluidPackage, routing);

		nextRequest.subtract(extracted.getAmount());
		if (nextRequest.isEmpty()) {
			queuedRequests.remove(0);
		}

		if (!heldBox.isEmpty() || animationTicks != 0) {
			queuedExitingPackages.add(new BigItemStack(fluidPackage, 1));
			return;
		}

		heldBox = fluidPackage;
		animationInward = false;
		animationTicks = CYCLE;

		triggerStockCheck();
		notifyUpdate();
	}

	@Override
	public boolean unwrapBox(ItemStack box, boolean simulate) {
		return CopperCanItem.isCopperCan(box) && unwrapCopperCan(box, simulate);
	}

	private boolean unwrapCopperCan(ItemStack box, boolean simulate) {
		if (animationTicks > 0)
			return false;

		IFluidHandler fluidHandler = getFluidHandler();
		if (fluidHandler == null)
			return false;

		FluidStack fluid = CopperCanItem.getFluid(box);
		if (fluid.isEmpty())
			return true;

		int filled = fluidHandler.fill(fluid, IFluidHandler.FluidAction.SIMULATE);
		if (filled < fluid.getAmount())
			return false;

		if (simulate)
			return true;

		fluidHandler.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
		previouslyUnwrapped = box.copyWithCount(1);
		animationInward = true;
		animationTicks = CYCLE;
		triggerStockCheck();
		notifyUpdate();
		return true;
	}

	private void submitFluidArrivals(InventorySummary before, InventorySummary after) {
		if (before == null || level == null || level.isClientSide)
			return;

		java.util.Set<RequestPromiseQueue> promiseQueues = new java.util.HashSet<>();
		for (Direction d : Iterate.directions) {
			BlockPos adjacentPos = worldPosition.relative(d);
			if (!level.isLoaded(adjacentPos))
				continue;

			BlockState adjacentState = level.getBlockState(adjacentPos);

			// Restocker factory gauges keep their target amount stocked via a per-panel promise queue.
			if (AllBlocks.FACTORY_GAUGE.has(adjacentState)
				&& FactoryPanelBlock.connectedDirection(adjacentState) == d
				&& level.getBlockEntity(adjacentPos) instanceof FactoryPanelBlockEntity fpbe
				&& fpbe.restocker) {
				for (FactoryPanelBehaviour behaviour : fpbe.panels.values()) {
					if (behaviour.isActive())
						promiseQueues.add(behaviour.restockerPromises);
				}
			}

			// Stock links carry the network-level queued promises used by autocrafting. Create's own
			// PackagerBlockEntity settles these when a package enters; the can filler must do the same
			// for fluids, otherwise crafting promises for fluid outputs are never cleared.
			if (AllBlocks.STOCK_LINK.has(adjacentState)
				&& PackagerLinkBlock.getConnectedDirection(adjacentState) == d
				&& level.getBlockEntity(adjacentPos) instanceof PackagerLinkBlockEntity plbe) {
				java.util.UUID freqId = plbe.behaviour.freqId;
				if (Create.LOGISTICS.hasQueuedPromises(freqId))
					promiseQueues.add(Create.LOGISTICS.getQueuedPromises(freqId));
			}
		}

		if (promiseQueues.isEmpty())
			return;

		for (BigItemStack entry : after.getStacks()) {
			before.add(entry.stack, -entry.count);
		}

		for (RequestPromiseQueue queue : promiseQueues) {
			for (BigItemStack entry : before.getStacks()) {
				if (entry.count < 0) {
					queue.itemEnteredSystem(entry.stack, -entry.count);
				}
			}
		}
	}

	/**
	 * Fluid-side counterpart to Create's {@code packager.targetInventory.getIdentifiedInventory()}.
	 * Create's IdentifiedInventory / InventoryIdentifier system is item-only and produces nothing
	 * for a fluid tank, so we build the identity ourselves from the can filler's fluid target.
	 * Passing this (instead of {@code null}) to the restock request lets the logistics network
	 * exclude this can filler's own fluid stock, so it can't fulfill its own restock order.
	 */
	public IdentifiedInventory getIdentifiedFluidInventory() {
		if (level == null || fluidTarget == null)
			return null;
		BlockFace target = fluidTarget.getTarget();
		if (target == null)
			return null;

		BlockPos ownKey = fluidInventoryKey(target.getOpposite().getPos());
		InventoryIdentifier identifier = face -> {
			BlockPos otherKey = fluidInventoryKey(face.getPos());
			return otherKey != null && otherKey.equals(ownKey);
		};
		return new IdentifiedInventory(identifier, EMPTY_ITEM_HANDLER);
	}

	@Override
	public boolean isTargetingSameInventory(IdentifiedInventory inventory) {
		// Preserve base item behaviour, then add the fluid-tank identity check that
		// PackagerBlockEntity#isTargetingSameInventory skips (it bails when the item
		// target handler is null, which is always the case for a can filler).
		if (super.isTargetingSameInventory(inventory))
			return true;
		if (inventory == null || inventory.identifier() == null || level == null || fluidTarget == null)
			return false;
		BlockFace target = fluidTarget.getTarget();
		if (target == null)
			return false;
		return inventory.identifier().contains(target.getOpposite());
	}

	/**
	 * Resolves a fluid container position to a stable inventory identity. For Create fluid tanks
	 * and Create: Connected fluid vessels (which subclass {@link FluidTankBlockEntity}) this is the
	 * multiblock controller position, so every block of one tank maps to the same key. For any
	 * other single-block fluid container it is the block position itself. Mirrors how Create's
	 * InventoryIdentifier unifies multiblock item inventories.
	 */
	private BlockPos fluidInventoryKey(BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank) {
			FluidTankBlockEntity controller = tank.getControllerBE();
			return controller != null ? controller.getBlockPos() : pos;
		}
		return pos;
	}

	private IFluidHandler getFluidHandler() {
		IFluidHandler handler = fluidTarget.getInventory();
		if (handler == null) {
			fluidTarget.findNewCapability();
			handler = fluidTarget.getInventory();
		}
		return handler;
	}

	@Override
	public FluidNetworkSummary getFluidSummary() {
		IFluidHandler fluidHandler = getFluidHandler();
		if (fluidHandler == null) {
			return new FluidNetworkSummary();
		}
		return FluidLogisticsManager.summarize(fluidHandler);
	}

	@Override
	public boolean canFulfill(FluidRequestKey key) {
		return getAvailableAmount(key) > 0;
	}

	@Override
	public int getAvailableAmount(FluidRequestKey key) {
		IFluidHandler fluidHandler = getFluidHandler();
		if (fluidHandler == null) {
			return 0;
		}
		return FluidLogisticsManager.getAvailableAmount(fluidHandler, key);
	}

	@Override
	public FluidPackagingPlan planRequest(FluidRequestKey key, int requestedMb) {
		IFluidHandler fluidHandler = getFluidHandler();
		if (fluidHandler == null) {
			return null;
		}
		return FluidLogisticsManager.planPackaging(fluidHandler, key, requestedMb, CFCommonConfig.getFluidPerPackage());
	}

	private FluidStack executePlan(FluidRequestKey key, int requestedMb) {
		IFluidHandler fluidHandler = getFluidHandler();
		if (fluidHandler == null) {
			return FluidStack.EMPTY;
		}

		FluidPackagingPlan plan = planRequest(key, requestedMb);
		if (plan == null) {
			return FluidStack.EMPTY;
		}

		FluidStack requestedFluid = resolveFluid(plan.key(), plan.plannedMb());
		if (requestedFluid.isEmpty()) {
			return FluidStack.EMPTY;
		}

		FluidStack simulated = FluidLogisticsManager.drainForPlan(fluidHandler, requestedFluid, plan,
			IFluidHandler.FluidAction.SIMULATE);
		if (simulated.isEmpty()) {
			return FluidStack.EMPTY;
		}

		return FluidLogisticsManager.drainForPlan(fluidHandler, requestedFluid, plan, IFluidHandler.FluidAction.EXECUTE);
	}

	private FluidStack resolveFluid(FluidRequestKey key, int amountMb) {
		IFluidHandler fluidHandler = getFluidHandler();
		if (fluidHandler == null) {
			return FluidStack.EMPTY;
		}

		for (int i = 0; i < fluidHandler.getTanks(); i++) {
			FluidStack fluid = fluidHandler.getFluidInTank(i);
			if (key.matches(fluid)) {
				return fluid.copyWithAmount(amountMb);
			}
		}

		return FluidStack.EMPTY;
	}

	private ItemStack createManifest(FluidRequestKey key) {
		FluidStack fluid = resolveFluid(key, 1);
		if (fluid.isEmpty())
			return ItemStack.EMPTY;
		return FluidManifestItem.of(fluid, 1);
	}
}
