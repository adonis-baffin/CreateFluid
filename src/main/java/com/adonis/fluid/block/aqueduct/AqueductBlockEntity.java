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
            // 网络会自动处理传输，不需要主动请求
            // 网络的tick方法会被NetworkManager调用
        }
    }

    private void fillFromWaterSource() {
        if (waterFillCooldown > 0) {
            waterFillCooldown--;
            return;
        }

        // 检查是否还有水源
        Direction inputDir = getFlowDirection().getOpposite();
        BlockPos sourcePos = worldPosition.relative(inputDir);

        if (level.getBlockState(sourcePos).getBlock() != Fluids.WATER.defaultFluidState().createLegacyBlock().getBlock()) {
            hasWaterSource = false;
            return;
        }

        // 检查是否可以填充
        if (tank.getFluidAmount() >= tank.getCapacity()) {
            return;
        }

        // 检查流体兼容性
        if (!tank.getFluid().isEmpty() && tank.getFluid().getFluid() != Fluids.WATER) {
            return;
        }

        // 填充水
        int fillAmount = Math.min(CFCommonConfig.WATER_SOURCE_FILL_RATE.get(), tank.getSpace());
        FluidStack water = new FluidStack(Fluids.WATER, fillAmount);
        tank.fill(water, IFluidHandler.FluidAction.EXECUTE);

        waterFillCooldown = 2; // 小延迟避免过于频繁
    }

    public void setHasWaterSource(boolean hasWaterSource) {
        this.hasWaterSource = hasWaterSource;
        setChanged();
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.putBoolean("HasWaterSource", hasWaterSource);
        tag.putInt("WaterFillCooldown", waterFillCooldown);
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        hasWaterSource = tag.getBoolean("HasWaterSource");
        waterFillCooldown = tag.getInt("WaterFillCooldown");
    }
}