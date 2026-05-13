package com.adonis.fluid.content.pipette;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public class SnowmanCoolerFluidInteractionPoint extends FluidInteractionPoint {
	private static final String CMR_MOD_ID = "cmr";
	private static final String SNOWMAN_COOLER_BLOCK_ID = CMR_MOD_ID + ":snowman_cooler";
	private static final int MAX_HEAT_CAPACITY = 10000;
	private static final int INSERTION_THRESHOLD = 500;

	public SnowmanCoolerFluidInteractionPoint(Level level, BlockPos pos, BlockState state) {
		super(level, pos, state);
		mode = Mode.DEPOSIT;
	}

	@Override
	public boolean isValid() {
		if (level == null)
			return false;

		long gameTime = level.getGameTime();
		if (gameTime == lastKnownValid)
			return true;

		boolean valid = isSnowmanCooler(level.getBlockState(pos)) && level.getBlockEntity(pos) != null;
		if (valid)
			lastKnownValid = gameTime;
		return valid;
	}

	@Override
	public FluidStack extract(int maxAmount, boolean simulate) {
		return FluidStack.EMPTY;
	}

	@Override
	public FluidStack insert(FluidStack stack, boolean simulate) {
		int filled = fill(stack, simulate);
		if (filled <= 0)
			return stack;

		FluidStack remainder = stack.copy();
		remainder.shrink(filled);
		return remainder;
	}

	@Override
	public boolean canExtract() {
		return false;
	}

	@Override
	public boolean canInsert(FluidStack stack) {
		return fill(stack, true) > 0;
	}

	@Override
	public void cycleMode() {
		mode = Mode.DEPOSIT;
	}

	private int fill(FluidStack stack, boolean simulate) {
		if (stack.isEmpty() || !isValid())
			return 0;

		SnowmanCoolerFuelRegistry.Entry fuelEntry = SnowmanCoolerFuelRegistry.find(stack).orElse(null);
		if (fuelEntry == null)
			return 0;

		BlockEntity be = level.getBlockEntity(pos);
		if (be == null || isCreative(be))
			return 0;

		int newBurnTime = fuelEntry.getBurnTimeForAmount(stack.getAmount());
		if (newBurnTime <= 0)
			return 0;

		try {
			Object activeFuel = getField(be, "activeFuel");
			int remainingBurnTime = (int) getField(be, "remainingBurnTime");
			Object newFuel = enumValue(activeFuel.getClass(), fuelEntry.freezing() ? "SPECIAL" : "NORMAL");

			if (((Enum<?>) newFuel).ordinal() < ((Enum<?>) activeFuel).ordinal())
				return 0;

			if (newFuel == activeFuel) {
				if (remainingBurnTime <= INSERTION_THRESHOLD) {
					newBurnTime += remainingBurnTime;
				} else {
					return 0;
				}
			}

			newBurnTime = Math.min(newBurnTime, MAX_HEAT_CAPACITY);

			if (!simulate) {
				setField(be, "activeFuel", newFuel);
				setField(be, "remainingBurnTime", newBurnTime);
				invokeNoArgs(be, "updateBlockState");
				if (stack.getAmount() >= 250)
					invokeNoArgs(be, "playSound");
			}

			return stack.getAmount();
		} catch (ReflectiveOperationException | ClassCastException e) {
			return 0;
		}
	}

	private static boolean isSnowmanCooler(BlockState state) {
		return state.getBlock().builtInRegistryHolder().key().location().toString().equals(SNOWMAN_COOLER_BLOCK_ID);
	}

	private static boolean isCreative(BlockEntity be) {
		try {
			Method method = be.getClass().getMethod("isCreative");
			Object result = method.invoke(be);
			return result instanceof Boolean value && value;
		} catch (ReflectiveOperationException ignored) {
			return false;
		}
	}

	private static Object getField(Object target, String name) throws ReflectiveOperationException {
		Field field = findField(target.getClass(), name);
		field.setAccessible(true);
		return field.get(target);
	}

	private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
		Field field = findField(target.getClass(), name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
		Class<?> current = type;
		while (current != null) {
			try {
				return current.getDeclaredField(name);
			} catch (NoSuchFieldException ignored) {
				current = current.getSuperclass();
			}
		}
		throw new NoSuchFieldException(name);
	}

	private static void invokeNoArgs(Object target, String name) throws ReflectiveOperationException {
		Method method = findMethod(target.getClass(), name);
		method.setAccessible(true);
		method.invoke(target);
	}

	private static Method findMethod(Class<?> type, String name) throws NoSuchMethodException {
		Class<?> current = type;
		while (current != null) {
			try {
				return current.getDeclaredMethod(name);
			} catch (NoSuchMethodException ignored) {
				current = current.getSuperclass();
			}
		}
		throw new NoSuchMethodException(name);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object enumValue(Class<?> enumClass, String name) {
		return Enum.valueOf((Class<? extends Enum>) enumClass, name.toUpperCase(Locale.ROOT));
	}
}
