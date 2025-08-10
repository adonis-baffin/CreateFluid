package com.adonis.fluid.block.SmartFluidInterface;

import com.simibubi.create.content.logistics.itemHatch.HatchFilterSlot;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class SmartFluidInterfaceBlockEntity extends SmartBlockEntity {
    public FilteringBehaviour filtering;

    public SmartFluidInterfaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // 添加流体过滤行为，过滤槽位位于方块的外表面（突出部分）
        behaviours.add(filtering = new FilteringBehaviour(this, new SmartFluidInterfaceFilterSlot()).forFluids());
    }
}