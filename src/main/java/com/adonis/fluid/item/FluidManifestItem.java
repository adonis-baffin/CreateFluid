package com.adonis.fluid.item;

import com.adonis.fluid.datacomponent.FluidManifestContent;
import com.adonis.fluid.logistics.data.FluidRequestKey;
import com.adonis.fluid.registry.CFBlocks;
import com.adonis.fluid.registry.CFDataComponents;
import com.adonis.fluid.registry.CFFluids;
import com.adonis.fluid.registry.CFItems;

import net.minecraft.ChatFormatting;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import net.createmod.catnip.data.Pair;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

public class FluidManifestItem extends Item {
	public FluidManifestItem(Properties properties) {
		super(properties);
	}

	public static ItemStack of(FluidStack fluid) {
		return of(fluid, fluid.getAmount());
	}

	public static ItemStack of(FluidStack fluid, int amount) {
		ItemStack stack = new ItemStack(CFItems.FLUID_MANIFEST.get());
		stack.set(CFDataComponents.FLUID_MANIFEST.get(),
			new FluidManifestContent(BuiltInRegistries.FLUID.getKey(fluid.getFluid()), fluid.isEmpty() ? 0 : 1));
		return stack;
	}

	public static FluidStack read(ItemStack stack) {
		FluidManifestContent content = stack.get(CFDataComponents.FLUID_MANIFEST.get());
		if (content == null || content.fluidId() == null)
			return FluidStack.EMPTY;
		Fluid fluid = BuiltInRegistries.FLUID.get(content.fluidId());
		return fluid == null ? FluidStack.EMPTY : new FluidStack(fluid, 1);
	}

	public static boolean isEmpty(ItemStack stack) {
		return read(stack).isEmpty();
	}

	public static FluidRequestKey readKey(ItemStack stack) {
		FluidManifestContent content = stack.get(CFDataComponents.FLUID_MANIFEST.get());
		if (content == null || content.fluidId() == null)
			return null;
		return new FluidRequestKey(content.fluidId());
	}

	public static int readAmount(ItemStack stack) {
		FluidManifestContent content = stack.get(CFDataComponents.FLUID_MANIFEST.get());
		if (content == null)
			return 0;
		return Math.max(content.amount(), 0);
	}

