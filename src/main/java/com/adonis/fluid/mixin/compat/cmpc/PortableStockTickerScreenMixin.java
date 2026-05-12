package com.adonis.fluid.mixin.compat.cmpc;

import com.adonis.fluid.item.FluidManifestItem;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Pseudo
@Mixin(targets = "com.kreidev.cmpackagecouriers.stock_ticker.PortableStockTickerScreen", remap = false)
public abstract class PortableStockTickerScreenMixin extends AbstractSimiContainerScreen<AbstractContainerMenu> {

	@Unique
	private static final int FLUID_STEP = 100;
	@Unique
	private static final int FLUID_CTRL_STEP = 1000;
	@Unique
	private static final int FLUID_SHIFT_STEP = 50000;
	@Unique
	private static final String FLUID_KEY_CLASS = "ru.zznty.create_factory_logistics.logistics.generic.FluidKey";

	protected PortableStockTickerScreenMixin(AbstractContainerMenu container, Inventory playerInventory, Component title) {
		super(container, playerInventory, title);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
	private void fluid$handleFluidClick(double mouseX, double mouseY, int button,
		CallbackInfoReturnable<Boolean> cir) {
		if (button != 0 && button != 1) {
			return;
		}

		try {
			Object entry = fluid$getHoveredEntry((int) mouseX, (int) mouseY);
			if (!fluid$isFluidEntry(entry)) {
				return;
			}

			boolean remove = button == 1 || fluid$isHoveredOrder((int) mouseX, (int) mouseY);
			int transfer = hasShiftDown() ? FLUID_SHIFT_STEP : hasControlDown() ? FLUID_CTRL_STEP : FLUID_STEP;
			fluid$adjustOrderForEntry(entry, transfer, remove);
			cir.setReturnValue(true);
		} catch (ReflectiveOperationException ignored) {
		}
	}

	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, require = 0)
	private void fluid$handleFluidScroll(double mouseX, double mouseY, double scrollX, double scrollY,
		CallbackInfoReturnable<Boolean> cir) {
		if (scrollY == 0) {
			return;
		}

		try {
			Object entry = fluid$getHoveredEntry((int) mouseX, (int) mouseY);
			if (!fluid$isFluidEntry(entry)) {
				return;
			}

			boolean remove = scrollY < 0;
			int transfer = (hasShiftDown() ? FLUID_SHIFT_STEP : hasControlDown() ? FLUID_CTRL_STEP : FLUID_STEP)
				* Mth.ceil(Math.abs(scrollY));
			fluid$adjustOrderForEntry(entry, transfer, remove);
			cir.setReturnValue(true);
		} catch (ReflectiveOperationException ignored) {
		}
	}

