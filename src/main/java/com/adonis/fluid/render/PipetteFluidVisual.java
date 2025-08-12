package com.adonis.fluid.render;

import com.mojang.math.Axis;
import com.simibubi.create.content.fluids.FluidMesh;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.visual.util.SmartRecycler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

public class PipetteFluidVisual {
    private final SmartRecycler<TextureAtlasSprite, TransformedInstance> surface;
    private final VisualizationContext context;
    
    public PipetteFluidVisual(VisualizationContext context) {
        this.context = context;
        this.surface = new SmartRecycler<>(key ->
                context.instancerProvider()
                        .instancer(InstanceTypes.TRANSFORMED, FluidMesh.surface(key, 1))
                        .createInstance());
    }
    
    /**
     * 为移液器头部创建流体渲染实例
     */
    public TransformedInstance createFluidInstance(FluidStack fluidStack) {
        if (fluidStack.isEmpty()) return null;
        
        IClientFluidTypeExtensions clientFluid = IClientFluidTypeExtensions.of(fluidStack.getFluid());
        var atlas = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite stillTexture = atlas.apply(clientFluid.getStillTexture(fluidStack));
        
        TransformedInstance instance = surface.get(stillTexture);
        instance.colorArgb(clientFluid.getTintColor(fluidStack));
        
        return instance;
    }
    
    /**
     * 设置流体在移液器针头中的渲染效果
     */
    public void setupPipetteFluid(TransformedInstance fluidInstance, FluidStack fluidStack,
                                 int capacity, boolean isInjectMode) {
        if (fluidInstance == null || fluidStack.isEmpty()) return;

        float fillFactor = (float) fluidStack.getAmount() / capacity;

        // 移液器针头内部的流体渲染
        fluidInstance.setIdentityTransform();

        // 针头内部尺寸 (很小的圆柱形空间)
        float needleRadius = 0.02f;
        float needleLength = 0.3f;

        // 根据填充比例计算流体长度
        float fluidLength = fillFactor * needleLength;

        // 定位到针头内部
        fluidInstance.translate(0, 0, -0.15f); // 移到针头中心位置

        if (isInjectMode) {
            // 注射模式：流体从针头尖端开始
            fluidInstance.translate(0, 0, -(needleLength - fluidLength) / 2);
        } else {
            // 抽取模式：流体从针头后端开始
            fluidInstance.translate(0, 0, (needleLength - fluidLength) / 2);
        }

        // 缩放到针头大小
        fluidInstance.scaleX(needleRadius);
        fluidInstance.scaleY(needleRadius);
        fluidInstance.scaleZ(fluidLength);

        // 如果是气体，从顶部渲染
        if (fluidStack.getFluid().getFluidType().isLighterThanAir()) {
            fluidInstance.rotateDegrees(180, Axis.ZP);
        }

        fluidInstance.setChanged();
    }

    /**
     * 在移液器储存仓中渲染流体（如果移液器有储存功能）
     */
    public void setupStorageFluid(TransformedInstance fluidInstance, FluidStack fluidStack,
                                 int capacity) {
        if (fluidInstance == null || fluidStack.isEmpty()) return;

        float fillFactor = (float) fluidStack.getAmount() / capacity;

        // 储存仓尺寸
        float storageWidth = 0.125f;
        float storageHeight = 0.25f;

        fluidInstance.setIdentityTransform();

        // 定位到储存仓
        fluidInstance.translate(0, 0, -0.5f);

        // 根据填充比例调整高度
        fluidInstance.translateY(-storageHeight / 2 + (fillFactor * storageHeight) / 2);

        // 设置尺寸
        fluidInstance.scaleX(storageWidth);
        fluidInstance.scaleY(fillFactor * storageHeight);
        fluidInstance.scaleZ(storageWidth);

        fluidInstance.setChanged();
    }
    
    public void begin() {
        surface.resetCount();
    }
    
    public void end() {
        surface.discardExtra();
    }
    
    public void delete() {
        surface.delete();
    }
}