	@Override
	public Component getName(ItemStack stack) {
		FluidStack fluid = read(stack);
		if (!fluid.isEmpty()) {
			return Component.translatable("item.fluid.fluid_manifest.named", fluid.getHoverName());
		}
		return super.getName(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
		super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
		// Tooltip 由 ItemDescription.Modifier (tooltip.summary) 提供，不在这里额外添加
	}

	@Override
	public int getMaxStackSize(ItemStack stack) {
		return 64;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Direction side = context.getClickedFace();
		Player player = context.getPlayer();
		ItemStack heldStack = context.getItemInHand();

		if (level.isClientSide())
			return InteractionResult.SUCCESS;

		// 潜行右键清空
		if (player != null && player.isShiftKeyDown() && !isEmpty(heldStack)) {
			fluid$writeFluid(heldStack, FluidStack.EMPTY);
			fluid$playClearSound(level, pos);
			return InteractionResult.SUCCESS;
		}

		IFluidHandler fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
		if (fluidHandler != null) {
			for (int i = 0; i < fluidHandler.getTanks(); i++) {
				FluidStack fluid = fluidHandler.getFluidInTank(i);
				if (!fluid.isEmpty()) {
					fluid$writeFluid(heldStack, fluid);
					fluid$playSampleSound(level, pos);
					return InteractionResult.SUCCESS;
				}
			}
		}

		FluidState fluidState = level.getFluidState(pos);
		if (!fluidState.isEmpty()) {
			fluid$writeFluid(heldStack, new FluidStack(fluidState.getType(), 1));
			fluid$playSampleSound(level, pos);
			return InteractionResult.SUCCESS;
		}

		// 射线穿过流体命中后方方块时，检测流体实际所在的相邻位置
		if (fluidHandler == null) {
			BlockPos fluidPos = pos.relative(side);
			FluidState adjacentFluid = level.getFluidState(fluidPos);
			if (!adjacentFluid.isEmpty()) {
				fluid$writeFluid(heldStack, new FluidStack(adjacentFluid.getType(), 1));
				fluid$playSampleSound(level, fluidPos);
				return InteractionResult.SUCCESS;
			}
		}

		BlockState state = level.getBlockState(pos);
		if (state.is(CFBlocks.QUICKSAND.get())) {
			fluid$writeFluid(heldStack, new FluidStack(CFFluids.QUICKSAND_SOURCE.get(), 1));
			fluid$playSampleSound(level, pos);
			return InteractionResult.SUCCESS;
		}
		if (state.is(Blocks.POWDER_SNOW)) {
			fluid$writeFluid(heldStack, new FluidStack(CFFluids.POWDER_SNOW.get(), 1));
			fluid$playSampleSound(level, pos);
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
		ItemStack heldStack = player.getItemInHand(usedHand);

		// 潜行右键空气清空
		if (player.isShiftKeyDown() && !isEmpty(heldStack)) {
			if (!level.isClientSide()) {
				fluid$writeFluid(heldStack, FluidStack.EMPTY);
				fluid$playClearSound(level, player.blockPosition());
			}
			return InteractionResultHolder.sidedSuccess(heldStack, level.isClientSide());
		}

		// 射线检测流体方块（流动的流体和源方块）
		var hitResult = player.pick(player.blockInteractionRange(), 1.0F, false);
		if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
			net.minecraft.world.phys.BlockHitResult blockHit = (net.minecraft.world.phys.BlockHitResult) hitResult;
			BlockPos pos = blockHit.getBlockPos();
			FluidState fluidState = level.getFluidState(pos);
			if (fluidState.isEmpty()) {
				BlockPos fluidPos = pos.relative(blockHit.getDirection());
				fluidState = level.getFluidState(fluidPos);
			}
			if (!fluidState.isEmpty()) {
				if (!level.isClientSide()) {
					Fluid fluid = fluidState.getType();
					if (fluid instanceof net.minecraft.world.level.material.FlowingFluid flowing) {
						fluid = flowing.getSource();
					}
					fluid$writeFluid(heldStack, new FluidStack(fluid, 1));
					fluid$playSampleSound(level, pos);
				}
				return InteractionResultHolder.sidedSuccess(heldStack, level.isClientSide());
			}
		}

		InteractionHand otherHand = usedHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack otherStack = player.getItemInHand(otherHand);

		if (otherStack.isEmpty() || !GenericItemEmptying.canItemBeEmptied(level, otherStack))
			return super.use(level, player, usedHand);

		Pair<FluidStack, ItemStack> result = GenericItemEmptying.emptyItem(level, otherStack, true);
		FluidStack fluid = result.getFirst();
		if (fluid.isEmpty())
			return super.use(level, player, usedHand);

		if (!level.isClientSide()) {
			fluid$writeFluid(heldStack, fluid);
			fluid$playSampleSound(level, player.blockPosition());
		}

		return InteractionResultHolder.sidedSuccess(heldStack, level.isClientSide());
	}

	@Override
	public boolean overrideStackedOnOther(ItemStack stack, Slot slot, ClickAction action, Player player) {
		if (stack.getCount() != 1 || action != ClickAction.SECONDARY)
			return false;

		ItemStack slotItem = slot.getItem();
		if (!fluid$canSampleFrom(player.level(), slotItem))
			return false;

		FluidStack fluid = fluid$getFluidToSample(player.level(), slotItem);
		if (fluid.isEmpty())
			return false;

		fluid$writeFluid(stack, fluid);
		fluid$playSampleSound(player.level(), player.blockPosition());
		return true;
	}

	@Override
	public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player,
		SlotAccess access) {
		if (stack.getCount() != 1 || action != ClickAction.SECONDARY || !slot.allowModification(player))
			return false;

		if (!fluid$canSampleFrom(player.level(), other))
			return false;

		FluidStack fluid = fluid$getFluidToSample(player.level(), other);
		if (fluid.isEmpty())
			return false;

		fluid$writeFluid(stack, fluid);
		fluid$playSampleSound(player.level(), player.blockPosition());
		return true;
	}

	private static boolean fluid$canSampleFrom(Level level, ItemStack stack) {
		return !stack.isEmpty() && GenericItemEmptying.canItemBeEmptied(level, stack);
	}

	private static FluidStack fluid$getFluidToSample(Level level, ItemStack stack) {
		if (!fluid$canSampleFrom(level, stack))
			return FluidStack.EMPTY;

		Pair<FluidStack, ItemStack> result = GenericItemEmptying.emptyItem(level, stack, true);
		return result.getFirst();
	}

	private static void fluid$writeFluid(ItemStack stack, FluidStack fluid) {
		stack.set(CFDataComponents.FLUID_MANIFEST.get(),
			new FluidManifestContent(BuiltInRegistries.FLUID.getKey(fluid.getFluid()), fluid.isEmpty() ? 0 : 1));
	}

	private static void fluid$playSampleSound(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
	}

	private static void fluid$playClearSound(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
	}
}
