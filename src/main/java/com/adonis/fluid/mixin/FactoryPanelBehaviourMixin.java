package com.adonis.fluid.mixin;

import com.adonis.fluid.block.CanFiller.CanFillerBlockEntity;
import com.adonis.fluid.client.FluidAmountHelper;
import com.adonis.fluid.datacomponent.BrassBoxRoutingData;
import com.adonis.fluid.datacomponent.BrassBoxRoutingData.FluidRoute;
import com.adonis.fluid.datacomponent.BrassBoxRoutingData.ItemRoute;
import com.adonis.fluid.item.FluidManifestItem;
import com.adonis.fluid.logistics.data.ContentRoute;
import com.adonis.fluid.logistics.manager.MixedOrderRoutingManager;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.RequestPromise;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.common.collect.Multimap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mixin(FactoryPanelBehaviour.class)
public class FactoryPanelBehaviourMixin {

	@Unique
	private static final int fluid$mbPerBucket = 1000;

	@ModifyVariable(method = "setFilter", at = @At("HEAD"), argsOnly = true)
	private ItemStack fluid$convertFluidContainerToManifest(ItemStack stack) {
		if (!(stack.getItem() instanceof FluidManifestItem))
			return stack;

		return fluid$normalizeManifest(stack);
	}

	@Unique
	private boolean fluid$hasFluidFilter(FactoryPanelBehaviour behaviour) {
		return behaviour.getFilter().getItem() instanceof FluidManifestItem;
	}

	@Unique
	private ItemStack fluid$normalizeManifest(ItemStack stack) {
		if (!(stack.getItem() instanceof FluidManifestItem))
			return stack;

		FluidStack fluid = FluidManifestItem.read(stack);
		if (fluid.isEmpty())
			return stack;

		return FluidManifestItem.of(fluid, 1);
	}

	@Unique
	private int fluid$bucketsToMb(int buckets) {
		return Math.max(0, buckets) * fluid$mbPerBucket;
	}

	@Unique
	private int fluid$boardValueToMb(ValueSettingsBehaviour.ValueSettings settings) {
		if (settings.row() == 1) {
			return fluid$bucketsToMb(settings.value());
		}
		return Math.max(0, settings.value()) * 10;
	}

	@Unique
	private ValueSettingsBehaviour.ValueSettings fluid$mbToBoardSettings(int amountMb) {
		if (amountMb >= fluid$mbPerBucket && amountMb % fluid$mbPerBucket == 0) {
			return new ValueSettingsBehaviour.ValueSettings(1, amountMb / fluid$mbPerBucket);
		}
		return new ValueSettingsBehaviour.ValueSettings(0, Math.max(0, amountMb / 10));
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

		if (value.row() == 1) {
			cir.setReturnValue(Component.literal(Math.max(0, value.value()) + "B"));
			return;
		}

		cir.setReturnValue(Component.literal(Math.max(0, value.value() * 10) + "mB"));
	}

	@Inject(method = "setValueSettings", at = @At("HEAD"), cancellable = true, remap = false)
	private void fluid$setBucketValueSettings(Player player, ValueSettingsBehaviour.ValueSettings settings, boolean ctrlDown,
		CallbackInfo ci) {
		FactoryPanelBehaviour behaviour = (FactoryPanelBehaviour) (Object) this;
		if (!fluid$hasFluidFilter(behaviour)) {
			return;
		}

		int storedAmountMb = fluid$boardValueToMb(settings);
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

		cir.setReturnValue(fluid$mbToBoardSettings(behaviour.count));
	}

	@Inject(method = "createBoard", at = @At("HEAD"), cancellable = true, remap = false)
	private void fluid$createBucketBoard(Player player, BlockHitResult hitResult,
		CallbackInfoReturnable<ValueSettingsBoard> cir) {
		FactoryPanelBehaviour behaviour = (FactoryPanelBehaviour) (Object) this;
		if (!fluid$hasFluidFilter(behaviour)) {
			return;
		}

		cir.setReturnValue(new ValueSettingsBoard(CreateLang.translate("factory_panel.target_amount")
			.component(), 100, 10, java.util.List.of(Component.literal("mB"), Component.literal("B")),
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
		ItemStack item = fluid$normalizeManifest(behaviour.getFilter());
		if (!(item.getItem() instanceof FluidManifestItem)) {
			return;
		}

		PackagerBlockEntity packager = behaviour.panelBE().getRestockedPackager();
		if (!(packager instanceof CanFillerBlockEntity canFiller)) {
			return;
		}

		// Exclude the can filler's own fluid stock, mirroring how Create's tryRestock passes
		// packager.targetInventory.getIdentifiedInventory(). Without this the can filler counts
		// itself as network supply and fulfills its own restock, addressing packages to itself.
		IdentifiedInventory ignoredInventory = canFiller.getIdentifiedFluidInventory();

		int availableOnNetwork = LogisticsManager.getStockOf(behaviour.network, item, ignoredInventory);
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
			ignoredInventory, behaviour.recipeAddress)) {
			ci.cancel();
			return;
		}

		behaviour.restockerPromises.add(new RequestPromise(orderedItem));
		ci.cancel();
	}

