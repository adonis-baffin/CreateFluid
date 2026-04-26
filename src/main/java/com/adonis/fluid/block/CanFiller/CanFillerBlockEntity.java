package com.adonis.fluid.block.CanFiller;

import java.util.List;

import com.adonis.fluid.config.CFCommonConfig;
import com.adonis.fluid.item.CopperCanItem;
import com.adonis.fluid.item.FluidManifestItem;
import com.adonis.fluid.logistics.api.IFluidLogisticsPackager;
import com.adonis.fluid.logistics.data.FluidNetworkEntry;
import com.adonis.fluid.logistics.data.FluidNetworkSummary;
import com.adonis.fluid.logistics.data.FluidPackagingPlan;
import com.adonis.fluid.logistics.data.FluidRequestKey;
import com.adonis.fluid.logistics.manager.FluidLogisticsManager;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.CapManipulationBehaviourBase.InterfaceProvider;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.TankManipulationBehaviour;
import net.createmod.catnip.data.Iterate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class CanFillerBlockEntity extends PackagerBlockEntity implements IFluidLogisticsPackager {

	public TankManipulationBehaviour fluidTarget;
	private InventorySummary cachedAvailableFluids;

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

		PackageItem.setOrder(fluidPackage, nextRequest.orderId(), nextRequest.linkIndex(),
			nextRequest.finalLink().booleanValue(), nextRequest.packageCounter().getAndIncrement(),
			nextRequest.isEmpty(), nextRequest.context());

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
			if (!level.isLoaded(worldPosition.relative(d)))
				continue;

			BlockState adjacentState = level.getBlockState(worldPosition.relative(d));
			if (!AllBlocks.FACTORY_GAUGE.has(adjacentState))
				continue;
			if (FactoryPanelBlock.connectedDirection(adjacentState) != d)
				continue;
			if (!(level.getBlockEntity(worldPosition.relative(d)) instanceof FactoryPanelBlockEntity fpbe))
				continue;
			if (!fpbe.restocker)
				continue;

			for (FactoryPanelBehaviour behaviour : fpbe.panels.values()) {
				if (!behaviour.isActive())
					continue;
				promiseQueues.add(behaviour.restockerPromises);
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
