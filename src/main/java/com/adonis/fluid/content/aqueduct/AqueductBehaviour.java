package com.adonis.fluid.content.aqueduct;

import com.adonis.fluid.block.aqueduct.AbstractAqueductBlockEntity;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

public class AqueductBehaviour extends BlockEntityBehaviour {
    
    public static final BehaviourType<AqueductBehaviour> TYPE = new BehaviourType<>();
    
    private final AbstractAqueductBlockEntity blockEntity;
    private boolean hasFluidPipeConnection = false;
    private Direction pipeConnectionSide = null;
    
    public AqueductBehaviour(AbstractAqueductBlockEntity be) {
        super(be);
        this.blockEntity = be;
    }

    @Override
    public void initialize() {
        super.initialize();
        checkForPipeConnections();
    }

    @Override
    public void tick() {
        super.tick();
        
        if (getWorld().getGameTime() % 20 == 0) {
            // 每秒检查一次管道连接
            checkForPipeConnections();
        }
    }

    private void checkForPipeConnections() {
        Level level = getWorld();
        BlockPos pos = getPos();
        
        hasFluidPipeConnection = false;
        pipeConnectionSide = null;
        
        // 检查所有方向的流体管道连接
        for (Direction dir : Direction.values()) {
            if (FluidPropagator.hasFluidCapability(level, pos.relative(dir), dir.getOpposite())) {
                // 检查是否有动力泵
                if (AqueductHelper.hasPoweredPump(level, pos.relative(dir))) {
                    hasFluidPipeConnection = true;
                    pipeConnectionSide = dir;
                    break;
                }
            }
        }
    }

    public boolean hasFluidPipeConnection() {
        return hasFluidPipeConnection;
    }

    public Direction getPipeConnectionSide() {
        return pipeConnectionSide;
    }

    public boolean canReceiveFrom(Direction side) {
        // 检查是否可以从指定方向接收流体
        Direction flowDir = blockEntity.getFlowDirection();
        return side == flowDir.getOpposite();
    }

    public boolean canOutputTo(Direction side) {
        // 检查是否可以向指定方向输出流体
        Direction flowDir = blockEntity.getFlowDirection();
        return side == flowDir;
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    @Override
    public void write(CompoundTag nbt, boolean clientPacket) {
        super.write(nbt, clientPacket);
        nbt.putBoolean("HasPipeConnection", hasFluidPipeConnection);
        if (pipeConnectionSide != null) {
            nbt.putString("PipeConnectionSide", pipeConnectionSide.getName());
        }
    }

    @Override
    public void read(CompoundTag nbt, boolean clientPacket) {
        super.read(nbt, clientPacket);
        hasFluidPipeConnection = nbt.getBoolean("HasPipeConnection");
        if (nbt.contains("PipeConnectionSide")) {
            pipeConnectionSide = Direction.byName(nbt.getString("PipeConnectionSide"));
        }
    }
}