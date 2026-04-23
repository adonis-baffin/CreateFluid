package com.adonis.fluid.mixin;

import com.adonis.fluid.block.canfiller.CanFillerBlockEntity;
import com.adonis.fluid.client.FluidAmountHelper;
import com.adonis.fluid.item.FluidManifestItem;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.RequestPromise;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FactoryPanelBehaviour.class)
public class FactoryPanelBehaviourMixin {

	@Unique
	private static final int fluid$mbPerBucket = 1000;

	@ModifyVariable(method = "setFilter", at = @At("HEAD"), argsOnly = true)
	private ItemStack fluid$convertFluidContainerToManifest(ItemStack stack) {
		if (stack.isEmpty() || stack.getItem() instanceof FluidManifestItem)
			return stack;

		Level level = ((FactoryPanelBehaviour) (Object) this).blockEntity.getLevel();
		if (level == null)
			return stack;

		if (GenericItemEmptying.canItemBeEmptied(level, stack)) {
			Pair<FluidStack, ItemStack> result = GenericItemEmptying.emptyItem(level, stack, true);
			FluidStack fluid = result.getFirst();
			if (!fluid.isEmpty()) {
				return FluidManifestItem.of(fluid, 1);
			}
		}

		return stack;
	}

	@Unique
	private boolean fluid$hasFluidFilter(FactoryPanelBehaviour behaviour) {
		return behaviour.getFilter().getItem() instanceof FluidManifestItem;
	}

	@Unique
	private int fluid$bucketsToMb(int buckets) {
		return Math.max(0, buckets) * fluid$mbPerBucket;
	}

	@Unique
	private int fluid$mbToBucketsForBoard(int amountMb) {
		if (amountMb <= 0) {
			return 0;
		}
		return Math.max(1, (int) Math.ceil(amountMb / (double) fluid$mbPerBucket));
	}

	@Unique
	private String fluid$formatBuckets(int amountMb) {
		return FluidAmountHelper.format(amountMb) + "B";
	}

	@Inject(method = "formatValue", at = @At("HEAD"), cancellable = true, remap = false)
	private void fluid$formatValue(ValueSettingsBehaviour.ValueSettings value,
		CallbackInfoReturnable<MutableComponent> cir) {
		FactoryPanelBehaviour behaviour = (FactoryPanelBehaviour) (Object) this;
		if (!fluid$hasFluidFilter(behaviour)) {
			return;
		}

		if (value.value() == 0) {
			cir.setReturnValue(CreateLang.translateDirect("gui.factory_panel.inactive"));
			return;
		}

		cir.setReturnValue(Component.literal(Math.max(0, value.value()) + "B"));
	}

	@Inject(method = "setValueSettings", at = @At("HEAD"), cancellable = true, remap = false)
	private void fluid$setBucketValueSettings(Player player, ValueSettingsBehaviour.ValueSettings settings, boolean ctrlDown,
		CallbackInfo ci) {
		FactoryPanelBehaviour behaviour = (FactoryPanelBehaviour) (Object) this;
		if (!fluid$hasFluidFilter(behaviour)) {
			return;
		}

		int storedAmountMb = fluid$bucketsToMb(settings.value());
		if (behaviour.count == storedAmountMb && behaviour.upTo) {
			ci.cancel();
			return;
		}

		behaviour.count = storedAmountMb;
		behaviour.upTo = true;
		behaviour.panelBE().redraw = true;
		behaviour.blockEntity.setChanged();
		behaviour.blockEntity.sendData();
		((ValueSettingsBehaviour) behaviour).playFeedbackSound(behaviour);
		behaviour.resetTimerSlightly();
		ci.cancel();
	}

	@Inject(method = "getValueSettings", at = @At("HEAD"), cancellable = true, remap = false)
	private void fluid$getBucketValueSettings(CallbackInfoReturnable<ValueSettingsBehaviour.ValueSettings> cir) {
		FactoryPanelBehaviour behaviour = (FactoryPanelBehaviour) (Object) this;
		if (!fluid$hasFluidFilter(behaviour)) {
			return;
		}

		cir.setReturnValue(new ValueSettingsBehaviour.ValueSettings(0, fluid$mbToBucketsForBoard(behaviour.count)));
	}

