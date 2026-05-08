package com.adonis.fluid.block.LogisticsJunction;

import java.util.ArrayList;
import java.util.List;

import com.adonis.fluid.datacomponent.BrassBoxFluidContent.FluidEntry;
import com.adonis.fluid.datacomponent.BrassBoxRoutingData;
import com.adonis.fluid.item.BrassBoxItem;
import com.adonis.fluid.item.CopperCanItem;
import com.adonis.fluid.item.PackageRoutingHelper;
import com.adonis.fluid.logistics.data.ContentRoute;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.depot.DepotItemHandler;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public class LogisticsJunctionBlockEntity extends SmartBlockEntity {
	private BlockPos flexibleTargetPos;
	private Direction flexibleTargetFace;

	private final ItemStackHandler input = new ItemStackHandler(1) {
		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return LogisticsJunctionBlock.acceptsPackage(stack);
		}
	};

	public LogisticsJunctionBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public static void registerCapabilities(RegisterCapabilitiesEvent event, BlockEntityType<LogisticsJunctionBlockEntity> type) {
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (be, side) -> be.input);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
	}

	public boolean tryInsert(ItemStack stack, Player player) {
		if (!input.getStackInSlot(0).isEmpty())
			return false;
		input.setStackInSlot(0, stack.copyWithCount(1));
		if (!player.getAbilities().instabuild)
			stack.shrink(1);
		setChanged();
		sendData();
		return true;
	}

	public void setFlexibleTarget(BlockPos targetPos, Direction targetFace) {
		flexibleTargetPos = targetPos.immutable();
		flexibleTargetFace = targetFace;
		setChanged();
		sendData();
	}

	public void clearFlexibleTarget() {
		flexibleTargetPos = null;
		flexibleTargetFace = null;
		setChanged();
		sendData();
	}

	public boolean hasFlexibleTarget() {
		return flexibleTargetPos != null && flexibleTargetFace != null;
	}

	public boolean hasValidFlexibleTarget() {
		return hasFlexibleTarget() && level != null && level.isLoaded(flexibleTargetPos);
	}

	public boolean hasRenderableFlexibleTarget() {
		if (!hasValidFlexibleTarget())
			return false;
		if (level.isClientSide)
			return true;
		return hasAnyTargetCapability(flexibleTargetPos, flexibleTargetFace);
	}

	public BlockPos getFlexibleTargetPos() {
		return flexibleTargetPos;
	}

	public Direction getFlexibleTargetFace() {
		return flexibleTargetFace;
	}

	public void tickServer() {
		validateFlexibleTarget();

		ItemStack stack = input.getStackInSlot(0);
		if (stack.isEmpty())
			return;
		UnpackPlan plan = planUnpack(stack);
		if (plan == null)
			return;
		if (!executeUnpack(plan))
			return;
		input.setStackInSlot(0, ItemStack.EMPTY);
		setChanged();
		sendData();
	}

	private UnpackPlan planUnpack(ItemStack stack) {
		BlockState state = getBlockState();
		Direction sideDirection = state.getValue(LogisticsJunctionBlock.FACING);
		IItemHandler sideItems = level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.relative(sideDirection), sideDirection.getOpposite());
		IFluidHandler sideFluids = level.getCapability(Capabilities.FluidHandler.BLOCK, worldPosition.relative(sideDirection), sideDirection.getOpposite());
		IItemHandler upItems = getUpperItemHandler();
		IFluidHandler upFluids = getUpperFluidHandler();

		HandlerAccess side = new HandlerAccess(sideItems, sideFluids);
		HandlerAccess up = new HandlerAccess(upItems, upFluids);
		ItemReservation sideItemReservation = new ItemReservation(sideItems);
		ItemReservation upItemReservation = new ItemReservation(upItems);
		FluidReservation sideFluidReservation = new FluidReservation(sideFluids);
		FluidReservation upFluidReservation = new FluidReservation(upFluids);
		UnpackPlan plan = new UnpackPlan();

		if (BrassBoxItem.isBrassBox(stack)) {
			if (!planPackageItems(stack, PackageRoutingHelper.getRoutingData(stack), plan, side, up, sideItemReservation, upItemReservation))
				return null;
			if (!planBrassBoxFluids(stack, plan, side, up, sideFluidReservation, upFluidReservation))
				return null;
		} else if (CopperCanItem.isCopperCan(stack)) {
			if (!planCopperCanFluid(stack, plan, side, up, sideFluidReservation, upFluidReservation))
				return null;
		} else if (LogisticsJunctionBlock.isCardboardPackage(stack)) {
			if (!planPackageItems(stack, PackageRoutingHelper.getRoutingData(stack), plan, side, up, sideItemReservation, upItemReservation))
				return null;
		} else {
			return null;
		}

		return plan;
	}

	private boolean planPackageItems(ItemStack stack, BrassBoxRoutingData routing, UnpackPlan plan, HandlerAccess side,
		HandlerAccess up, ItemReservation sideReservation, ItemReservation upReservation) {
		ItemStackHandler items = PackageItem.getContents(stack);
		for (int slot = 0; slot < items.getSlots(); slot++) {
			ItemStack entry = items.getStackInSlot(slot);
			if (entry.isEmpty())
				continue;
			OutputTarget target = chooseItemTarget(entry, routing.routeForItem(entry), side, up, sideReservation, upReservation);
			if (target == null)
				return false;
			plan.itemPlans.add(new ItemPlan(entry.copy(), target));
		}
		return true;
	}

	private boolean planBrassBoxFluids(ItemStack stack, UnpackPlan plan, HandlerAccess side, HandlerAccess up,
		FluidReservation sideReservation, FluidReservation upReservation) {
		for (FluidEntry entry : BrassBoxItem.getFluidContent(stack).fluids()) {
			if (entry.isEmpty())
				continue;
			OutputTarget target = chooseFluidTarget(entry.fluid(), entry.route(), side, up, sideReservation, upReservation);
			if (target == null)
				return false;
			plan.fluidPlans.add(new FluidPlan(entry.copy(), target));
		}
		return true;
	}

	private boolean planCopperCanFluid(ItemStack stack, UnpackPlan plan, HandlerAccess side, HandlerAccess up,
		FluidReservation sideReservation, FluidReservation upReservation) {
		FluidStack fluid = CopperCanItem.getFluid(stack);
		if (fluid.isEmpty())
			return true;
		OutputTarget target = chooseFluidTarget(fluid, ContentRoute.NONE, side, up, sideReservation, upReservation);
		if (target == null)
			return false;
		plan.fluidPlans.add(new FluidPlan(new FluidEntry(fluid.copy(), ContentRoute.NONE), target));
		return true;
	}

	private boolean executeUnpack(UnpackPlan plan) {
		BlockState state = getBlockState();
		Direction sideDirection = state.getValue(LogisticsJunctionBlock.FACING);
		IItemHandler sideItems = level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.relative(sideDirection), sideDirection.getOpposite());
		IFluidHandler sideFluids = level.getCapability(Capabilities.FluidHandler.BLOCK, worldPosition.relative(sideDirection), sideDirection.getOpposite());
		IItemHandler upItems = getUpperItemHandler();
		IFluidHandler upFluids = getUpperFluidHandler();

		for (ItemPlan itemPlan : plan.itemPlans)
			if (!insertItemIntoTarget(itemPlan.stack, itemPlan.target, sideItems, upItems, false))
				return false;
		for (FluidPlan fluidPlan : plan.fluidPlans)
			if (!insertFluidIntoTarget(fluidPlan.entry.fluid(), fluidPlan.target, sideFluids, upFluids, false))
				return false;
		return true;
	}

	private OutputTarget chooseItemTarget(ItemStack stack, ContentRoute route, HandlerAccess side, HandlerAccess up,
		ItemReservation sideReservation, ItemReservation upReservation) {
		OutputTarget preferred = route == ContentRoute.UP ? OutputTarget.UP : OutputTarget.SIDE;
		OutputTarget fallback = preferred == OutputTarget.UP ? OutputTarget.SIDE : OutputTarget.UP;
		if (canReserveItem(stack, preferred, side, up, sideReservation, upReservation))
			return preferred;
		if (canReserveItem(stack, fallback, side, up, sideReservation, upReservation))
			return fallback;
		return null;
	}

	private OutputTarget chooseFluidTarget(FluidStack fluid, ContentRoute route, HandlerAccess side, HandlerAccess up,
		FluidReservation sideReservation, FluidReservation upReservation) {
		OutputTarget preferred = route == ContentRoute.UP ? OutputTarget.UP : OutputTarget.SIDE;
		OutputTarget fallback = preferred == OutputTarget.UP ? OutputTarget.SIDE : OutputTarget.UP;
		if (canReserveFluid(fluid, preferred, side, up, sideReservation, upReservation))
			return preferred;
		if (canReserveFluid(fluid, fallback, side, up, sideReservation, upReservation))
			return fallback;
		return null;
	}

	private boolean canReserveItem(ItemStack stack, OutputTarget target, HandlerAccess side, HandlerAccess up,
		ItemReservation sideReservation, ItemReservation upReservation) {
		return switch (target) {
			case SIDE -> side.items != null && sideReservation.reserve(stack);
			case UP -> up.items != null && upReservation.reserve(stack);
		};
	}

	private boolean canReserveFluid(FluidStack fluid, OutputTarget target, HandlerAccess side, HandlerAccess up,
		FluidReservation sideReservation, FluidReservation upReservation) {
		return switch (target) {
			case SIDE -> side.fluids != null && sideReservation.reserve(fluid);
			case UP -> up.fluids != null && upReservation.reserve(fluid);
		};
	}

	private boolean insertItemIntoTarget(ItemStack stack, OutputTarget target, IItemHandler sideItems, IItemHandler upItems,
		boolean simulate) {
		return switch (target) {
			case SIDE -> canInsertItem(sideItems, stack, simulate);
			case UP -> canInsertItem(upItems, stack, simulate);
		};
	}

	private boolean insertFluidIntoTarget(FluidStack fluid, OutputTarget target, IFluidHandler sideFluids,
		IFluidHandler upFluids, boolean simulate) {
		return switch (target) {
			case SIDE -> canInsertFluid(sideFluids, fluid, simulate);
			case UP -> canInsertFluid(upFluids, fluid, simulate);
		};
	}

	private boolean canInsertItem(IItemHandler handler, ItemStack stack, boolean simulate) {
		if (handler == null)
			return false;
		ItemStack remaining = stack.copy();
		for (int slot = 0; slot < handler.getSlots(); slot++) {
			remaining = handler.insertItem(slot, remaining, simulate);
			if (remaining.isEmpty())
				return true;
		}
		return remaining.isEmpty();
	}

	private boolean canInsertFluid(IFluidHandler handler, FluidStack fluid, boolean simulate) {
		if (handler == null)
			return false;
		int filled = handler.fill(fluid.copy(), simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
		return filled >= fluid.getAmount();
	}

	private IItemHandler getUpperItemHandler() {
		Direction face = getUpperTargetFace();
		BlockPos targetPos = getUpperTargetPos();
		return targetPos == null || face == null ? null : level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, face);
	}

	private IFluidHandler getUpperFluidHandler() {
		Direction face = getUpperTargetFace();
		BlockPos targetPos = getUpperTargetPos();
		return targetPos == null || face == null ? null : level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, face);
	}

	private BlockPos getUpperTargetPos() {
		if (hasValidFlexibleTarget())
			return flexibleTargetPos;
		return worldPosition.above();
	}

	private Direction getUpperTargetFace() {
		if (hasFlexibleTarget())
			return flexibleTargetFace;
		return Direction.DOWN;
	}

	private void validateFlexibleTarget() {
		if (!hasValidFlexibleTarget())
			return;
		if (!hasAnyTargetCapability(flexibleTargetPos, flexibleTargetFace))
			clearFlexibleTarget();
	}

	private boolean hasAnyTargetCapability(BlockPos targetPos, Direction targetFace) {
		return level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, targetFace) != null
			|| level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, targetFace) != null;
	}

	@Override
	protected AABB createRenderBoundingBox() {
		AABB box = super.createRenderBoundingBox();
		if (!hasFlexibleTarget())
			return box;
		return box.minmax(new AABB(flexibleTargetPos)).inflate(2);
	}

	@Override
	protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(compound, registries, clientPacket);
		compound.put("Input", input.serializeNBT(registries));
		if (flexibleTargetPos != null)
			compound.putLong("FlexibleTargetPos", flexibleTargetPos.asLong());
		if (flexibleTargetFace != null)
			compound.putByte("FlexibleTargetFace", (byte) flexibleTargetFace.ordinal());
	}

	@Override
	protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(compound, registries, clientPacket);
		input.deserializeNBT(registries, compound.getCompound("Input"));
		flexibleTargetPos = compound.contains("FlexibleTargetPos") ? BlockPos.of(compound.getLong("FlexibleTargetPos")) : null;
		flexibleTargetFace = compound.contains("FlexibleTargetFace") ? Direction.from3DDataValue(compound.getByte("FlexibleTargetFace")) : null;
	}

	private enum OutputTarget {
		SIDE,
		UP
	}

	private record HandlerAccess(IItemHandler items, IFluidHandler fluids) {
	}

	private static class UnpackPlan {
		private final List<ItemPlan> itemPlans = new ArrayList<>();
		private final List<FluidPlan> fluidPlans = new ArrayList<>();
	}

	private record ItemPlan(ItemStack stack, OutputTarget target) {
	}

	private record FluidPlan(FluidEntry entry, OutputTarget target) {
	}

	private static class ItemReservation {
		private final IItemHandler handler;
		private final List<ItemStack> virtualSlots;
		private boolean depotOccupied;

		private ItemReservation(IItemHandler handler) {
			this.handler = handler;
			this.virtualSlots = new ArrayList<>();
			if (handler != null) {
				for (int slot = 0; slot < handler.getSlots(); slot++)
					virtualSlots.add(handler.getStackInSlot(slot).copy());
				depotOccupied = handler instanceof DepotItemHandler && !handler.getStackInSlot(0).isEmpty();
			}
		}

		private boolean reserve(ItemStack stack) {
			if (handler == null)
				return false;
			if (handler instanceof DepotItemHandler)
				return reserveForDepot(stack);
			ItemStack remaining = stack.copy();
			for (int slot = 0; slot < virtualSlots.size(); slot++) {
				remaining = reserveIntoSlot(slot, remaining);
				if (remaining.isEmpty())
					return true;
			}
			return false;
		}

		private boolean reserveForDepot(ItemStack stack) {
			if (depotOccupied)
				return false;
			if (!handler.isItemValid(0, stack))
				return false;
			ItemStack remainder = handler.insertItem(0, stack, true);
			if (!remainder.isEmpty())
				return false;
			depotOccupied = true;
			virtualSlots.set(0, stack.copy());
			return true;
		}

		private ItemStack reserveIntoSlot(int slot, ItemStack stack) {
			if (stack.isEmpty())
				return ItemStack.EMPTY;
			ItemStack existing = virtualSlots.get(slot);
			if (!handler.isItemValid(slot, stack))
				return stack;
			int limit = Math.min(handler.getSlotLimit(slot), stack.getMaxStackSize());
			if (limit <= 0)
				return stack;
			if (existing.isEmpty()) {
				int moved = Math.min(limit, stack.getCount());
				virtualSlots.set(slot, stack.copyWithCount(moved));
				return stack.getCount() == moved ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
			}
			if (!ItemStack.isSameItemSameComponents(existing, stack))
				return stack;
			int space = limit - existing.getCount();
			if (space <= 0)
				return stack;
			int moved = Math.min(space, stack.getCount());
			existing.grow(moved);
			return stack.getCount() == moved ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
		}
	}

	private static class FluidReservation {
		private final IFluidHandler handler;
		private final List<FluidStack> virtualTanks;

		private FluidReservation(IFluidHandler handler) {
			this.handler = handler;
			this.virtualTanks = new ArrayList<>();
			if (handler != null)
				for (int tank = 0; tank < handler.getTanks(); tank++)
					virtualTanks.add(handler.getFluidInTank(tank).copy());
		}

		private boolean reserve(FluidStack fluid) {
			if (handler == null || fluid.isEmpty() || fluid.getAmount() <= 0)
				return false;
			int remaining = fluid.getAmount();
			for (int tank = 0; tank < virtualTanks.size(); tank++) {
				remaining -= reserveIntoTank(tank, fluid, remaining);
				if (remaining <= 0)
					return true;
			}
			return false;
		}

		private int reserveIntoTank(int tank, FluidStack fluid, int remaining) {
			if (remaining <= 0)
				return 0;
			if (!handler.isFluidValid(tank, fluid))
				return 0;
			FluidStack existing = virtualTanks.get(tank);
			int capacity = handler.getTankCapacity(tank);
			if (capacity <= 0)
				return 0;
			if (existing.isEmpty()) {
				int moved = Math.min(capacity, remaining);
				virtualTanks.set(tank, fluid.copyWithAmount(moved));
				return moved;
			}
			if (!FluidStack.isSameFluidSameComponents(existing, fluid))
				return 0;
			int space = capacity - existing.getAmount();
			if (space <= 0)
				return 0;
			int moved = Math.min(space, remaining);
			existing.grow(moved);
			return moved;
		}
	}
}
