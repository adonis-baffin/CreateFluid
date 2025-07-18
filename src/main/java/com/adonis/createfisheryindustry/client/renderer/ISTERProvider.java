package com.adonis.createfisheryindustry.client.renderer;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

// 这个类用于防止服务器端类加载问题
public class ISTERProvider {
    
    public static IClientItemExtensions harpoon() {
        return new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return HarpoonISTER.RENDERER;
            }
        };
    }
}