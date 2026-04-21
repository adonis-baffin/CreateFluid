package com.adonis.fluid.item;

import com.adonis.fluid.datacomponent.FluidManifestContent;
import com.adonis.fluid.registry.CFDataComponents;
import com.adonis.fluid.registry.CFItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

public class FluidManifestItem extends Item {
	public FluidManifestItem(Properties properties) {
		super(properties);
	}

	public static ItemStack of(FluidStack fluid) {
		ItemStack stack = new ItemStack(CFItems.FLUID_MANIFEST.get());
		stack.set(CFDataComponents.FLUID_MANIFEST.get(), new FluidManifestContent(fluid.copyWithAmount(1)));
		return stack;
	}

	public static FluidStack read(ItemStack stack) {
		FluidManifestContent content = stack.get(CFDataComponents.FLUID_MANIFEST.get());
		return content != null ? content.fluid() : FluidStack.EMPTY;
	}

	public static boolean isEmpty(ItemStack stack) {
		return read(stack).isEmpty();
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
		FluidStack fluid = read(stack);
		if (fluid.isEmpty()) {
			tooltipComponents.add(Component.translatable("item.fluid.fluid_manifest.empty")
				.withStyle(ChatFormatting.GRAY));
		}
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Direction side = context.getClickedFace();
		Player player = context.getPlayer();
		ItemStack heldStack = context.getItemInHand();

		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!isEmpty(heldStack)) {
			return InteractionResult.PASS;
		}

		IFluidHandler fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
		if (fluidHandler == null) {
			return InteractionResult.PASS;
		}

		for (int i = 0; i < fluidHandler.getTanks(); i++) {
			FluidStack fluid = fluidHandler.getFluidInTank(i);
			if (!fluid.isEmpty()) {
				heldStack.set(CFDataComponents.FLUID_MANIFEST.get(), new FluidManifestContent(fluid.copyWithAmount(1)));
				level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
				return InteractionResult.SUCCESS;
			}
		}

		return InteractionResult.PASS;
	}
}
