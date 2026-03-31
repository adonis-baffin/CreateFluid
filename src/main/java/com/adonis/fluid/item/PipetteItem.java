package com.adonis.fluid.item;

import com.adonis.fluid.content.pipette.FluidInteractionPoint;
import com.adonis.fluid.packet.PipetteFluidPlacementPacket;
import com.adonis.fluid.registry.CFBlocks;
import net.createmod.catnip.platform.CatnipServices;
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

public class PipetteItem extends BlockItem {

    public PipetteItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level world = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = world.getBlockState(pos);

        // 检查是否可以作为流体交互点
        if (FluidInteractionPoint.create(world, pos, state) != null) {
            return InteractionResult.SUCCESS;
        }

        // 否则正常放置移液器
        return super.useOn(ctx);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level world, Player player,
                                                 ItemStack stack, BlockState state) {
        // 使用 CatnipServices 发送网络包（与 ArmItem 保持一致）
        if (!world.isClientSide && player instanceof ServerPlayer sp) {
            CatnipServices.NETWORK.sendToClient(sp,
                    new PipetteFluidPlacementPacket.ClientBoundRequest(pos));
        }

        return super.updateCustomBlockEntityTag(pos, world, player, stack, state);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level world, BlockPos pos, Player player) {
        // 防止破坏可交互的方块
        return FluidInteractionPoint.create(world, pos, state) == null;
    }
}