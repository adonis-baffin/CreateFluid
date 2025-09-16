package com.adonis.fluid.block.CentrifugalPump;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.model.Models;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

import java.util.function.Consumer;

public class CentrifugalPumpVisual extends KineticBlockEntityVisual<CentrifugalPumpBlockEntity> {
    
    protected final RotatingInstance shaft;
    protected final Direction shaftDirection;

    public CentrifugalPumpVisual(VisualizationContext context, CentrifugalPumpBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
        
        BlockState state = blockEntity.getBlockState();
        this.shaftDirection = CentrifugalPumpBlock.getShaftDirection(state);
        Direction opposite = shaftDirection.getOpposite();
        AttachFace face = state.getValue(CentrifugalPumpBlock.FACE);
        
        // 创建半轴实例
        this.shaft = (RotatingInstance) this.instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF))
                .createInstance();
        
        // 设置轴的位置和朝向
        setupShaft(blockEntity, state, face, opposite);
    }
    
    private void setupShaft(CentrifugalPumpBlockEntity be, BlockState state, AttachFace face, Direction shaftOpposite) {
        shaft.setup(be)
             .setPosition(this.getVisualPosition());
        
        if (face == AttachFace.WALL) {
            // 垂直模式：轴垂直向上
            shaft.rotateToFace(Direction.SOUTH, shaftOpposite);
        } else {
            // 水平模式：轴水平
            Direction facing = state.getValue(CentrifugalPumpBlock.FACING);
            
            // 根据facing方向调整轴的朝向
            if (face == AttachFace.FLOOR) {
                shaft.rotateToFace(Direction.SOUTH, shaftOpposite);
            } else { // CEILING
                // 天花板模式需要特殊处理
                shaft.rotateToFace(Direction.NORTH, shaftOpposite);
            }
        }
        
        shaft.setChanged();
    }

    @Override
    public void update(float pt) {
        shaft.setup((KineticBlockEntity) blockEntity).setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        BlockPos behind = pos.relative(shaftDirection.getOpposite());
        relight(behind, new FlatLit[]{shaft});
    }

    @Override
    protected void _delete() {
        shaft.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(shaft);
    }
}