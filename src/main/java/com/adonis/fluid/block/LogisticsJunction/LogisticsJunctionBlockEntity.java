package com.adonis.fluid.block.LogisticsJunction;

import java.util.ArrayList;
import java.util.List;

import com.adonis.fluid.config.CFCommonConfig;
import org.jetbrains.annotations.Nullable;

import com.adonis.fluid.datacomponent.BrassBoxFluidContent.FluidEntry;
import com.adonis.fluid.datacomponent.BrassBoxRoutingData;
import com.adonis.fluid.item.BrassBoxItem;
import com.adonis.fluid.item.CopperCanItem;
import com.adonis.fluid.item.PackageRoutingHelper;
import com.adonis.fluid.logistics.data.ContentRoute;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.depot.DepotItemHandler;
import com.simibubi.create.content.fluids.FluidFX;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.item.SmartInventory;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class LogisticsJunctionBlockEntity extends KineticBlockEntity implements IHaveGoggleInformation {
	private static final int INVENTORY_SLOTS = 9;
	private static final int FLUID_CAPACITY = 4000;
	private static final int FLUID_TRANSFER_PER_TICK = 1000;
	private static final String FLEXIBLE_TARGET_OFFSET_KEY = "FlexibleTargetOffset";
	private static final String LEGACY_FLEXIBLE_TARGET_POS_KEY = "FlexibleTargetPos";
	private static final String FLEXIBLE_TARGET_FACE_KEY = "FlexibleTargetFace";

	private BlockPos flexibleTargetPos;
	private Direction flexibleTargetFace;

	private final SmartInventory inventory;
	private SmartFluidTankBehaviour tankBehaviour;
	private ScrollOptionBehaviour<IOMode> ioMode;

	public LogisticsJunctionBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		inventory = new SmartInventory(INVENTORY_SLOTS, this).whenContentsChanged($ -> onInventoryChanged());
	}

	public static void registerCapabilities(RegisterCapabilitiesEvent event, BlockEntityType<LogisticsJunctionBlockEntity> type) {
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, LogisticsJunctionBlockEntity::getItemCapability);
		event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, LogisticsJunctionBlockEntity::getFluidCapability);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);

		ioMode = new ScrollOptionBehaviour<>(IOMode.class,
			Component.translatable("create.fluid.logistics_junction.io_mode"), this, new IOModeValueBox());
		ioMode.withCallback(mode -> {
			setChanged();
			sendData();
		});
		behaviours.add(ioMode);

		tankBehaviour = new SmartFluidTankBehaviour(SmartFluidTankBehaviour.INPUT, this, 1, FLUID_CAPACITY, false)
			.whenFluidUpdates(this::onFluidChanged);
		behaviours.add(tankBehaviour);
		behaviours.add(new DirectBeltInputBehaviour(this).allowingBeltFunnels());
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide)
			return;

		validateFlexibleTarget();
		if (getSpeed() == 0 || isOverStressed())
			return;

		collectIncomingContents();

		int packageSlot = findQueuedPackageSlot();
		if (packageSlot != -1)
			tryProcessPackage(packageSlot);
		dispatchStoredContents();
	}

	public boolean tryInsert(ItemStack stack, Player player) {
		ItemStack remainder = insertOneItem(stack.copyWithCount(1));
		if (!remainder.isEmpty())
			return false;
		if (!player.getAbilities().instabuild)
			stack.shrink(1);
		return true;
	}

	public IFluidHandler getFluidHandler() {
		if (tankBehaviour == null)
			return new FluidTank(0);
		IFluidHandler handler = tankBehaviour.getCapability();
		return handler != null ? handler : new FluidTank(0);
	}

	public IOMode getIOMode() {
		return ioMode == null ? IOMode.INPUT : ioMode.get();
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

	public static boolean isTargetInRange(BlockPos junctionPos, BlockPos targetPos) {
		return junctionPos.closerThan(targetPos, CFCommonConfig.getLogisticsJunctionLinkRange() + 0.5);
	}

	public static boolean isValidTarget(Level level, BlockPos targetPos, Direction targetFace) {
		return level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, targetFace) != null
			|| level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, targetFace) != null;
	}

	private static IItemHandler getItemCapability(LogisticsJunctionBlockEntity be, @Nullable Direction side) {
		if (side == null)
			return be.inventory;
		if (be.canInputFrom(side))
			return be.new ItemInputHandler();
		if (be.canOutputTo(side))
			return be.new ItemOutputHandler();
		return null;
	}

	private static IFluidHandler getFluidCapability(LogisticsJunctionBlockEntity be, @Nullable Direction side) {
		if (side == null)
			return be.getFluidHandler();
		if (be.canInputFrom(side))
			return be.new FluidInputHandler();
		if (be.canOutputTo(side))
			return be.new FluidOutputHandler();
		return null;
	}

	private void onInventoryChanged() {
		if (level != null && !level.isClientSide) {
			setChanged();
			sendData();
		}
	}

	private void onFluidChanged() {
		if (level != null && !level.isClientSide) {
			setChanged();
			sendData();
		}
	}

	private ItemStack insertOneItem(ItemStack stack) {
		ItemStack remainder = stack;
		for (int slot = 0; slot < inventory.getSlots(); slot++) {
			remainder = inventory.insertItem(slot, remainder, false);
			if (remainder.isEmpty())
				return ItemStack.EMPTY;
		}
		return remainder;
	}

	private int findQueuedPackageSlot() {
		for (int slot = 0; slot < inventory.getSlots(); slot++) {
			ItemStack stack = inventory.getStackInSlot(slot);
			if (!stack.isEmpty() && LogisticsJunctionBlock.acceptsPackage(stack))
				return slot;
		}
		return -1;
	}

	private void tryProcessPackage(int packageSlot) {
		ItemStack stack = inventory.getStackInSlot(packageSlot);
		UnpackPlan plan = planUnpack(stack.copyWithCount(1));
		if (plan == null || !executeUnpack(plan))
			return;

		if (stack.getCount() <= 1)
			inventory.setStackInSlot(packageSlot, ItemStack.EMPTY);
		else
			inventory.setStackInSlot(packageSlot, stack.copyWithCount(stack.getCount() - 1));
	}

	private void collectIncomingContents() {
		collectIncomingItems();
		collectIncomingFluid();
	}

	private void collectIncomingItems() {
		for (InputTarget target : getInputTargets()) {
			IItemHandler handler = target.items();
			if (handler == null)
				continue;
			for (int slot = 0; slot < handler.getSlots(); slot++) {
				ItemStack extracted = handler.extractItem(slot, 64, true);
				if (extracted.isEmpty())
					continue;
				ItemStack remainder = insertOneItem(extracted.copy());
				if (remainder.getCount() == extracted.getCount())
					continue;
				int moved = extracted.getCount() - remainder.getCount();
				handler.extractItem(slot, moved, false);
				if (!remainder.isEmpty())
					insertOneItem(remainder);
				return;
			}
		}
	}

	private void collectIncomingFluid() {
		IFluidHandler localTank = getFluidHandler();
		for (InputTarget target : getInputTargets()) {
			IFluidHandler handler = target.fluids();
			if (handler == null)
				continue;

			FluidStack drained = handler.drain(FLUID_TRANSFER_PER_TICK, IFluidHandler.FluidAction.SIMULATE);
			if (drained.isEmpty())
				continue;

			int accepted = localTank.fill(drained.copy(), IFluidHandler.FluidAction.SIMULATE);
			if (accepted <= 0)
				continue;

			FluidStack executedDrain = handler.drain(Math.min(accepted, drained.getAmount()), IFluidHandler.FluidAction.EXECUTE);
			if (executedDrain.isEmpty())
				continue;

			localTank.fill(executedDrain, IFluidHandler.FluidAction.EXECUTE);
			if (target.upper())
				spawnUpperTransferParticles(executedDrain, true);
			return;
		}
	}

	private void dispatchStoredContents() {
		dispatchStoredItems();
		dispatchStoredFluid();
	}

	private void dispatchStoredItems() {
		for (int slot = 0; slot < inventory.getSlots(); slot++) {
			ItemStack stack = inventory.getStackInSlot(slot);
			if (stack.isEmpty() || LogisticsJunctionBlock.acceptsPackage(stack))
				continue;

			ItemStack remainder = moveItemToOutputs(stack.copy());
			if (remainder.getCount() == stack.getCount())
				continue;

			inventory.setStackInSlot(slot, remainder);
			return;
		}
	}

	private void dispatchStoredFluid() {
		IFluidHandler tank = getFluidHandler();
		if (tank.getTanks() <= 0)
			return;

		FluidStack stored = tank.getFluidInTank(0);
		if (stored.isEmpty())
			return;

		int limit = Math.min(FLUID_TRANSFER_PER_TICK, stored.getAmount());
		int moved = 0;
		for (OutputTarget target : getOutputTargets()) {
			if (target.fluids() == null || moved >= limit)
				continue;
			FluidStack attempt = stored.copyWithAmount(limit - moved);
			int filled = target.fluids().fill(attempt, IFluidHandler.FluidAction.EXECUTE);
			if (filled > 0) {
				moved += filled;
				if (target.upper())
					spawnUpperTransferParticles(stored.copyWithAmount(filled), false);
			}
		}

		if (moved > 0)
			tank.drain(moved, IFluidHandler.FluidAction.EXECUTE);
	}

	private UnpackPlan planUnpack(ItemStack stack) {
		List<OutputTarget> outputs = getOutputTargets();
		if (outputs.isEmpty())
			return null;

		List<ItemReservation> itemReservations = new ArrayList<>();
		List<FluidReservation> fluidReservations = new ArrayList<>();
		for (OutputTarget target : outputs) {
			itemReservations.add(new ItemReservation(target.items()));
			fluidReservations.add(new FluidReservation(target.fluids()));
		}

		UnpackPlan plan = new UnpackPlan();
		if (BrassBoxItem.isBrassBox(stack)) {
			BrassBoxRoutingData routing = PackageRoutingHelper.getRoutingData(stack);
			if (!planPackageItems(stack, routing, plan, outputs, itemReservations))
				return null;
			if (!planBrassBoxFluids(stack, plan, outputs, fluidReservations))
				return null;
		} else if (CopperCanItem.isCopperCan(stack)) {
			if (!planCopperCanFluid(stack, plan, outputs, fluidReservations))
				return null;
		} else if (LogisticsJunctionBlock.isCardboardPackage(stack)) {
			BrassBoxRoutingData routing = PackageRoutingHelper.getRoutingData(stack);
			if (!planPackageItems(stack, routing, plan, outputs, itemReservations))
				return null;
		} else {
			return null;
		}

		return plan;
	}

	private boolean planPackageItems(ItemStack stack, @Nullable BrassBoxRoutingData routing, UnpackPlan plan,
		List<OutputTarget> outputs, List<ItemReservation> reservations) {
		var items = PackageItem.getContents(stack);
		for (int slot = 0; slot < items.getSlots(); slot++) {
			ItemStack entry = items.getStackInSlot(slot);
			if (entry.isEmpty())
				continue;
			ContentRoute route = routing == null ? ContentRoute.NONE : routing.routeForItem(entry);
			Integer targetIndex = chooseItemTarget(entry, route, outputs, reservations);
			if (targetIndex == null)
				return false;
			plan.itemPlans.add(new ItemPlan(entry.copy(), targetIndex));
		}
		return true;
	}

	private boolean planBrassBoxFluids(ItemStack stack, UnpackPlan plan, List<OutputTarget> outputs,
		List<FluidReservation> reservations) {
		for (FluidEntry entry : BrassBoxItem.getFluidContent(stack).fluids()) {
			if (entry.isEmpty())
				continue;
			Integer targetIndex = chooseFluidTarget(entry.fluid(), entry.route(), outputs, reservations);
			if (targetIndex == null)
				return false;
			plan.fluidPlans.add(new FluidPlan(entry.copy(), targetIndex));
		}
		return true;
	}

	private boolean planCopperCanFluid(ItemStack stack, UnpackPlan plan, List<OutputTarget> outputs,
		List<FluidReservation> reservations) {
		FluidStack fluid = CopperCanItem.getFluid(stack);
		if (fluid.isEmpty())
			return true;
		Integer targetIndex = chooseFluidTarget(fluid, ContentRoute.NONE, outputs, reservations);
		if (targetIndex == null)
			return false;
		plan.fluidPlans.add(new FluidPlan(new FluidEntry(fluid.copy(), ContentRoute.NONE), targetIndex));
		return true;
	}

	/**
	 * Unpacks a package atomically: either all of its contents are inserted into the outputs, or
	 * nothing is. This is what prevents item/fluid duplication.
	 *
	 * <p>Phase 1 simulates every insertion against the real handlers ({@code simulate = true}); if
	 * anything would not fully fit, we abort without having mutated the world. Phase 2 commits for
	 * real, and if a commit unexpectedly falls short (a handler whose simulate/execute behaviour
	 * disagrees), every insertion made so far is rolled back so the surviving package cannot be
	 * duplicated. Returns {@code true} only when the whole package was committed, at which point the
	 * caller consumes the source package.
	 */
	private boolean executeUnpack(UnpackPlan plan) {
		List<OutputTarget> outputs = getOutputTargets();

		// Phase 1: validate indices and simulate every insertion. No world state is changed here.
		for (ItemPlan itemPlan : plan.itemPlans) {
			if (itemPlan.targetIndex() < 0 || itemPlan.targetIndex() >= outputs.size())
				return false;
			if (!canFullyInsertItem(outputs.get(itemPlan.targetIndex()).items(), itemPlan.stack()))
				return false;
		}
		for (FluidPlan fluidPlan : plan.fluidPlans) {
			if (fluidPlan.targetIndex() < 0 || fluidPlan.targetIndex() >= outputs.size())
				return false;
			if (!canFullyInsertFluid(outputs.get(fluidPlan.targetIndex()).fluids(), fluidPlan.entry().fluid()))
				return false;
		}

		// Phase 2: commit for real, tracking what was inserted so we can undo it if a later step fails.
		List<CommittedItem> committedItems = new ArrayList<>();
		List<CommittedFluid> committedFluids = new ArrayList<>();

		for (ItemPlan itemPlan : plan.itemPlans) {
			IItemHandler handler = outputs.get(itemPlan.targetIndex()).items();
			ItemStack stack = itemPlan.stack();
			int inserted = insertItemForReal(handler, stack);
			if (inserted > 0)
				committedItems.add(new CommittedItem(handler, stack.copyWithCount(inserted)));
			if (inserted < stack.getCount()) {
				rollbackUnpack(committedItems, committedFluids);
				return false;
			}
		}
		for (FluidPlan fluidPlan : plan.fluidPlans) {
			IFluidHandler handler = outputs.get(fluidPlan.targetIndex()).fluids();
			FluidStack fluid = fluidPlan.entry().fluid();
			int filled = fillFluidForReal(handler, fluid);
			if (filled > 0)
				committedFluids.add(new CommittedFluid(handler, fluid.copyWithAmount(filled)));
			if (filled < fluid.getAmount()) {
				rollbackUnpack(committedItems, committedFluids);
				return false;
			}
		}
		return true;
	}

	private void rollbackUnpack(List<CommittedItem> committedItems, List<CommittedFluid> committedFluids) {
		for (CommittedFluid committed : committedFluids)
			committed.handler().drain(committed.stack(), IFluidHandler.FluidAction.EXECUTE);
		for (CommittedItem committed : committedItems)
			extractExact(committed.handler(), committed.stack());
	}

	private void extractExact(IItemHandler handler, ItemStack template) {
		int remaining = template.getCount();
		for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
			ItemStack inSlot = handler.getStackInSlot(slot);
			if (inSlot.isEmpty() || !ItemStack.isSameItemSameComponents(inSlot, template))
				continue;
			remaining -= handler.extractItem(slot, remaining, false).getCount();
		}
	}

	private @Nullable Integer chooseItemTarget(ItemStack stack, ContentRoute route, List<OutputTarget> outputs,
		List<ItemReservation> reservations) {
		for (int index : getTargetOrder(route, outputs)) {
			if (reservations.get(index).reserve(stack))
				return index;
		}
		return null;
	}

	private @Nullable Integer chooseFluidTarget(FluidStack fluid, ContentRoute route, List<OutputTarget> outputs,
		List<FluidReservation> reservations) {
		for (int index : getTargetOrder(route, outputs)) {
			if (reservations.get(index).reserve(fluid))
				return index;
		}
		return null;
	}

	private List<Integer> getTargetOrder(ContentRoute route, List<OutputTarget> outputs) {
		List<Integer> order = new ArrayList<>();
		if (route == ContentRoute.UP) {
			for (int i = 0; i < outputs.size(); i++) {
				if (outputs.get(i).upper()) {
					order.add(i);
					break;
				}
			}
		}
		for (int i = 0; i < outputs.size(); i++) {
			if (!order.contains(i))
				order.add(i);
		}
		return order;
	}

	/** Simulates inserting the whole stack; returns true only if all of it would fit. Mutates nothing. */
	private boolean canFullyInsertItem(@Nullable IItemHandler handler, ItemStack stack) {
		if (handler == null)
			return false;
		ItemStack remaining = stack.copy();
		for (int slot = 0; slot < handler.getSlots(); slot++) {
			remaining = handler.insertItem(slot, remaining, true);
			if (remaining.isEmpty())
				return true;
		}
		return remaining.isEmpty();
	}

	/** Simulates filling the whole amount; returns true only if all of it would fit. Mutates nothing. */
	private boolean canFullyInsertFluid(@Nullable IFluidHandler handler, FluidStack fluid) {
		if (handler == null)
			return false;
		return handler.fill(fluid.copy(), IFluidHandler.FluidAction.SIMULATE) >= fluid.getAmount();
	}

	/** Inserts as much of the stack as fits, for real, and returns how many items were actually inserted. */
	private int insertItemForReal(@Nullable IItemHandler handler, ItemStack stack) {
		if (handler == null)
			return 0;
		ItemStack remaining = stack.copy();
		for (int slot = 0; slot < handler.getSlots(); slot++) {
			remaining = handler.insertItem(slot, remaining, false);
			if (remaining.isEmpty())
				return stack.getCount();
		}
		return stack.getCount() - remaining.getCount();
	}

	/** Fills as much of the fluid as fits, for real, and returns how much was actually accepted. */
	private int fillFluidForReal(@Nullable IFluidHandler handler, FluidStack fluid) {
		if (handler == null)
			return 0;
		return handler.fill(fluid.copy(), IFluidHandler.FluidAction.EXECUTE);
	}

	private ItemStack moveItemToOutputs(ItemStack stack) {
		ItemStack remainder = stack;
		for (OutputTarget target : getOutputTargets()) {
			if (target.items() == null)
				continue;
			remainder = insertIntoHandler(target.items(), remainder);
			if (remainder.isEmpty())
				return ItemStack.EMPTY;
		}
		return remainder;
	}

	private ItemStack insertIntoHandler(IItemHandler handler, ItemStack stack) {
		ItemStack remainder = stack;
		for (int slot = 0; slot < handler.getSlots(); slot++) {
			remainder = handler.insertItem(slot, remainder, false);
			if (remainder.isEmpty())
				return ItemStack.EMPTY;
		}
		return remainder;
	}

	private List<OutputTarget> getOutputTargets() {
		List<OutputTarget> outputs = new ArrayList<>();
		Direction facing = getBlockState().getValue(LogisticsJunctionBlock.FACING);

		if (getIOMode() == IOMode.INPUT) {
			outputs.add(buildSideTarget(facing));
			OutputTarget upper = buildUpperTarget();
			if (upper.items() != null || upper.fluids() != null)
				outputs.add(upper);
		} else {
			for (Direction direction : new Direction[] { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST }) {
				if (direction == facing)
					continue;
				outputs.add(buildSideTarget(direction));
			}
		}

		outputs.removeIf(target -> target.items() == null && target.fluids() == null);
		return outputs;
	}

	private List<InputTarget> getInputTargets() {
		List<InputTarget> inputs = new ArrayList<>();
		Direction facing = getBlockState().getValue(LogisticsJunctionBlock.FACING);

		if (getIOMode() == IOMode.INPUT) {
			for (Direction direction : new Direction[] { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST }) {
				if (direction == facing)
					continue;
				inputs.add(buildSideInputTarget(direction));
			}
		} else {
			inputs.add(buildSideInputTarget(facing));
			InputTarget upper = buildUpperInputTarget();
			if (upper.items() != null || upper.fluids() != null)
				inputs.add(upper);
		}

		inputs.removeIf(target -> target.items() == null && target.fluids() == null);
		return inputs;
	}

	private OutputTarget buildSideTarget(Direction direction) {
		BlockPos pos = worldPosition.relative(direction);
		Direction face = direction.getOpposite();
		return new OutputTarget(level.getCapability(Capabilities.ItemHandler.BLOCK, pos, face),
			level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face), false);
	}

	private InputTarget buildSideInputTarget(Direction direction) {
		BlockPos pos = worldPosition.relative(direction);
		Direction face = direction.getOpposite();
		return new InputTarget(level.getCapability(Capabilities.ItemHandler.BLOCK, pos, face),
			level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face), false);
	}

	private OutputTarget buildUpperTarget() {
		BlockPos targetPos = getUpperTargetPos();
		Direction face = getUpperTargetFace();
		if (targetPos == null || face == null)
			return new OutputTarget(null, null, true);
		return new OutputTarget(level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, face),
			level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, face), true);
	}

	private InputTarget buildUpperInputTarget() {
		BlockPos targetPos = getUpperTargetPos();
		Direction face = getUpperTargetFace();
		if (targetPos == null || face == null)
			return new InputTarget(null, null, true);
		return new InputTarget(level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, face),
			level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, face), true);
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
		return isValidTarget(level, targetPos, targetFace);
	}

	private void spawnUpperTransferParticles(FluidStack fluid, boolean inbound) {
		if (!(level instanceof ServerLevel serverLevel) || fluid.isEmpty())
			return;

		BlockPos targetPos = getUpperTargetPos();
		Direction targetFace = getUpperTargetFace();
		if (targetPos == null || targetFace == null)
			return;

		ParticleOptions particle = FluidFX.getFluidParticle(fluid);
		Vec3 startNormal = new Vec3(0, 1, 0);
		Vec3 endNormal = Vec3.atLowerCornerOf(targetFace.getNormal());
		Vec3 start = Vec3.atBottomCenterOf(worldPosition).add(0, 1 + 1f / 16f, 0).add(startNormal.scale(3f / 16f));
		Vec3 end = Vec3.atCenterOf(targetPos).add(endNormal.scale(0.5 + 3f / 16f));
		if (inbound) {
			Vec3 swap = start;
			start = end;
			end = swap;
		}

		Vec3 controlA = start.add(startNormal.scale(10f / 16f));
		Vec3 controlB = end.subtract(endNormal.scale(6f / 16f));
		Vec3 previous = start;
		for (int i = 1; i <= 8; i++) {
			double t = i / 8d;
			Vec3 point = cubicBezier(start, controlA, controlB, end, t);
			Vec3 motion = point.subtract(previous).scale(0.2);
			serverLevel.sendParticles(particle, point.x, point.y, point.z, 1, motion.x, motion.y, motion.z, 0);
			previous = point;
		}
	}

	private static Vec3 cubicBezier(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
		double u = 1 - t;
		return p0.scale(u * u * u)
			.add(p1.scale(3 * u * u * t))
			.add(p2.scale(3 * u * t * t))
			.add(p3.scale(t * t * t));
	}

	private boolean canInputFrom(Direction side) {
		if (side == Direction.DOWN)
			return false;
		Direction facing = getBlockState().getValue(LogisticsJunctionBlock.FACING);
		if (getIOMode() == IOMode.INPUT)
			return side.getAxis().isHorizontal() && side != facing;
		return side == Direction.UP || side == facing;
	}

	private boolean canOutputTo(Direction side) {
		if (side == Direction.DOWN)
			return false;
		Direction facing = getBlockState().getValue(LogisticsJunctionBlock.FACING);
		if (getIOMode() == IOMode.INPUT)
			return side == Direction.UP || side == facing;
		return side.getAxis().isHorizontal() && side != facing;
	}

	@Override
	public void destroy() {
		ItemHelper.dropContents(level, worldPosition, inventory);
		super.destroy();
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
		compound.put("Inventory", inventory.serializeNBT(registries));
		writeFlexibleTarget(compound);
	}

	@Override
	protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(compound, registries, clientPacket);
		inventory.deserializeNBT(registries, compound.getCompound("Inventory"));
		readFlexibleTarget(compound);
	}

	private void writeFlexibleTarget(CompoundTag compound) {
		if (flexibleTargetPos == null || flexibleTargetFace == null)
			return;

		compound.putLong(FLEXIBLE_TARGET_OFFSET_KEY, flexibleTargetPos.subtract(worldPosition).asLong());
		compound.putByte(FLEXIBLE_TARGET_FACE_KEY, (byte) flexibleTargetFace.get3DDataValue());
	}

	private void readFlexibleTarget(CompoundTag compound) {
		flexibleTargetPos = null;
		flexibleTargetFace = null;

		if (!compound.contains(FLEXIBLE_TARGET_FACE_KEY))
			return;

		BlockPos targetPos = null;
		if (compound.contains(FLEXIBLE_TARGET_OFFSET_KEY)) {
			targetPos = BlockPos.of(compound.getLong(FLEXIBLE_TARGET_OFFSET_KEY)).offset(worldPosition);
		} else if (compound.contains(LEGACY_FLEXIBLE_TARGET_POS_KEY)) {
			BlockPos legacyTarget = BlockPos.of(compound.getLong(LEGACY_FLEXIBLE_TARGET_POS_KEY));
			if (isTargetInRange(worldPosition, legacyTarget))
				targetPos = legacyTarget;
		}

		if (targetPos == null)
			return;

		flexibleTargetPos = targetPos;
		flexibleTargetFace = Direction.from3DDataValue(compound.getByte(FLEXIBLE_TARGET_FACE_KEY));
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);

		CreateLang.builder()
			.add(Component.translatable("fluid.logistics_junction.goggles.io_mode"))
			.forGoggles(tooltip);
		CreateLang.builder()
			.add(Component.translatable(getIOMode().getTranslationKey()).withStyle(ChatFormatting.GRAY))
			.forGoggles(tooltip, 1);

		boolean hasItemContents = false;
		for (int slot = 0; slot < inventory.getSlots(); slot++) {
			ItemStack stack = inventory.getStackInSlot(slot);
			if (stack.isEmpty())
				continue;
			hasItemContents = true;
		}

		FluidStack fluid = tankBehaviour == null ? FluidStack.EMPTY : tankBehaviour.getPrimaryHandler().getFluid();

		if (hasItemContents) {
			CreateLang.builder()
				.add(Component.translatable("fluid.logistics_junction.goggles.contents"))
				.forGoggles(tooltip);
		}

		for (int slot = 0; slot < inventory.getSlots(); slot++) {
			ItemStack stack = inventory.getStackInSlot(slot);
			if (stack.isEmpty())
				continue;
			CreateLang.text("")
				.add(stack.getHoverName().copy().withStyle(ChatFormatting.GRAY))
				.add(CreateLang.text(" x" + stack.getCount()).style(ChatFormatting.GREEN))
				.forGoggles(tooltip, 1);
		}

		containedFluidTooltip(tooltip, isPlayerSneaking, getFluidHandler());

		return added || true;
	}

	public enum IOMode implements INamedIconOptions {
		INPUT(AllIcons.I_REFRESH),
		OUTPUT(AllIcons.I_ROTATE_CCW);

		private final String translationKey;
		private final AllIcons icon;

		IOMode(AllIcons icon) {
			this.icon = icon;
			this.translationKey = "create.fluid.logistics_junction.io_mode." + Lang.asId(name());
		}

		@Override
		public AllIcons getIcon() {
			return icon;
		}

		@Override
		public String getTranslationKey() {
			return translationKey;
		}
	}

	private record OutputTarget(@Nullable IItemHandler items, @Nullable IFluidHandler fluids, boolean upper) {
	}

	private record InputTarget(@Nullable IItemHandler items, @Nullable IFluidHandler fluids, boolean upper) {
	}

	private static class UnpackPlan {
		private final List<ItemPlan> itemPlans = new ArrayList<>();
		private final List<FluidPlan> fluidPlans = new ArrayList<>();
	}

	private record ItemPlan(ItemStack stack, int targetIndex) {
	}

	private record FluidPlan(FluidEntry entry, int targetIndex) {
	}

	/** A real item insertion made during commit, kept so it can be extracted back out on rollback. */
	private record CommittedItem(IItemHandler handler, ItemStack stack) {
	}

	/** A real fluid fill made during commit, kept so it can be drained back out on rollback. */
	private record CommittedFluid(IFluidHandler handler, FluidStack stack) {
	}

	private class IOModeValueBox extends CenteredSideValueBoxTransform {
		private IOModeValueBox() {
			super((state, direction) -> direction.getAxis().isHorizontal()
				&& direction != state.getValue(LogisticsJunctionBlock.FACING));
		}

		@Override
		public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
			Vec3 location = VecHelper.voxelSpace(8, 13, 15.5);
			return VecHelper.rotateCentered(location, AngleHelper.horizontalAngle(this.getSide()), Direction.Axis.Y);
		}
	}

	private class ItemInputHandler implements IItemHandler {
		@Override
		public int getSlots() {
			return inventory.getSlots();
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			return ItemStack.EMPTY;
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			return inventory.insertItem(slot, stack, simulate);
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			return ItemStack.EMPTY;
		}

		@Override
		public int getSlotLimit(int slot) {
			return inventory.getSlotLimit(slot);
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return inventory.isItemValid(slot, stack);
		}
	}

	private class ItemOutputHandler implements IItemHandler {
		@Override
		public int getSlots() {
			return inventory.getSlots();
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			return inventory.getStackInSlot(slot);
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			return stack;
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			return inventory.extractItem(slot, amount, simulate);
		}

		@Override
		public int getSlotLimit(int slot) {
			return inventory.getSlotLimit(slot);
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return false;
		}
	}

	private class FluidInputHandler implements IFluidHandler {
		@Override
		public int getTanks() {
			return getFluidHandler().getTanks();
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			return FluidStack.EMPTY;
		}

		@Override
		public int getTankCapacity(int tank) {
			return getFluidHandler().getTankCapacity(tank);
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return getFluidHandler().isFluidValid(tank, stack);
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			return getFluidHandler().fill(resource, action);
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			return FluidStack.EMPTY;
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			return FluidStack.EMPTY;
		}
	}

	private class FluidOutputHandler implements IFluidHandler {
		@Override
		public int getTanks() {
			return getFluidHandler().getTanks();
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			return getFluidHandler().getFluidInTank(tank);
		}

		@Override
		public int getTankCapacity(int tank) {
			return getFluidHandler().getTankCapacity(tank);
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return false;
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			return 0;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			return getFluidHandler().drain(resource, action);
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			return getFluidHandler().drain(maxDrain, action);
		}
	}

	private static class ItemReservation {
		private final IItemHandler handler;
		private final List<ItemStack> virtualSlots;
		private boolean depotOccupied;

		private ItemReservation(@Nullable IItemHandler handler) {
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

		private FluidReservation(@Nullable IFluidHandler handler) {
			this.handler = handler;
			this.virtualTanks = new ArrayList<>();
			if (handler != null) {
				for (int tank = 0; tank < handler.getTanks(); tank++)
					virtualTanks.add(handler.getFluidInTank(tank).copy());
			}
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
