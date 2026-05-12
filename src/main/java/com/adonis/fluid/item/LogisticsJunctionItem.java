package com.adonis.fluid.item;

import com.adonis.fluid.block.LogisticsJunction.LogisticsJunctionBlockEntity;
import com.adonis.fluid.packet.LogisticsJunctionPlacementPacket;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class LogisticsJunctionItem extends BlockItem {
    public LogisticsJunctionItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        if (LogisticsJunctionBlockEntity.isValidTarget(level, pos, face))
            return InteractionResult.SUCCESS;
        return super.useOn(context);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player, ItemStack stack,
                                                 BlockState state) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            CatnipServices.NETWORK.sendToClient(serverPlayer, new LogisticsJunctionPlacementPacket.ClientBoundRequest(pos));
        }
        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        for (Direction direction : Direction.values()) {
            if (LogisticsJunctionBlockEntity.isValidTarget(level, pos, direction))
                return false;
        }
        return true;
    }
}
