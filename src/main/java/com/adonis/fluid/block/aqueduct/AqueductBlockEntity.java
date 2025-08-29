package com.adonis.fluid.block.aqueduct;

import com.adonis.fluid.config.CFCommonConfig;
import com.adonis.fluid.content.aqueduct.AqueductNetwork;
import com.adonis.fluid.content.aqueduct.AqueductNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class AqueductBlockEntity extends AbstractAqueductBlockEntity {

    private boolean hasWaterSource = false;
    private int waterFillCooldown = 0;
    private boolean networkControlled = false;  // 是否被网络控制

    public AqueductBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();

        if (level.isClientSide) return;

        // 水源方块自动填充
        if (hasWaterSource && !locked) {
            fillFromWaterSource();
        }
    }

    @Override
    protected void performTransfer() {
        // 获取或创建网络
        AqueductNetwork network = AqueductNetworkManager.getInstance().getOrCreateNetwork(level, worldPosition);
        if (network != null) {
            // 如果被网络控制（有动力泵输入），不进行自发传输
            networkControlled = network.isNodeControlled(worldPosition);
            if (networkControlled) {
                return;  // 不进行自发传输
            }
        }

        // 没有网络控制时进行普通传输
        if (!networkControlled) {
            performNormalTransfer();
        }
    }

    private void performNormalTransfer() {
        // 普通的自发传输逻辑（用于桶填充等情况）
        Direction flowDir = getFlowDirection();
        BlockPos targetPos = worldPosition.relative(flowDir);

        if (level.getBlockEntity(targetPos) instanceof AbstractAqueductBlockEntity target) {
            if (tank.getFluidAmount() > 0 && target.getSpace() > 0) {
                FluidStack fluid = tank.getFluid();
                if (target.tank.isEmpty() || target.tank.getFluid().isFluidEqual(fluid)) {
                    int transfer = Math.min(
                            Math.min(getTransferRate(), fluid.getAmount()),
                            target.getSpace()
                    );

                    FluidStack transferStack = fluid.copy();
                    transferStack.setAmount(transfer);

                    tank.drain(transferStack, IFluidHandler.FluidAction.EXECUTE);
                    target.tank.fill(transferStack, IFluidHandler.FluidAction.EXECUTE);
                }
            }
        }
    }

    private void fillFromWaterSource() {
        if (waterFillCooldown > 0) {
            waterFillCooldown--;
            return;
        }

        Direction inputDir = getFlowDirection().getOpposite();
        BlockPos sourcePos = worldPosition.relative(inputDir);

        if (level.getBlockState(sourcePos).getBlock() != Fluids.WATER.defaultFluidState().createLegacyBlock().getBlock()) {
            hasWaterSource = false;
            return;
        }

        if (tank.getFluidAmount() >= tank.getCapacity()) {
            return;
        }

        if (!tank.getFluid().isEmpty() && tank.getFluid().getFluid() != Fluids.WATER) {
            return;
        }

        int fillAmount = Math.min(CFCommonConfig.WATER_SOURCE_FILL_RATE.get(), tank.getSpace());
        FluidStack water = new FluidStack(Fluids.WATER, fillAmount);
        tank.fill(water, IFluidHandler.FluidAction.EXECUTE);

        waterFillCooldown = 2;
    }

    public void setHasWaterSource(boolean hasWaterSource) {
        this.hasWaterSource = hasWaterSource;
        setChanged();
    }

    public boolean isNetworkControlled() {
        return networkControlled;
    }

    public void setNetworkControlled(boolean controlled) {
        this.networkControlled = controlled;
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.putBoolean("HasWaterSource", hasWaterSource);
        tag.putInt("WaterFillCooldown", waterFillCooldown);
        tag.putBoolean("NetworkControlled", networkControlled);
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        hasWaterSource = tag.getBoolean("HasWaterSource");
        waterFillCooldown = tag.getInt("WaterFillCooldown");
        networkControlled = tag.getBoolean("NetworkControlled");
    }
}