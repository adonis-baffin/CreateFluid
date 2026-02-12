package com.adonis.fluid.block.RedstoneTripleValve;

import java.util.List;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidReactions;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
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
     *
     * canHaveFlowToward 对三个口都返回 true（防止关闭侧口被判定为开口放水）。
     * canPullFluidFrom 控制 inbound 方向的拉取。
     * tick 中重写 internalFluid 分配逻辑，阻止向关闭侧口推送 outbound 流体。
     */
    class TripleValvePipeBehaviour extends FluidTransportBehaviour {

        public TripleValvePipeBehaviour(SmartBlockEntity be) {
            super(be);
        }

        @Override
        public boolean canHaveFlowToward(BlockState state, Direction direction) {
            return RedstoneTripleValveBlock.isAnyPort(state, direction);
        }

        @Override
        public boolean canPullFluidFrom(FluidStack fluid, BlockState state, Direction direction) {
            if (RedstoneTripleValveBlock.isOpenAt(state, direction))
                return super.canPullFluidFrom(fluid, state, direction);
            return false;
        }

        /**
         * 重写 tick 以控制 internalFluid 的分配。
         *
         * 原版逻辑中，internalFluid（从其他连接流入的流体）会被无条件推送到所有连接。
         * 我们需要对关闭的侧口不传递 internalFluid，否则流体会从关闭的口流出。
         */
        @Override
        public void tick() {
            // 调用 BlockEntityBehaviour.tick() 而不是 FluidTransportBehaviour.tick()
            // 因为我们需要完全接管流体分配逻辑
            Level world = getWorld();
            BlockPos pos = getPos();
            boolean onServer = !world.isClientSide || blockEntity.isVirtual();

            if (interfaces == null)
                return;
            java.util.Collection<PipeConnection> connections = interfaces.values();

            PipeConnection singleSource = null;

            if (phase == UpdatePhase.WAIT_FOR_PUMPS) {
                phase = UpdatePhase.FLIP_FLOWS;
                return;
            }

            if (onServer) {
                boolean sendUpdate = false;
                for (PipeConnection connection : connections) {
                    sendUpdate |= connection.flipFlowsIfPressureReversed();
                    connection.manageSource(world, pos);
                }
                if (sendUpdate)
                    blockEntity.notifyUpdate();
            }

            if (phase == UpdatePhase.FLIP_FLOWS) {
                phase = UpdatePhase.IDLE;
                return;
            }

            if (onServer) {
                FluidStack availableFlow = FluidStack.EMPTY;
                FluidStack collidingFlow = FluidStack.EMPTY;

                for (PipeConnection connection : connections) {
                    FluidStack fluidInFlow = connection.getProvidedFluid();
                    if (fluidInFlow.isEmpty())
                        continue;
                    if (availableFlow.isEmpty()) {
                        singleSource = connection;
                        availableFlow = fluidInFlow;
                        continue;
                    }
                    if (availableFlow.isFluidEqual(fluidInFlow)) {
                        singleSource = null;
                        availableFlow = fluidInFlow;
                        continue;
                    }
                    collidingFlow = fluidInFlow;
                    break;
                }

                if (!collidingFlow.isEmpty()) {
                    FluidReactions.handlePipeFlowCollision(world, pos, availableFlow, collidingFlow);
                    return;
                }

                BlockState currentState = blockEntity.getBlockState();
                boolean sendUpdate = false;
                for (PipeConnection connection : connections) {
                    // ===== 关键修改：对关闭的侧口不传递 internalFluid =====
                    FluidStack internalFluid;
                    if (singleSource != connection && RedstoneTripleValveBlock.isOpenAt(currentState, connection.side)) {
                        internalFluid = availableFlow;
                    } else {
                        internalFluid = FluidStack.EMPTY;
                    }

                    java.util.function.Predicate<FluidStack> extractionPredicate =
                            extracted -> canPullFluidFrom(extracted, currentState, connection.side);
                    sendUpdate |= connection.manageFlows(world, pos, internalFluid, extractionPredicate);
                }

                if (sendUpdate)
                    blockEntity.notifyUpdate();
            }

            for (PipeConnection connection : connections)
                connection.tickFlowProgress(world, pos);
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