	@WrapOperation(
		method = "renderForeground",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;renderComponentTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V"
		),
		require = 0
	)
	private void fluid$renderFluidTooltip(GuiGraphics graphics, net.minecraft.client.gui.Font font, List<Component> lines,
		int mouseX, int mouseY, Operation<Void> original) {
		try {
			Object entry = fluid$getHoveredEntry(mouseX, mouseY);
			if (!fluid$isFluidEntry(entry)) {
				original.call(graphics, font, lines, mouseX, mouseY);
				return;
			}

			List<Component> tooltip = new ArrayList<>(2);
			Component name = fluid$getFluidName(entry);
			if (name != null) {
				tooltip.add(name.copy());
			}
			original.call(graphics, font, tooltip, mouseX, mouseY);
		} catch (ReflectiveOperationException ignored) {
			original.call(graphics, font, lines, mouseX, mouseY);
		}
	}

	@Unique
	private void fluid$adjustOrderForEntry(Object entry, int transfer, boolean remove) throws ReflectiveOperationException {
		Object stack = fluid$getEntryStack(entry);
		Object existingOrder = fluid$invoke(this, "orderForStack", stack);
		List<Object> itemsToOrder = fluid$getField(this, "itemsToOrder", List.class);
		int cols = fluid$getField(this, "cols", Integer.class);

		if (existingOrder == null) {
			if (remove || itemsToOrder.size() >= cols) {
				return;
			}
			Object zeroedStack = fluid$invoke(stack, "withAmount", 0);
			existingOrder = fluid$invokeStatic(
				"ru.zznty.create_factory_abstractions.generic.support.BigGenericStack",
				"of",
				new Class<?>[]{Class.forName("ru.zznty.create_factory_abstractions.api.generic.stack.GenericStack")},
				zeroedStack);
			itemsToOrder.add(existingOrder);
		}

		int current = fluid$getEntryAmount(existingOrder);
		if (remove) {
			fluid$setBigStackAmount(existingOrder, Math.max(0, current - transfer));
			if (fluid$getEntryAmount(existingOrder) <= 0) {
				itemsToOrder.remove(existingOrder);
			}
			return;
		}

		int max = fluid$getEntryAmount(entry);
		fluid$setBigStackAmount(existingOrder, Math.min(max, current + transfer));
	}

	@Unique
	private boolean fluid$isHoveredOrder(int mouseX, int mouseY) throws ReflectiveOperationException {
		Object hovered = fluid$invoke(this, "getHoveredSlot", mouseX, mouseY);
		return fluid$getCouplePart(hovered, "getFirst") == -1;
	}

	@Unique
	private Object fluid$getHoveredEntry(int mouseX, int mouseY) throws ReflectiveOperationException {
		Object hovered = fluid$invoke(this, "getHoveredSlot", mouseX, mouseY);
		int first = fluid$getCouplePart(hovered, "getFirst");
		int second = fluid$getCouplePart(hovered, "getSecond");
		if (first == -1 && second == -1) {
			return null;
		}

		if (first == -2) {
			List<?> recipesToOrder = fluid$getField(this, "recipesToOrder", List.class);
			return second >= 0 && second < recipesToOrder.size() ? recipesToOrder.get(second) : null;
		}

		if (first == -1) {
			List<?> itemsToOrder = fluid$getField(this, "itemsToOrder", List.class);
			return second >= 0 && second < itemsToOrder.size() ? itemsToOrder.get(second) : null;
		}

		if (first < 0) {
			return null;
		}

		List<?> displayedItems = fluid$getField(this, "displayedItems", List.class);
		if (first >= displayedItems.size()) {
			return null;
		}

		Object category = displayedItems.get(first);
		if (!(category instanceof List<?> list) || second < 0 || second >= list.size()) {
			return null;
		}
		return list.get(second);
	}

	@Unique
	private static boolean fluid$isFluidEntry(Object entry) {
		return !fluid$getFluidFromEntry(entry).isEmpty();
	}

	@Unique
	private static Object fluid$getEntryStack(Object entry) throws ReflectiveOperationException {
		return entry == null ? null : fluid$invoke(entry, "get");
	}

	@Unique
	private static Object fluid$getEntryKey(Object entry) {
		try {
			Object stack = fluid$getEntryStack(entry);
			return stack == null ? null : fluid$invoke(stack, "key");
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	@Unique
	private static int fluid$getEntryAmount(Object entry) {
		try {
			Object stack = fluid$getEntryStack(entry);
			if (stack == null) {
				return 0;
			}
			Object amount = fluid$invoke(stack, "amount");
			return amount instanceof Integer integer ? integer : 0;
		} catch (ReflectiveOperationException e) {
			return 0;
		}
	}

	@Unique
	private static void fluid$setBigStackAmount(Object bigStack, int amount) throws ReflectiveOperationException {
		Method method = bigStack.getClass().getMethod("setAmount", int.class);
		method.setAccessible(true);
		method.invoke(bigStack, amount);
	}

	@Unique
	private static Component fluid$getFluidName(Object entry) {
		FluidStack fluid = fluid$getFluidFromEntry(entry);
		if (!fluid.isEmpty()) {
			return fluid.getHoverName().copy();
		}
		return null;
	}

	@Unique
	private static FluidStack fluid$getFluidFromEntry(Object entry) {
		Object key = fluid$getEntryKey(entry);
		return fluid$getFluidFromKey(key);
	}

	@Unique
	private static FluidStack fluid$getFluidFromKey(Object key) {
		if (key == null) {
			return FluidStack.EMPTY;
		}

		try {
			if (key.getClass().getName().equals(FLUID_KEY_CLASS)) {
				Object fluidStack = fluid$invoke(key, "stack");
				if (fluidStack instanceof FluidStack stack) {
					return stack;
				}
			}

			Object maybeItemStack = fluid$invoke(key, "stack");
			if (maybeItemStack instanceof ItemStack itemStack && itemStack.getItem() instanceof FluidManifestItem) {
				return FluidManifestItem.read(itemStack);
			}
		} catch (ReflectiveOperationException ignored) {
		}

		return FluidStack.EMPTY;
	}

	@Unique
	private static int fluid$getCouplePart(Object couple, String methodName) throws ReflectiveOperationException {
		Object value = fluid$invoke(couple, methodName);
		return value instanceof Integer integer ? integer : -1;
	}

	@Unique
	private static Object fluid$invoke(Object target, String methodName, Object... args) throws ReflectiveOperationException {
		Method selected = null;
		Class<?> current = target.getClass();
		while (current != null && selected == null) {
			for (Method method : current.getDeclaredMethods()) {
				if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
					continue;
				}
				Class<?>[] parameterTypes = method.getParameterTypes();
				boolean compatible = true;
				for (int i = 0; i < args.length; i++) {
					if (!fluid$isCompatibleParameter(parameterTypes[i], args[i])) {
						compatible = false;
						break;
					}
				}
				if (compatible) {
					selected = method;
					break;
				}
			}
			current = current.getSuperclass();
		}
		if (selected == null) {
			throw new NoSuchMethodException(methodName);
		}
		selected.setAccessible(true);
		return selected.invoke(target, args);
	}

	@Unique
	private static Object fluid$invokeStatic(String className, String methodName, Class<?>[] parameterTypes, Object... args)
		throws ReflectiveOperationException {
		Class<?> clazz = Class.forName(className);
		Method method = clazz.getMethod(methodName, parameterTypes);
		method.setAccessible(true);
		return method.invoke(null, args);
	}

	@Unique
	@SuppressWarnings("unchecked")
	private static <T> T fluid$getField(Object target, String fieldName, Class<T> type) throws ReflectiveOperationException {
		Class<?> current = target.getClass();
		Field field = null;
		while (current != null) {
			try {
				field = current.getDeclaredField(fieldName);
				break;
			} catch (NoSuchFieldException ignored) {
				current = current.getSuperclass();
			}
		}
		if (field == null) {
			throw new NoSuchFieldException(fieldName);
		}
		field.setAccessible(true);
		return (T) field.get(target);
	}

	@Unique
	private static boolean fluid$isCompatibleParameter(Class<?> parameterType, Object arg) {
		if (arg == null) {
			return !parameterType.isPrimitive();
		}
		if (parameterType.isInstance(arg)) {
			return true;
		}
		if (!parameterType.isPrimitive()) {
			return parameterType.isAssignableFrom(arg.getClass());
		}
		return (parameterType == int.class && arg instanceof Integer)
			|| (parameterType == boolean.class && arg instanceof Boolean)
			|| (parameterType == double.class && arg instanceof Double)
			|| (parameterType == float.class && arg instanceof Float)
			|| (parameterType == long.class && arg instanceof Long);
	}
}
