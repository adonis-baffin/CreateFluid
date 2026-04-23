package com.adonis.fluid.mixin;

import com.adonis.fluid.block.canfiller.CanFillerBlockEntity;
import com.adonis.fluid.registry.CFBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity;
import com.tterrag.registrate.util.entry.BlockEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FactoryPanelBlockEntity.class)
public class FactoryPanelBlockEntityMixin {

	@Inject(method = "getRestockedPackager", at = @At("HEAD"), cancellable = true, remap = false)
	private void fluid$getCanFillerAsRestockedPackager(CallbackInfoReturnable<PackagerBlockEntity> cir) {
		FactoryPanelBlockEntity be = (FactoryPanelBlockEntity) (Object) this;
		if (!fluid$isFactoryGauge(be)) {
			return;
		}

		BlockEntity attached = fluid$getAttachedBlockEntity(be);
		if (!(attached instanceof CanFillerBlockEntity canFiller)) {
			return;
		}

		cir.setReturnValue(canFiller);
	}

	@Redirect(
		method = "lazyTick",
		at = @At(
			value = "INVOKE",
			target = "Lcom/tterrag/registrate/util/entry/BlockEntry;has(Lnet/minecraft/world/level/block/state/BlockState;)Z"
		),
		remap = false
	)
	private boolean fluid$recognizeCanFillerBlock(BlockEntry<?> entry, BlockState state) {
		return entry.has(state) || CFBlocks.CAN_FILLER.has(state);
	}

	@Inject(method = "read", at = @At("TAIL"), remap = false)
	private void fluid$syncRestockerStateOnRead(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket,
		CallbackInfo ci) {
		FactoryPanelBlockEntity be = (FactoryPanelBlockEntity) (Object) this;
		if (!fluid$isFactoryGauge(be)) {
			return;
		}

		BlockEntity attached = fluid$getAttachedBlockEntity(be);
		if (attached instanceof CanFillerBlockEntity
			|| attached instanceof PackagerBlockEntity && !(attached instanceof RepackagerBlockEntity)) {
			be.restocker = true;
		}
	}

	private static boolean fluid$isFactoryGauge(FactoryPanelBlockEntity be) {
		return be.getLevel() != null && AllBlocks.FACTORY_GAUGE.has(be.getBlockState());
	}

	private static BlockEntity fluid$getAttachedBlockEntity(FactoryPanelBlockEntity be) {
		BlockState state = be.getBlockState();
		BlockPos attachedPos = be.getBlockPos()
			.relative(FactoryPanelBlock.connectedDirection(state).getOpposite());
		if (!be.getLevel().isLoaded(attachedPos)) {
			return null;
		}
		return be.getLevel().getBlockEntity(attachedPos);
	}
}
