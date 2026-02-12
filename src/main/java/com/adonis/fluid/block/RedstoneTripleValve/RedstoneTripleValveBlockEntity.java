package com.adonis.fluid.block.RedstoneTripleValve;

import java.util.List;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;

public class RedstoneTripleValveBlockEntity extends SmartBlockEntity {

    public RedstoneTripleValveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new TripleValvePipeBehaviour(this));
        registerAwardables(behaviours, FluidPropagator.getSharedTriggers());
    }

    /**
     * 三通阀门的流体传输行为。
     *
     * 始终只有两个口通流：固定口 + 当前活跃的侧口。
     * 红石信号切换哪个侧口活跃。
     * 效果等同于一个可切换转弯方向的管道。
     */
    class TripleValvePipeBehaviour extends FluidTransportBehaviour {

        public TripleValvePipeBehaviour(SmartBlockEntity be) {
            super(be);
        }

        /**
         * 三个端口都建立连接（这样邻居管道不会认为关闭的侧口是"开口"而放水）。
         * 实际通断由 canPullFluidFrom 控制。
         */
        @Override
        public boolean canHaveFlowToward(BlockState state, Direction direction) {
            return RedstoneTripleValveBlock.isAnyPort(state, direction);
        }

        /**
         * 只有当前打开的两个口才允许流体通过。
         * 关闭的侧口虽然有连接（防止放水），但不允许流体流通。
         */
        @Override
        public boolean canPullFluidFrom(FluidStack fluid, BlockState state, Direction direction) {
            if (RedstoneTripleValveBlock.isOpenAt(state, direction))
                return super.canPullFluidFrom(fluid, state, direction);
            return false;
        }

        @Override
        public AttachmentTypes getRenderedRimAttachment(BlockAndTintGetter world, BlockPos pos,
                                                        BlockState state, Direction direction) {
            AttachmentTypes attachment = super.getRenderedRimAttachment(world, pos, state, direction);
            if (attachment == AttachmentTypes.RIM)
                return AttachmentTypes.NONE;
            return attachment.withoutConnector();
        }
    }
}