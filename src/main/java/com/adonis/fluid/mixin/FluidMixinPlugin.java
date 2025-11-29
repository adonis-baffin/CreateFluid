package com.adonis.fluid.mixin;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin 插件，用于根据 TFMG 是否存在来决定加载哪些 Mixin
 */
public class FluidMixinPlugin implements IMixinConfigPlugin {

    private static final String TFMG_MODID = "tfmg";
    private static Boolean isTFMGLoaded = null;

    @Override
    public void onLoad(String mixinPackage) {
        // 在加载时检测 TFMG
        isTFMGLoaded = LoadingModList.get().getModFileById(TFMG_MODID) != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // FluidPropagatorMixin - 只在 TFMG 不存在时加载（使用 Overwrite 方式）
        if (mixinClassName.equals("com.adonis.fluid.mixin.FluidPropagatorMixin")) {
            return !isTFMGLoaded;
        }
        
        // FluidPropagatorMixin_TFMGCompat - 只在 TFMG 存在时加载（使用 Inject 方式补充）
        if (mixinClassName.equals("com.adonis.fluid.mixin.compat.FluidPropagatorMixin_TFMGCompat")) {
            return isTFMGLoaded;
        }
        
        // 其他 Mixin 正常加载
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}