	@Inject(method = "createBoard", at = @At("HEAD"), cancellable = true, remap = false)
	private void fluid$createBucketBoard(Player player, BlockHitResult hitResult,
		CallbackInfoReturnable<ValueSettingsBoard> cir) {
		FactoryPanelBehaviour behaviour = (FactoryPanelBehaviour) (Object) this;
		if (!fluid$hasFluidFilter(behaviour)) {
			return;
		}

		cir.setReturnValue(new ValueSettingsBoard(CreateLang.translate("factory_panel.target_amount")
			.component(), 100, 10, java.util.List.of(Component.literal("Buckets")),
			new ValueSettingsFormatter(behaviour::formatValue)));
	}

	@Inject(method = "getCountLabelForValueBox", at = @At("HEAD"), cancellable = true, remap = false)
	private void fluid$getBucketCountLabel(CallbackInfoReturnable<MutableComponent> cir) {
		FactoryPanelBehaviour behaviour = (FactoryPanelBehaviour) (Object) this;
		if (!fluid$hasFluidFilter(behaviour)) {
			return;
		}
		if (behaviour.getFilter().isEmpty()) {
			cir.setReturnValue(Component.empty());
			return;
		}
		if (behaviour.waitingForNetwork) {
			cir.setReturnValue(Component.literal("?"));
			return;
		}

		int levelInStorage = behaviour.getLevelInStorage();
		boolean inf = levelInStorage >= BigItemStack.INF;
		int promised = behaviour.getPromised();
		String inStorageText = inf ? "  \u221e" : fluid$formatBuckets(levelInStorage);
		String targetText = fluid$formatBuckets(behaviour.count);

		if (behaviour.count == 0) {
			cir.setReturnValue(CreateLang.text(inStorageText)
				.color(0xF1EFE8)
				.component());
			return;
		}

		cir.setReturnValue(CreateLang.text(inf ? "  \u221e" : " " + inStorageText)
			.color(behaviour.satisfied ? 0xD7FFA8 : behaviour.promisedSatisfied ? 0xffcd75 : 0xFFBFA8)
			.add(CreateLang.text(promised == 0 ? "" : "\u23F6"))
			.add(CreateLang.text("/")
				.style(ChatFormatting.WHITE))
			.add(CreateLang.text(targetText + " ")
				.color(0xF1EFE8))
			.component());
	}

	@Inject(method = "tryRestock", at = @At("HEAD"), cancellable = true, remap = false)
	private void fluid$tryRestockFromCanFiller(CallbackInfo ci) {
		FactoryPanelBehaviour behaviour = (FactoryPanelBehaviour) (Object) this;
		ItemStack item = behaviour.getFilter();
		if (!(item.getItem() instanceof FluidManifestItem)) {
			return;
		}

		PackagerBlockEntity packager = behaviour.panelBE().getRestockedPackager();
		if (!(packager instanceof CanFillerBlockEntity)) {
			return;
		}

		int availableOnNetwork = LogisticsManager.getStockOf(behaviour.network, item, null);
		if (availableOnNetwork == 0) {
			ci.cancel();
			return;
		}

		int inStorage = behaviour.getLevelInStorage();
		int promised = behaviour.getPromised();
		int demand = behaviour.getAmount();
		int amountToOrder = Math.max(demand - promised - inStorage, 0);
		if (amountToOrder <= 0) {
			ci.cancel();
			return;
		}

		BigItemStack orderedItem = new BigItemStack(item, Math.min(amountToOrder, availableOnNetwork));
		PackageOrderWithCrafts order = PackageOrderWithCrafts.simple(java.util.List.of(orderedItem));

		if (!LogisticsManager.broadcastPackageRequest(behaviour.network,
			com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType.RESTOCK, order,
			null, behaviour.recipeAddress)) {
			ci.cancel();
			return;
		}

		behaviour.restockerPromises.add(new RequestPromise(orderedItem));
		ci.cancel();
	}
}
