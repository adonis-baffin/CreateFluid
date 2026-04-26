package com.adonis.fluid.block.CommunicatingVessel;

import com.adonis.fluid.registry.CFBlockEntities;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public class CommunicatingVesselBlock extends Block implements IBE<CommunicatingVesselBlockEntity>, IWrenchable {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    /*
     * 默认模型朝 Z 轴。
     * 这版碰撞/选择箱包含：
     * 1) 工字主体
     * 2) 两端管口接口
     *
     * 工字主体来源：
     * - 腹板: [6,5,0] -> [10,11,16]
     * - 主翼缘: [1,1,7] -> [15,15,9]
     * - 次级翼缘: [2,2,6] -> [14,14,7]、[2,2,9] -> [14,14,10]
     * - 加强条: [5,4,2] -> [6,12,14]、[10,4,2] -> [11,12,14]
     *
     * 两端管口：
     * - [4,4,0] -> [12,12,2]
     * - [4,4,14] -> [12,12,16]
     *
     * 以上都来自你给的模型文件。:contentReference[oaicite:2]{index=2}
     */

    private static final VoxelShape SHAPE_Z = Shapes.or(
            // 工字主体 - 腹板
            Shapes.box(6 / 16.0, 5 / 16.0, 0 / 16.0, 10 / 16.0, 11 / 16.0, 16 / 16.0),

            // 工字主体 - 主翼缘
            Shapes.box(1 / 16.0, 1 / 16.0, 7 / 16.0, 15 / 16.0, 15 / 16.0, 9 / 16.0),

            // 工字主体 - 次级翼缘
            Shapes.box(2 / 16.0, 2 / 16.0, 6 / 16.0, 14 / 16.0, 14 / 16.0, 7 / 16.0),
            Shapes.box(2 / 16.0, 2 / 16.0, 9 / 16.0, 14 / 16.0, 14 / 16.0, 10 / 16.0),

            // 工字主体 - 加强条
            Shapes.box(5 / 16.0, 4 / 16.0, 2 / 16.0, 6 / 16.0, 12 / 16.0, 14 / 16.0),
            Shapes.box(10 / 16.0, 4 / 16.0, 2 / 16.0, 11 / 16.0, 12 / 16.0, 14 / 16.0),

            // 两端管口
            Shapes.box(4 / 16.0, 4 / 16.0, 0 / 16.0, 12 / 16.0, 12 / 16.0, 2 / 16.0),
            Shapes.box(4 / 16.0, 4 / 16.0, 14 / 16.0, 12 / 16.0, 12 / 16.0, 16 / 16.0)
    );

    private static final VoxelShape SHAPE_X = Shapes.or(
            // 工字主体 - 腹板
            Shapes.box(0 / 16.0, 5 / 16.0, 6 / 16.0, 16 / 16.0, 11 / 16.0, 10 / 16.0),

            // 工字主体 - 主翼缘
            Shapes.box(7 / 16.0, 1 / 16.0, 1 / 16.0, 9 / 16.0, 15 / 16.0, 15 / 16.0),

            // 工字主体 - 次级翼缘
            Shapes.box(6 / 16.0, 2 / 16.0, 2 / 16.0, 7 / 16.0, 14 / 16.0, 14 / 16.0),
            Shapes.box(9 / 16.0, 2 / 16.0, 2 / 16.0, 10 / 16.0, 14 / 16.0, 14 / 16.0),

            // 工字主体 - 加强条
            Shapes.box(2 / 16.0, 4 / 16.0, 5 / 16.0, 14 / 16.0, 12 / 16.0, 6 / 16.0),
            Shapes.box(2 / 16.0, 4 / 16.0, 10 / 16.0, 14 / 16.0, 12 / 16.0, 11 / 16.0),

            // 两端管口
            Shapes.box(0 / 16.0, 4 / 16.0, 4 / 16.0, 2 / 16.0, 12 / 16.0, 12 / 16.0),
            Shapes.box(14 / 16.0, 4 / 16.0, 4 / 16.0, 16 / 16.0, 12 / 16.0, 12 / 16.0)
    );

    private static final VoxelShape SHAPE_Y = Shapes.or(
            // 工字主体 - 腹板
            Shapes.box(6 / 16.0, 0 / 16.0, 5 / 16.0, 10 / 16.0, 16 / 16.0, 11 / 16.0),

            // 工字主体 - 主翼缘
            Shapes.box(1 / 16.0, 7 / 16.0, 1 / 16.0, 15 / 16.0, 9 / 16.0, 15 / 16.0),

            // 工字主体 - 次级翼缘
            Shapes.box(2 / 16.0, 6 / 16.0, 2 / 16.0, 14 / 16.0, 7 / 16.0, 14 / 16.0),
            Shapes.box(2 / 16.0, 9 / 16.0, 2 / 16.0, 14 / 16.0, 10 / 16.0, 14 / 16.0),

            // 工字主体 - 加强条
            Shapes.box(5 / 16.0, 2 / 16.0, 4 / 16.0, 6 / 16.0, 14 / 16.0, 12 / 16.0),
            Shapes.box(10 / 16.0, 2 / 16.0, 4 / 16.0, 11 / 16.0, 14 / 16.0, 12 / 16.0),

            // 两端管口
            Shapes.box(4 / 16.0, 0 / 16.0, 4 / 16.0, 12 / 16.0, 2 / 16.0, 12 / 16.0),
            Shapes.box(4 / 16.0, 14 / 16.0, 4 / 16.0, 12 / 16.0, 16 / 16.0, 12 / 16.0)
    );

    public CommunicatingVesselBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.Y));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction.Axis preferredAxis = getPreferredAxis(context);

        if (preferredAxis != null && (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown())) {
            return this.defaultBlockState().setValue(AXIS, preferredAxis);
        }

        return this.defaultBlockState().setValue(AXIS, context.getNearestLookingDirection().getAxis());
    }

    @Nullable
    private static Direction.Axis getPreferredAxis(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        AxisCandidate best = null;

        for (Direction.Axis axis : Direction.Axis.values()) {
            Direction negative = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
            Direction positive = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);

            NeighborKind negKind = classifyNeighbor(level, pos.relative(negative), axis, negative.getOpposite());
            NeighborKind posKind = classifyNeighbor(level, pos.relative(positive), axis, positive.getOpposite());

            int score = scoreAxis(negKind, posKind);

            if (best == null || score > best.score) {
                best = new AxisCandidate(axis, score);
            }
        }

        return best != null && best.score > 0 ? best.axis : null;
    }

    private static NeighborKind classifyNeighbor(Level level, BlockPos neighborPos, Direction.Axis axis, Direction faceTowardPlacedBlock) {
        BlockState neighborState = level.getBlockState(neighborPos);

        if (neighborState.getBlock() instanceof CommunicatingVesselBlock) {
            Direction.Axis neighborAxis = neighborState.getValue(AXIS);
            return neighborAxis == axis ? NeighborKind.SAME_AXIS_VESSEL : NeighborKind.OTHER_AXIS_VESSEL;
        }

        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, neighborPos, faceTowardPlacedBlock);
        if (handler == null) {
            handler = level.getCapability(Capabilities.FluidHandler.BLOCK, neighborPos, null);
        }

        if (handler != null && handler.getTanks() > 0) {
            return NeighborKind.FLUID_HANDLER;
        }

        return NeighborKind.NONE;
    }

    private static int scoreAxis(NeighborKind a, NeighborKind b) {
        if (a == NeighborKind.FLUID_HANDLER && b == NeighborKind.FLUID_HANDLER) return 100;

        if ((a == NeighborKind.FLUID_HANDLER && b == NeighborKind.SAME_AXIS_VESSEL)
                || (b == NeighborKind.FLUID_HANDLER && a == NeighborKind.SAME_AXIS_VESSEL)) {
            return 80;
        }

        if (a == NeighborKind.SAME_AXIS_VESSEL && b == NeighborKind.SAME_AXIS_VESSEL) return 60;

        if (a == NeighborKind.FLUID_HANDLER || b == NeighborKind.FLUID_HANDLER) return 40;

        if (a == NeighborKind.SAME_AXIS_VESSEL || b == NeighborKind.SAME_AXIS_VESSEL) return 20;

        return 0;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> SHAPE_X;
            case Y -> SHAPE_Y;
            case Z -> SHAPE_Z;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (oldState.getBlock() == state.getBlock()) return;
        level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            Direction.Axis axis = state.getValue(AXIS);
            Direction positive = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
            Direction negative = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);

            BlockPos neighbor = pos.relative(positive);
            BlockState neighborState = level.getBlockState(neighbor);
            if (neighborState.getBlock() instanceof CommunicatingVesselBlock) {
                level.scheduleTick(neighbor, neighborState.getBlock(), 1);
            }

            neighbor = pos.relative(negative);
            neighborState = level.getBlockState(neighbor);
            if (neighborState.getBlock() instanceof CommunicatingVesselBlock) {
                level.scheduleTick(neighbor, neighborState.getBlock(), 1);
            }
        }
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        level.scheduleTick(pos, this, 1);

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof CommunicatingVesselBlockEntity vessel) {
            vessel.markTopologyDirty();
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof CommunicatingVesselBlockEntity vessel) {
            vessel.onScheduledTick();
        }
    }

    @Override
    public Class<CommunicatingVesselBlockEntity> getBlockEntityClass() {
        return CommunicatingVesselBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CommunicatingVesselBlockEntity> getBlockEntityType() {
        return CFBlockEntities.COMMUNICATING_VESSEL.get();
    }

    private enum NeighborKind {
        NONE,
        FLUID_HANDLER,
        SAME_AXIS_VESSEL,
        OTHER_AXIS_VESSEL
    }

    private record AxisCandidate(Direction.Axis axis, int score) {
    }
}
