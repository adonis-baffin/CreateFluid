package com.adonis.fluid.block.fluidpackager;

import java.util.List;

import com.adonis.fluid.config.CFCommonConfig;
import com.adonis.fluid.item.FluidManifestItem;
import com.adonis.fluid.item.FluidPackageItem;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.CapManipulationBehaviourBase.InterfaceProvider;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.TankManipulationBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class FluidPackagerBlockEntity extends PackagerBlockEntity {

	public TankManipulationBehaviour fluidTarget;

	public FluidPackagerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
		super(typeIn, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		behaviours.add(fluidTarget = new TankManipulationBehaviour(this, InterfaceProvider.oppositeOfBlockFacing()));
	}

	@Override
	public InventorySummary getAvailableItems() {
		InventorySummary summary = super.getAvailableItems();

		IFluidHandler fluidHandler = getFluidHandler();
		if (fluidHandler != null) {
			for (int i = 0; i < fluidHandler.getTanks(); i++) {
				FluidStack fluid = fluidHandler.getFluidInTank(i);
				if (!fluid.isEmpty()) {
					// count 单位就是 mB，直接上报实际 mB 数量
					int mb = fluid.getAmount();
					if (mb <= 0) mb = 1; // 至少显示 1mB，避免 0 导致条目消失
					ItemStack manifest = FluidManifestItem.of(fluid, fluid.getAmount());
					summary.add(manifest, mb);
				}
			}
		}

		return summary;
	}

	@Override
	public void attemptToSend(List<PackagingRequest> queuedRequests) {
		if (queuedRequests == null) {
			// 红石/被动模式：先尝试原版物品打包
			super.attemptToSend(null);
			// 如果原版没有打出包裹，尝试被动流体打包
			if (heldBox.isEmpty() && animationTicks == 0) {
				attemptToSendFluidPassive();
			}
			return;
		}

		if (queuedRequests.isEmpty())
			return;

		PackagingRequest nextRequest = queuedRequests.get(0);
		if (!(nextRequest.item().getItem() instanceof FluidManifestItem)) {
			super.attemptToSend(queuedRequests);
			return;
		}

		attemptToSendFluid(queuedRequests);
	}

	private void attemptToSendFluidPassive() {
		if (!heldBox.isEmpty() || animationTicks != 0 || buttonCooldown > 0)
			return;

		IFluidHandler fluidHandler = getFluidHandler();
		if (fluidHandler == null)
			return;

		int fluidPerPackage = CFCommonConfig.getFluidPerPackage();

		for (int i = 0; i < fluidHandler.getTanks(); i++) {
			FluidStack fluid = fluidHandler.getFluidInTank(i);
			if (fluid.isEmpty())
				continue;

			int available = fluid.getAmount();
			int toExtract = Math.min(available, fluidPerPackage);
			if (toExtract <= 0)
				continue;

			FluidStack extracted = fluidHandler.drain(fluid.copyWithAmount(toExtract), IFluidHandler.FluidAction.SIMULATE);
			if (extracted.isEmpty())
				continue;

			extracted = fluidHandler.drain(fluid.copyWithAmount(toExtract), IFluidHandler.FluidAction.EXECUTE);
			if (extracted.isEmpty())
				continue;

			ItemStack fluidPackage = FluidPackageItem.create(extracted, fluidPerPackage);
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
		if (!heldBox.isEmpty() || animationTicks != 0 || buttonCooldown > 0)
			return;

		IFluidHandler fluidHandler = getFluidHandler();
		if (fluidHandler == null)
			return;

		PackagingRequest nextRequest = queuedRequests.get(0);
		FluidStack requestedFluid = FluidManifestItem.read(nextRequest.item());
		if (requestedFluid.isEmpty()) {
			queuedRequests.remove(0);
			return;
		}

		int fluidPerPackage = CFCommonConfig.getFluidPerPackage();
		// count 的单位就是 mB
		int requestedMB = nextRequest.getCount();
		int toExtract = Math.min(requestedMB, fluidPerPackage);
		if (toExtract <= 0) {
			queuedRequests.remove(0);
			return;
		}

		// 检查实际可用量
		int available = 0;
		for (int i = 0; i < fluidHandler.getTanks(); i++) {
			FluidStack fluid = fluidHandler.getFluidInTank(i);
			if (fluid.getFluid() == requestedFluid.getFluid()) {
				available += fluid.getAmount();
			}
		}
		if (available <= 0) {
			queuedRequests.remove(0);
			return;
		}

		toExtract = Math.min(toExtract, available);
		FluidStack extracted = fluidHandler.drain(
			new FluidStack(requestedFluid.getFluid(), toExtract),
			IFluidHandler.FluidAction.SIMULATE);
		if (extracted.isEmpty()) {
			queuedRequests.remove(0);
			return;
		}

		extracted = fluidHandler.drain(
			new FluidStack(requestedFluid.getFluid(), toExtract),
			IFluidHandler.FluidAction.EXECUTE);
		if (extracted.isEmpty()) {
			queuedRequests.remove(0);
			return;
		}

		ItemStack fluidPackage = FluidPackageItem.create(extracted, fluidPerPackage);

		PackageItem.clearAddress(fluidPackage);
		String address = nextRequest.address();
		if (address != null && !address.isBlank())
			PackageItem.addAddress(fluidPackage, address);

		PackageItem.setOrder(fluidPackage, nextRequest.orderId(), nextRequest.linkIndex(),
			nextRequest.finalLink().booleanValue(), nextRequest.packageCounter().getAndIncrement(),
			nextRequest.isEmpty(), nextRequest.context());

		// 扣除实际发送的 mB 数量
		int sentUnits = extracted.getAmount();
		if (sentUnits == 0 && extracted.getAmount() > 0) sentUnits = 1;
		nextRequest.subtract(sentUnits);
		if (nextRequest.isEmpty()) {
			queuedRequests.remove(0);
		}

		if (!heldBox.isEmpty() || animationTicks != 0) {
			queuedExitingPackages.add(new BigItemStack(fluidPackage, 1));
		} else {
			heldBox = fluidPackage;
			animationInward = false;
			animationTicks = CYCLE;
		}

		triggerStockCheck();
		notifyUpdate();
	}

	@Override
	public boolean unwrapBox(ItemStack box, boolean simulate) {
		if (FluidPackageItem.isFluidPackage(box)) {
			return unwrapFluidPackage(box, simulate);
		}
		return super.unwrapBox(box, simulate);
	}

	private boolean unwrapFluidPackage(ItemStack box, boolean simulate) {
		if (animationTicks > 0)
			return false;

		IFluidHandler fluidHandler = getFluidHandler();
		if (fluidHandler == null)
			return false;

		FluidStack fluid = FluidPackageItem.getFluid(box);
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
		notifyUpdate();
		return true;
	}

	/**
	 * 获取背面流体处理器，如果还未初始化则主动触发一次查找。
	 * 解决 TankManipulationBehaviour 初始化延迟导致右键/红石立刻交互失败的问题。
	 */
	private IFluidHandler getFluidHandler() {
		IFluidHandler handler = fluidTarget.getInventory();
		if (handler == null) {
			fluidTarget.findNewCapability();
			handler = fluidTarget.getInventory();
		}
		return handler;
	}
}
