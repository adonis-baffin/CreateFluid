package com.adonis.fluid.config;

import com.adonis.fluid.CreateFluid;
import com.simibubi.create.api.stress.BlockStressValues;
import net.createmod.catnip.config.ConfigBase;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * 应力值配置 - 与原版Create的CStress结构一致
 * 注意：此类通过nested()嵌套在CFKinetics中使用
 */
public class CFStress extends ConfigBase {
    // 版本号，修改此值会重置已配置的应力值
    private static final int VERSION = 1;

    // 使用static存储默认值，确保在配置注册前可以被填充
    private static final Map<ResourceLocation, Double> DEFAULT_IMPACTS = new HashMap<>();
    private static final Map<ResourceLocation, Double> DEFAULT_CAPACITIES = new HashMap<>();

    // 存储配置值
    protected final Map<ResourceLocation, ConfigValue<Double>> impacts = new HashMap<>();
    protected final Map<ResourceLocation, ConfigValue<Double>> capacities = new HashMap<>();

    @Override
    public void registerAll(Builder builder) {
        // impact 组
        builder.comment(".", Comments.su, Comments.impact)
                .push("impact");
        DEFAULT_IMPACTS.forEach((id, value) -> this.impacts.put(id, builder.define(id.getPath(), value)));
        builder.pop();

        // capacity 组
        builder.comment(".", Comments.su, Comments.capacity)
                .push("capacity");
        DEFAULT_CAPACITIES.forEach((id, value) -> this.capacities.put(id, builder.define(id.getPath(), value)));
        builder.pop();
    }

    @Override
    public String getName() {
        return "stressValues.v" + VERSION;
    }

    @Nullable
    public DoubleSupplier getImpact(Block block) {
        ResourceLocation id = CatnipServices.REGISTRIES.getKeyOrThrow(block);
        if (!id.getNamespace().equals(CreateFluid.MODID)) {
            return null;
        }
        ConfigValue<Double> value = this.impacts.get(id);
        return value == null ? null : value::get;
    }

    @Nullable
    public DoubleSupplier getCapacity(Block block) {
        ResourceLocation id = CatnipServices.REGISTRIES.getKeyOrThrow(block);
        if (!id.getNamespace().equals(CreateFluid.MODID)) {
            return null;
        }
        ConfigValue<Double> value = this.capacities.get(id);
        return value == null ? null : value::get;
    }

    /**
     * 设置方块的无应力影响（静态方法，原版风格）
     */
    public static <B extends Block, P> com.tterrag.registrate.util.nullness.NonNullUnaryOperator<com.tterrag.registrate.builders.BlockBuilder<B, P>> setNoImpact() {
        return setImpact(0);
    }

    /**
     * 设置方块的应力影响（静态方法，原版风格）
     */
    public static <B extends Block, P> com.tterrag.registrate.util.nullness.NonNullUnaryOperator<com.tterrag.registrate.builders.BlockBuilder<B, P>> setImpact(double value) {
        return builder -> {
            assertFromCreateFluid(builder);
            ResourceLocation id = CreateFluid.asResource(builder.getName());
            DEFAULT_IMPACTS.put(id, value);
            return builder;
        };
    }

    /**
     * 设置方块的应力容量（静态方法，原版风格）
     */
    public static <B extends Block, P> com.tterrag.registrate.util.nullness.NonNullUnaryOperator<com.tterrag.registrate.builders.BlockBuilder<B, P>> setCapacity(double value) {
        return builder -> {
            assertFromCreateFluid(builder);
            ResourceLocation id = CreateFluid.asResource(builder.getName());
            DEFAULT_CAPACITIES.put(id, value);
            return builder;
        };
    }

    private static void assertFromCreateFluid(com.tterrag.registrate.builders.BlockBuilder<?, ?> builder) {
        if (!builder.getOwner().getModid().equals(CreateFluid.MODID)) {
            throw new IllegalStateException("Non-CreateFluid blocks cannot be added to CreateFluid's stress config.");
        }
    }

    private static class Comments {
        static String su = "[in Stress Units]";
        static String impact =
                "Configure the individual stress impact of mechanical blocks. Note that this cost is doubled for every speed increase it receives.";
        static String capacity = "Configure how much stress a source can accommodate for.";
    }
}
