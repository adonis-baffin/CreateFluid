package com.adonis.fluid.item;

import com.adonis.fluid.content.pipette.FluidInteractionPoint;
import com.adonis.fluid.packet.PipetteFluidPlacementPacket;
import com.simibubi.create.AllPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;

public class PipetteItem extends BlockItem {
    public PipetteItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level world = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = world.getBlockState(pos);
        if (isFluidInteractable(world, pos, state)) {
            return InteractionResult.SUCCESS;
        }
        return super.useOn(ctx);
    }

    /**
     * 检查方块是否可以作为流体交互点
     */
    private boolean isFluidInteractable(Level world, BlockPos pos, BlockState state) {
        return FluidInteractionPoint.create(world, pos, state) != null;
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level world, Player player, ItemStack stack, BlockState state) {
        if (!world.isClientSide && player instanceof ServerPlayer sp) {
            AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> sp),
                    new PipetteFluidPlacementPacket.ClientBoundRequest(pos));
        }
        return super.updateCustomBlockEntityTag(pos, world, player, stack, state);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level world, BlockPos pos, Player player) {
        return !isFluidInteractable(world, pos, state);
    }
}