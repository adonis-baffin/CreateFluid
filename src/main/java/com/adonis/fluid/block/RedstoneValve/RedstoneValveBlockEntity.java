package com.adonis.fluid.block.RedstoneValve;

import java.util.List;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity.StraightPipeFluidTransportBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;

public class RedstoneValveBlockEntity extends SmartBlockEntity {

    public RedstoneValveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new RedstoneValvePipeBehaviour(this));
        registerAwardables(behaviours, FluidPropagator.getSharedTriggers());
    }

    /**
     * 红石阀门的流体传输行为。
     * 管道方向由 AXIS 属性决定，流体通过由 ENABLED 属性控制。
     */
    class RedstoneValvePipeBehaviour extends StraightPipeFluidTransportBehaviour {

        public RedstoneValvePipeBehaviour(SmartBlockEntity be) {
            super(be);
        }

        @Override
        public boolean canHaveFlowToward(BlockState state, Direction direction) {
            return RedstoneValveBlock.getPipeAxis(state) == direction.getAxis();
        }

        @Override
        public boolean canPullFluidFrom(FluidStack fluid, BlockState state, Direction direction) {
            if (state.hasProperty(RedstoneValveBlock.ENABLED) && state.getValue(RedstoneValveBlock.ENABLED))
                return super.canPullFluidFrom(fluid, state, direction);
            return false;
        }

        @Override
        public AttachmentTypes getRenderedRimAttachment(BlockAndTintGetter world, BlockPos pos,
                                                        BlockState state, Direction direction) {
            // 获取默认逻辑判断的附件类型
            AttachmentTypes attachment = super.getRenderedRimAttachment(world, pos, state, direction);
            return AttachmentTypes.NONE;
        }
    }
}