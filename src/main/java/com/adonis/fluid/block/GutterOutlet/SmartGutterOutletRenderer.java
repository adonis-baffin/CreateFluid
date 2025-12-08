package com.adonis.fluid.block.GutterOutlet;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * 智能集水器渲染器
 * 继承集水器渲染器，可根据需要添加额外渲染（如过滤器显示）
 */
public class SmartGutterOutletRenderer extends GutterOutletRenderer {

    public SmartGutterOutletRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    // 如果需要额外渲染过滤器物品等，可以在这里重写 renderSafe 方法
}