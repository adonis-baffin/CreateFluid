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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
public class RedstoneTripleValveBlockEntity extends SmartBlockEntity {
    // 自定义一个空的 FluidHandler，用于堵住关闭的端口
    // 这样管道会认为这里连接了一个不可填充的容器，从而不会视为敞开端
    private static final IFluidHandler BLOCKED_HANDLER = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 0;
        }
        @Override
        @Nonnull
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }
        @Override
        public int getTankCapacity(int tank) {
            return 0;
        }
        @Override
        public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
            return false;
        }
        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0; // 返回0，表示拒绝任何流体注入
        }
        @Override
        @Nonnull
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }
        @Override
        @Nonnull
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };
    // 封装为 LazyOptional
    private static final LazyOptional<IFluidHandler> BLOCKED_CAPABILITY = LazyOptional.of(() -> BLOCKED_HANDLER);
    public RedstoneTripleValveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new TripleValvePipeBehaviour(this));
        registerAwardables(behaviours, FluidPropagator.getSharedTriggers());
    }
    /**
     * 核心修复：Capabilities 控制。
     *
     * 当端口关闭时，我们返回一个空的 IFluidHandler。
     * 这样做的效果是：
     * 1. FluidPropagator.isOpenEnd 会检测到 Capability，认为这不是敞开的口，从而阻止流体喷出。
     * 2. 相邻管道会尝试向这个 Handler 注入流体，但因为它是空的，注入失败，表现为“堵住”了。
     */
    @Override
    @Nonnull
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        // 仅处理流体能力
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            // 关键修复：必须检查 side 是否为 null。
            // 管道类方块通常没有“内部”储罐，side 为 null 时不应该返回任何特殊能力。
            if (side != null) {
                BlockState state = getBlockState();
                // 如果请求的一侧是物理端口之一
                if (RedstoneTripleValveBlock.isAnyPort(state, side)) {
                    // 但当前逻辑是关闭的
                    if (!RedstoneTripleValveBlock.isOpenAt(state, side)) {
                        // 返回阻塞 Handler，假装这里是一个满的/不通的容器
                        return BLOCKED_CAPABILITY.cast();
                    }
                }
            }
        }
        return super.getCapability(cap, side);
    }
    class TripleValvePipeBehaviour extends FluidTransportBehaviour {
        public TripleValvePipeBehaviour(SmartBlockEntity be) {
            super(be);
        }
        /**
         * 核心修复：控制流体网络的拓扑结构。
         *
         * 仅对当前红石状态下“开启”的两个端口返回 true。
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