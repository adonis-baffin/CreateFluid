package com.adonis.fluid.item;

import com.adonis.fluid.packet.PipettePlacementPacket;
import com.simibubi.create.AllPackets;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
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
        return ArmInteractionPoint.isInteractable(world, pos, world.getBlockState(pos)) ? 
            InteractionResult.SUCCESS : super.useOn(ctx);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level world, Player player, ItemStack stack, BlockState state) {
        if (!world.isClientSide && player instanceof ServerPlayer sp) {
            AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> sp), 
                new PipettePlacementPacket.ClientBoundRequest(pos));
        }
        return super.updateCustomBlockEntityTag(pos, world, player, stack, state);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level world, BlockPos pos, Player player) {
        return !ArmInteractionPoint.isInteractable(world, pos, state);
    }
}