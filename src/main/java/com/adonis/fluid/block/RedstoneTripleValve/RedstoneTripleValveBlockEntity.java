package com.adonis.fluid.block.RedstoneTripleValve;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RedstoneTripleValveBlockEntity extends SmartBlockEntity {

    // 自定义一个空的 FluidHandler，用于堵住关闭的端口
    // 这样管道会认为这里连接了一个不可填充的容器，从而不会视为敞开端
    private static final IFluidHandler BLOCKED_HANDLER = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 0;
        }

        @Override
        @NotNull
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return 0;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0; // 返回0，表示拒绝任何流体注入
        }

        @Override
        @NotNull
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        @NotNull
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };

    public RedstoneTripleValveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new TripleValvePipeBehaviour(this));
        registerAwardables(behaviours, FluidPropagator.getSharedTriggers());
    }

    /**
     * 静态方法注册能力
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event, BlockEntityType<? extends RedstoneTripleValveBlockEntity> type) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                type,
                (be, side) -> {
                    if (side != null) {
                        BlockState state = be.getBlockState();
                        // 如果请求的一侧是物理端口之一
                        if (RedstoneTripleValveBlock.isAnyPort(state, side)) {
                            // 但当前逻辑是关闭的
                            if (!RedstoneTripleValveBlock.isOpenAt(state, side)) {
                                // 返回阻塞 Handler，假装这里是一个满的/不通的容器
                                return BLOCKED_HANDLER;
                            }
                        }
                    }
                    return null;
                }
        );
    }

    class TripleValvePipeBehaviour extends FluidTransportBehaviour {
        public TripleValvePipeBehaviour(SmartBlockEntity be) {
            super(be);
        }

        /**
         * 核心修复：控制流体网络的拓扑结构。
         *
         * 仅对当前红石状态下"开启"的两个端口返回 true。
         * 这会导致 FluidTransportBehaviour 在 wipePressure() 时移除关闭端口的 PipeConnection。
         * 结果是，流体网络逻辑（压力传播、寻路）将完全忽略关闭的端口，
         * 从而彻底解决压力抵消的问题。
         */
        @Override
        public boolean canHaveFlowToward(BlockState state, Direction direction) {
            return RedstoneTripleValveBlock.isOpenAt(state, direction);
        }

        /**
         * 视觉渲染逻辑：
         * 保留了你原有的渲染风格（移除默认的RIM连接器，避免与方块模型冲突），
         * 同时适配了新的拓扑逻辑。
         */
        @Override
        public AttachmentTypes getRenderedRimAttachment(BlockAndTintGetter world, BlockPos pos,
                                                        BlockState state, Direction direction) {
            // 调用父类方法获取默认判定
            AttachmentTypes attachment = super.getRenderedRimAttachment(world, pos, state, direction);
            // 如果是 RIM（普通管道连接），返回 NONE，因为我们使用自定义方块模型。
            if (attachment == AttachmentTypes.RIM)
                return AttachmentTypes.NONE;
            // 其他情况（如连接容器时的 DRAIN）保留，但移除连接器部件
            return attachment.withoutConnector();
        }
    }
}
