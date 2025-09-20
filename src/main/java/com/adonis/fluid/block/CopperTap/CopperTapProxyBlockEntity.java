package com.adonis.fluid.block.CopperTap;

import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class CopperTapProxyBlockEntity extends SmartBlockEntity {

    private BeltProcessingBehaviour beltProcessing;

    public CopperTapProxyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // 注册传送带处理行为
        beltProcessing = new BeltProcessingBehaviour(this)
                .whenItemEnters(this::onItemReceived)
                .whileItemHeld(this::whenItemHeld);
        behaviours.add(beltProcessing);

        // 注册物品处理行为
        behaviours.add(new TransportedItemStackHandlerBehaviour(this, this::applyToAllItems)
                .withStackPlacement(this::getWorldPositionOf));
    }

    private BeltProcessingBehaviour.ProcessingResult onItemReceived(
            TransportedItemStack transported,
            TransportedItemStackHandlerBehaviour handler) {

        // 添加调试日志
        if (!level.isClientSide) {
            System.out.println("CopperTapProxy: Item received at " + worldPosition);
        }

        // 检查下方是否有铜龙头
        BlockPos tapPos = worldPosition.below();

        // 如果代理方块在传送带上方2格，那铜龙头应该在上方1格
        if (!(level.getBlockEntity(tapPos) instanceof CopperTapBlockEntity)) {
            // 尝试检查上方（可能代理方块直接在铜龙头上方）
            tapPos = worldPosition.above();
            if (!(level.getBlockEntity(tapPos) instanceof CopperTapBlockEntity)) {
                System.out.println("CopperTapProxy: No tap found at " + tapPos);
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }
        }

        CopperTapBlockEntity tapBE = (CopperTapBlockEntity) level.getBlockEntity(tapPos);

        // 检查铜龙头是否开启
        BlockState tapState = level.getBlockState(tapPos);
        if (!tapState.hasProperty(CopperTapBlock.OPEN) || !tapState.getValue(CopperTapBlock.OPEN)) {
            System.out.println("CopperTapProxy: Tap is closed");
            return BeltProcessingBehaviour.ProcessingResult.PASS;
        }

        // 委托给铜龙头处理
        boolean canProcess = tapBE.canProcessBeltItem(transported.stack);
        System.out.println("CopperTapProxy: Can process = " + canProcess);

        return canProcess ?
                BeltProcessingBehaviour.ProcessingResult.HOLD :
                BeltProcessingBehaviour.ProcessingResult.PASS;
    }

    private BeltProcessingBehaviour.ProcessingResult whenItemHeld(
            TransportedItemStack transported,
            TransportedItemStackHandlerBehaviour handler) {

        BlockPos tapPos = worldPosition.below();

        // 同样的查找逻辑
        if (!(level.getBlockEntity(tapPos) instanceof CopperTapBlockEntity)) {
            tapPos = worldPosition.above();
            if (!(level.getBlockEntity(tapPos) instanceof CopperTapBlockEntity)) {
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }
        }

        CopperTapBlockEntity tapBE = (CopperTapBlockEntity) level.getBlockEntity(tapPos);

        // 委托给铜龙头处理
        return tapBE.processBeltItem(transported, handler);
    }

    private void applyToAllItems(float maxDistanceFromCenter,
                                 java.util.function.Function<TransportedItemStack,
                                         TransportedItemStackHandlerBehaviour.TransportedResult> processFunction) {
        // 这个方法由传送带调用来处理物品
    }

    private Vec3 getWorldPositionOf(TransportedItemStack transported) {
        // 返回物品在世界中的位置（在传送带位置）
        return Vec3.atCenterOf(worldPosition.below(2));
    }
}