	@Redirect(method = "tickRequests",
		at = @At(value = "INVOKE",
			target = "Lcom/simibubi/create/content/logistics/packagerLink/LogisticsManager;findPackagersForRequest(Ljava/util/UUID;Lcom/simibubi/create/content/logistics/stockTicker/PackageOrderWithCrafts;Lcom/simibubi/create/content/logistics/packager/IdentifiedInventory;Ljava/lang/String;)Lcom/google/common/collect/Multimap;"))
	private Multimap<PackagerBlockEntity, com.simibubi.create.content.logistics.packager.PackagingRequest> fluid$attachRoutingToOrder(
		java.util.UUID network, PackageOrderWithCrafts order, IdentifiedInventory ignoredHandler, String address) {
		FactoryPanelBehaviour behaviour = (FactoryPanelBehaviour) (Object) this;
		BrassBoxRoutingData routing = fluid$buildRoutingData(behaviour, network, order);
		if (!routing.isEmpty())
			MixedOrderRoutingManager.attachToOrder(order, routing);
		return LogisticsManager.findPackagersForRequest(network, order, ignoredHandler, address);
	}

	@Unique
	private BrassBoxRoutingData fluid$buildRoutingData(FactoryPanelBehaviour behaviour, java.util.UUID network,
		PackageOrderWithCrafts order) {
		Map<String, ItemStack> itemKinds = new LinkedHashMap<>();
		Map<String, net.minecraft.resources.ResourceLocation> fluidKinds = new LinkedHashMap<>();
		Map<String, Integer> kindScores = new LinkedHashMap<>();
		int maxScore = Integer.MIN_VALUE;
		int minScore = Integer.MAX_VALUE;

		for (FactoryPanelConnection connection : behaviour.targetedBy.values()) {
			FactoryPanelBehaviour source = FactoryPanelBehaviour.at(behaviour.getWorld(), connection);
			if (source == null || !network.equals(source.network))
				continue;
			ItemStack filter = fluid$normalizeManifest(source.getFilter());
			if (filter.isEmpty())
				continue;
			int score = connection.from.pos().getY() * 2 + connection.from.slot().yOffset;
			maxScore = Math.max(maxScore, score);
			minScore = Math.min(minScore, score);

			if (filter.getItem() instanceof FluidManifestItem) {
				var key = FluidManifestItem.readKey(filter);
				if (key == null)
					continue;
				String kind = key.fluidId().toString();
				fluidKinds.putIfAbsent(kind, key.fluidId());
				kindScores.merge(kind, score, Math::max);
				continue;
			}

			String kind = filter.getItemHolder().unwrapKey().map(Object::toString).orElse(filter.getItem().toString()) + "#" + filter.getComponentsPatch();
			itemKinds.putIfAbsent(kind, filter.copyWithCount(1));
			kindScores.merge(kind, score, Math::max);
		}

		if (kindScores.isEmpty())
			return BrassBoxRoutingData.EMPTY;

		boolean allSameHeight = maxScore == minScore;
		List<ItemRoute> itemRoutes = new ArrayList<>();
		List<FluidRoute> fluidRoutes = new ArrayList<>();

		for (BigItemStack entry : order.stacks()) {
			ItemStack stack = fluid$normalizeManifest(entry.stack);
			if (stack.isEmpty())
				continue;
			if (stack.getItem() instanceof FluidManifestItem) {
				var key = FluidManifestItem.readKey(stack);
				if (key == null)
					continue;
				ContentRoute route = allSameHeight ? ContentRoute.NONE
					: kindScores.getOrDefault(key.fluidId().toString(), minScore) == maxScore ? ContentRoute.UP : ContentRoute.DOWN;
				fluidRoutes.add(new FluidRoute(key.fluidId(), route));
				continue;
			}
			String kind = stack.getItemHolder().unwrapKey().map(Object::toString).orElse(stack.getItem().toString()) + "#" + stack.getComponentsPatch();
			ContentRoute route = allSameHeight ? ContentRoute.NONE
				: kindScores.getOrDefault(kind, minScore) == maxScore ? ContentRoute.UP : ContentRoute.DOWN;
			itemRoutes.add(new ItemRoute(stack.copyWithCount(1), route));
		}

		return new BrassBoxRoutingData(itemRoutes, fluidRoutes);
	}
}
