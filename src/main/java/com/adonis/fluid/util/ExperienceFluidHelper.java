package com.adonis.fluid.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 经验流体判断工具类，仅支持 Create Enchantment Industry 的经验流体。
 */
public class ExperienceFluidHelper {

    private static final ResourceLocation CEI_EXPERIENCE_ID =
            ResourceLocation.fromNamespaceAndPath("create_enchantment_industry", "experience");

    /**
     * 判断指定流体是否为 CEI 经验流体。
     */
    public static boolean isExperienceFluid(FluidStack fluid) {
        if (fluid.isEmpty()) return false;
        ResourceLocation id = resolveFluidId(fluid);
        return CEI_EXPERIENCE_ID.equals(id);
    }

    /**
     * 根据经验流体换算出对应的经验点数。
     * CEI 经验流体比例为 1 mB = 1 XP。
     *
     * @return 经验点数，若不为 CEI 经验流体则返回 0
     */
    public static int getExperienceFromFluid(FluidStack fluid) {
        if (fluid.isEmpty()) return 0;
        ResourceLocation id = resolveFluidId(fluid);
        if (!CEI_EXPERIENCE_ID.equals(id)) return 0;
        return fluid.getAmount();
    }

    /**
     * 解析 FluidStack 中流体对应的 ResourceLocation。
     * 优先使用 Holder 正向获取 key，fallback 到注册表反向查找。
     */
    private static ResourceLocation resolveFluidId(FluidStack fluid) {
        ResourceLocation id = fluid.getFluidHolder()
                .unwrapKey()
                .map(k -> k.location())
                .orElse(null);
        if (id != null) return id;
        return BuiltInRegistries.FLUID.getKey(fluid.getFluid());
    }
}
